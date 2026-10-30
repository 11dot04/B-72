package com.b72.keep_canvas.data

import com.b72.keep_canvas.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/** GET with redirects followed by hand (HttpURLConnection will not follow http -> https). */
private fun get(url: String, ua: String = UA): ByteArray? = runCatching {
    var cur = URL(url)
    repeat(6) {
        val c = (cur.openConnection() as HttpURLConnection).apply {
            connectTimeout = 14000; readTimeout = 14000; instanceFollowRedirects = false
            setRequestProperty("User-Agent", ua); setRequestProperty("Accept-Language", "en")
        }
        val code = c.responseCode
        if (code in setOf(301, 302, 303, 307, 308)) {
            val loc = c.getHeaderField("Location"); c.disconnect()
            if (loc == null) return@runCatching null
            cur = URL(cur, loc)
        } else {
            if (code != 200) { c.disconnect(); return@runCatching null }
            return@runCatching c.inputStream.use { it.readBytes() }
        }
    }
    null
}.getOrNull()

suspend fun fetchText(url: String, ua: String = UA): String? = withContext(Dispatchers.IO) { get(url, ua)?.toString(Charsets.UTF_8) }

/** Sites hand their preview tags to link-preview crawlers even when they will not to a phone browser. */
private const val CRAWLER = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

/** Downloads an image into [dir] and returns its path. */
suspend fun saveImage(url: String?, dir: File): String? {
    if (url.isNullOrEmpty()) return null
    return withContext(Dispatchers.IO) {
        val b = get(url) ?: return@withContext null
        if (b.size < 800) return@withContext null
        dir.mkdirs()
        File(dir, "w_${System.currentTimeMillis()}.jpg").apply { writeBytes(b) }.path
    }
}

private fun clean(s: String) = s.replace(Regex("<[^>]+>"), " ")
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
    .replace(Regex("\\s+"), " ").trim()

/** A search result the user can pick from. */
class Cand(val title: String, val sub: String, val image: String?, val data: Map<String, String>)

private fun q(s: String) = URLEncoder.encode(s, "UTF-8")
private fun year(d: String?) = d?.substringBefore('-') ?: ""
private fun art(u: String?, n: Int) = u?.replace(Regex("\\d+x\\d+bb"), "${n}x${n}bb")

private suspend fun json(url: String): Any? = fetchText(url)?.let { runCatching { org.json.JSONTokener(it).nextValue() }.getOrNull() }
private fun JSONObject.s(k: String): String = if (isNull(k)) "" else opt(k).toString()
private fun JSONArray.objs(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private suspend fun itunes(query: String, media: String, entity: String): List<JSONObject> =
    (json("https://itunes.apple.com/search?term=${q(query)}&media=$media&entity=$entity&limit=8") as? JSONObject)?.optJSONArray("results")?.objs() ?: emptyList()

/** TMDB (key from the TMDB_KEY secret at build time): best posters, plus director and runtime. */
private suspend fun tmdbFilms(query: String): List<Cand> {
    val key = BuildConfig.TMDB_KEY
    if (key.isEmpty()) return emptyList()
    val j = json("https://api.themoviedb.org/3/search/movie?api_key=$key&include_adult=false&query=${q(query)}") as? JSONObject ?: return emptyList()
    val top = j.optJSONArray("results")?.objs()?.take(8) ?: return emptyList()
    val details = coroutineScope { top.map { r -> async { json("https://api.themoviedb.org/3/movie/${r.optLong("id")}?api_key=$key&append_to_response=credits") as? JSONObject } }.awaitAll() }
    val out = ArrayList<Cand>()
    for ((i, r) in top.withIndex()) {
        val d = details[i]
        val title = r.s("title"); if (title.isEmpty()) continue
        var director = ""; var genre = ""; var runtime = ""
        if (d != null) {
            director = d.optJSONObject("credits")?.optJSONArray("crew")?.objs()?.firstOrNull { it.s("job") == "Director" }?.s("name") ?: ""
            genre = d.optJSONArray("genres")?.objs()?.firstOrNull()?.s("name") ?: ""
            runtime = d.optInt("runtime").takeIf { it > 0 }?.toString() ?: ""
        }
        val y = year(r.s("release_date")); val poster = r.s("poster_path")
        out += Cand(title, "$y · $director", poster.takeIf { it.isNotEmpty() }?.let { "https://image.tmdb.org/t/p/w500$it" },
            mapOf("year" to y, "director" to director, "genre" to genre, "runtime" to runtime, "overview" to r.s("overview")))
    }
    return out
}

/** Films: TMDB if a key is set, else iTunes, else Wikipedia (no key; posters need pilicense=any). */
suspend fun searchFilms(query: String): List<Cand> {
    tmdbFilms(query).let { if (it.isNotEmpty()) return it }
    val out = itunes(query, "movie", "movie").filter { it.s("trackName").isNotEmpty() }.map { r ->
        Cand(r.s("trackName"), "${year(r.s("releaseDate"))} · ${r.s("artistName")}", art(r.s("artworkUrl100").ifEmpty { null }, 600),
            mapOf("year" to year(r.s("releaseDate")), "director" to r.s("artistName"), "genre" to r.s("primaryGenreName"),
                "runtime" to (if (r.has("trackTimeMillis")) (r.optDouble("trackTimeMillis") / 60000).toInt().toString() else ""),
                "overview" to r.s("longDescription").ifEmpty { r.s("shortDescription") }))
    }
    return out.ifEmpty { wikiFilms(query) }
}

private suspend fun wikiFilms(query: String): List<Cand> {
    val j = json("https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrlimit=8" +
        "&gsrsearch=${q("$query film")}&prop=pageimages%7Cextracts%7Cdescription" +
        "&piprop=thumbnail&pilicense=any&pithumbsize=600&exintro=1&explaintext=1&exsentences=3&exlimit=max") as? JSONObject ?: return emptyList()
    val pages = j.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
    val list = pages.keys().asSequence().mapNotNull { pages.optJSONObject(it) }.sortedBy { it.optInt("index", 99) }.toList()
    val out = ArrayList<Cand>()
    for (p in list) {
        val d = p.s("description")
        if (!d.lowercase().contains("film")) continue
        val y = Regex("\\b(1[89]\\d\\d|20\\d\\d)\\b").find(d)?.value ?: ""
        val dir = Regex("\\bby ([^,;(]+)").find(d)?.groupValues?.get(1)?.trim() ?: ""
        val title = p.s("title").replace(Regex("\\s*\\([^)]*film\\)$", RegexOption.IGNORE_CASE), "")
        val thumb = p.optJSONObject("thumbnail")?.s("source")?.ifEmpty { null }
        out += Cand(title, "$y · $dir", thumb, mapOf("year" to y, "director" to dir, "genre" to "", "runtime" to "", "overview" to clean(p.s("extract"))))
    }
    return out
}

suspend fun searchTracks(query: String): List<Cand> =
    itunes(query, "music", "song").filter { it.s("trackName").isNotEmpty() }.map { r ->
        Cand(r.s("trackName"), "${r.s("artistName")} · ${r.s("collectionName")}", art(r.s("artworkUrl100").ifEmpty { null }, 600),
            mapOf("artist" to r.s("artistName"), "album" to r.s("collectionName"), "year" to year(r.s("releaseDate")), "genre" to r.s("primaryGenreName")))
    }

/** Books: Open Library first (covers by id), Google Books as a fallback. Neither needs a key. */
suspend fun searchBooks(query: String): List<Cand> {
    val out = ArrayList<Cand>()
    val j = json("https://openlibrary.org/search.json?q=${q(query)}&limit=10&fields=title,author_name,first_publish_year,cover_i,number_of_pages_median,subject") as? JSONObject
    for (d in j?.optJSONArray("docs")?.objs() ?: emptyList()) {
        if (d.s("title").isEmpty()) continue
        val au = d.optJSONArray("author_name")?.optString(0) ?: ""
        val yr = d.s("first_publish_year")
        val sub = d.optJSONArray("subject")?.optString(0) ?: ""
        val cover = d.s("cover_i")
        out += Cand(d.s("title"), "$au · $yr", cover.takeIf { it.isNotEmpty() }?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
            mapOf("author" to au, "year" to yr, "pages" to d.s("number_of_pages_median"), "genre" to sub))
    }
    // books with a cover first, keeping the relevance order otherwise
    val sorted = out.sortedBy { if (it.image == null) 1 else 0 }
    if (sorted.isNotEmpty()) return sorted.take(8)
    val g = json("https://www.googleapis.com/books/v1/volumes?q=${q(query)}&maxResults=8") as? JSONObject
    for (it in g?.optJSONArray("items")?.objs() ?: emptyList()) {
        val v = it.optJSONObject("volumeInfo") ?: continue
        if (v.s("title").isEmpty()) continue
        val au = v.optJSONArray("authors")?.optString(0) ?: ""
        val img = v.optJSONObject("imageLinks")?.s("thumbnail")?.replaceFirst("http://", "https://")?.replaceFirst("zoom=1", "zoom=2")?.ifEmpty { null }
        out += Cand(v.s("title"), "$au · ${year(v.s("publishedDate"))}", img,
            mapOf("author" to au, "year" to year(v.s("publishedDate")), "pages" to v.s("pageCount"), "genre" to (v.optJSONArray("categories")?.optString(0) ?: "")))
    }
    return out
}

/** Open Graph preview of a web page: title, desc, site, image. Special services get their own, more reliable source first. */
suspend fun linkPreview(url: String): Map<String, String> {
    val special = specialPreview(url)
    val og = if (special.isNotEmpty() && special["image"] != null && special["title"]?.isNotEmpty() == true) emptyMap() else ogPreview(url)
    val merged = og + special.filterValues { it.isNotEmpty() }
    if (merged.isEmpty()) return emptyMap()
    return merged + Sources.enrich(url, merged)
}

private suspend fun specialPreview(url: String): Map<String, String> {
    val host = runCatching { URL(url).host.removePrefix("www.").removePrefix("m.") }.getOrDefault("")
    return when {
        host == "open.spotify.com" -> {
            val j = json("https://open.spotify.com/oembed?url=${q(url)}") as? JSONObject
            if (j == null) emptyMap() else mapOf("title" to j.s("title"), "image" to j.s("thumbnail_url"), "site" to "Spotify", "desc" to "")
        }
        host.endsWith("youtube.com") || host == "youtu.be" -> {
            val j = json("https://www.youtube.com/oembed?format=json&url=${q(url)}") as? JSONObject
            if (j == null) emptyMap() else mapOf("title" to j.s("title"), "image" to j.s("thumbnail_url"), "site" to "YouTube", "desc" to j.s("author_name"))
        }
        host.endsWith("wikipedia.org") -> {
            val lang = host.substringBefore('.').takeIf { it.length in 2..3 } ?: "en"
            val title = url.substringAfter("/wiki/", "").substringBefore('#').substringBefore('?')
            if (title.isEmpty()) emptyMap() else {
                val j = json("https://$lang.wikipedia.org/api/rest_v1/page/summary/$title") as? JSONObject
                if (j == null) emptyMap() else mapOf(
                    "title" to j.s("title"), "desc" to j.s("extract"), "site" to "Wikipedia",
                    "image" to (j.optJSONObject("thumbnail")?.s("source") ?: j.optJSONObject("originalimage")?.s("source") ?: ""),
                )
            }
        }
        else -> emptyMap()
    }
}

private suspend fun ogPreview(url: String): Map<String, String> {
    var html = fetchText(url)
    fun meta(h: String, p: String): String? {
        for (re in listOf(
            Regex("<meta[^>]+(?:property|name)=[\"']$p[\"'][^>]*content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE),
            Regex("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']$p[\"']", RegexOption.IGNORE_CASE),
        )) {
            val m = re.find(h)
            if (m != null && m.groupValues[1].isNotBlank()) return clean(m.groupValues[1])
        }
        return null
    }
    if (html == null || meta(html, "og:title") == null) fetchText(url, CRAWLER)?.let { c -> if (html == null || meta(c, "og:title") != null) html = c }
    val h = html ?: return emptyMap()
    val host = runCatching { URL(url).host.removePrefix("www.") }.getOrDefault("")
    val title = meta(h, "og:title") ?: meta(h, "twitter:title") ?: Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE).find(h)?.groupValues?.get(1)
    val img = meta(h, "og:image") ?: meta(h, "twitter:image")
    return buildMap {
        put("title", title?.let { clean(it) } ?: "")
        put("desc", meta(h, "og:description") ?: meta(h, "description") ?: "")
        put("site", meta(h, "og:site_name") ?: host)
        if (img != null) runCatching { put("image", URL(URL(url), img).toString()) }
    }
}

/** Reads a recipe page's schema.org data (most recipe sites publish it). */
suspend fun importRecipe(url: String): Map<String, String> {
    val html = fetchText(url) ?: return emptyMap()
    fun find(j: Any?): JSONObject? {
        when (j) {
            is JSONArray -> for (i in 0 until j.length()) find(j.opt(i))?.let { return it }
            is JSONObject -> {
                val t = j.opt("@type")
                if (t == "Recipe" || (t is JSONArray && (0 until t.length()).any { t.optString(it) == "Recipe" })) return j
                for (k in listOf("@graph", "mainEntity")) if (j.has(k)) find(j.opt(k))?.let { return it }
            }
        }
        return null
    }
    var rec: JSONObject? = null
    for (m in Regex("<script[^>]+ld\\+json[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html)) {
        rec = runCatching { find(org.json.JSONTokener(m.groupValues[1]).nextValue()) }.getOrNull()
        if (rec != null) break
    }
    val r = rec ?: return emptyMap()
    fun img(i: Any?): String? = when (i) { is String -> i; is JSONArray -> if (i.length() > 0) img(i.opt(0)) else null; is JSONObject -> i.s("url"); else -> null }
    fun steps(s: Any?): List<String> {
        val out = ArrayList<String>()
        when (s) {
            is String -> out += s.split(Regex("\\n+"))
            is JSONArray -> for (i in 0 until s.length()) {
                val e = s.opt(i)
                if (e is JSONObject && e.has("itemListElement")) out += steps(e.opt("itemListElement"))
                else if (e is JSONObject) out += e.s("text").ifEmpty { e.s("name") }
                else out += e.toString()
            }
        }
        return out.map { clean(it) }.filter { it.isNotEmpty() }
    }
    fun mins(d: Any?): Int {
        val m = Regex("P(?:\\d+D)?T?(?:(\\d+)H)?(?:(\\d+)M)?").find(d.toString()) ?: return 0
        return (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
    }
    val y = r.opt("recipeYield").let { if (it is JSONArray && it.length() > 0) it.opt(0) else it }
    val ing = r.optJSONArray("recipeIngredient")
    val im = img(r.opt("image"))
    return buildMap {
        put("title", clean(r.s("name")))
        if (im != null) runCatching { put("image", URL(URL(url), im).toString()) }
        put("ingredients", if (ing != null) (0 until ing.length()).map { clean(ing.optString(it)) }.filter { it.isNotEmpty() }.joinToString("\n") else "")
        put("steps", steps(r.opt("recipeInstructions")).joinToString("\n"))
        put("time", mins(r.opt("totalTime") ?: r.opt("cookTime") ?: r.opt("prepTime")).toString())
        put("servings", Regex("\\d+").find(y?.toString() ?: "")?.value ?: "")
        put("url", url)
    }
}
