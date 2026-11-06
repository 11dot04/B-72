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

/** TMDB series: creator, genre and seasons. */
private suspend fun tmdbShows(query: String): List<Cand> {
    val key = BuildConfig.TMDB_KEY
    if (key.isEmpty()) return emptyList()
    val j = json("https://api.themoviedb.org/3/search/tv?api_key=$key&include_adult=false&query=${q(query)}") as? JSONObject ?: return emptyList()
    val top = j.optJSONArray("results")?.objs()?.take(6) ?: return emptyList()
    val details = coroutineScope { top.map { r -> async { json("https://api.themoviedb.org/3/tv/${r.optLong("id")}?api_key=$key") as? JSONObject } }.awaitAll() }
    val out = ArrayList<Cand>()
    for ((i, r) in top.withIndex()) {
        val d = details[i]
        val title = r.s("name"); if (title.isEmpty()) continue
        val creator = d?.optJSONArray("created_by")?.objs()?.firstOrNull()?.s("name") ?: ""
        val genre = d?.optJSONArray("genres")?.objs()?.firstOrNull()?.s("name") ?: ""
        val seasons = d?.optInt("number_of_seasons")?.takeIf { it > 0 }?.toString() ?: ""
        val y = year(r.s("first_air_date")); val poster = r.s("poster_path")
        out += Cand(title, "Series · $y" + (if (seasons.isNotEmpty()) " · $seasons seasons" else ""), poster.takeIf { it.isNotEmpty() }?.let { "https://image.tmdb.org/t/p/w500$it" },
            mapOf("year" to y, "director" to creator, "genre" to genre, "runtime" to "", "seasons" to seasons, "series" to "1", "overview" to r.s("overview")))
    }
    return out
}

private suspend fun itunesShows(query: String): List<Cand> =
    itunes(query, "tvShow", "tvSeason").filter { it.s("collectionName").isNotEmpty() }.map { r ->
        Cand(r.s("collectionName"), "Series · ${year(r.s("releaseDate"))}", art(r.s("artworkUrl100").ifEmpty { null }, 600),
            mapOf("year" to year(r.s("releaseDate")), "director" to "", "genre" to r.s("primaryGenreName"), "runtime" to "", "series" to "1",
                "overview" to r.s("longDescription").ifEmpty { r.s("shortDescription") }))
    }

/** Films and series together, interleaved. */
suspend fun searchFilmsAndShows(query: String): List<Cand> {
    val (f, sh) = coroutineScope { val a = async { searchFilms(query) }; val b = async { tmdbShows(query).ifEmpty { itunesShows(query) } }; a.await() to b.await() }
    val out = ArrayList<Cand>()
    for (i in 0 until maxOf(f.size, sh.size)) { f.getOrNull(i)?.let { out += it }; sh.getOrNull(i)?.let { out += it } }
    return out.take(12)
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

/** The width-to-height of a cover image, or null when there is no image there. Reads only the header, from the small size. */
private suspend fun coverShape(url: String): Float? = withContext(Dispatchers.IO) {
    val bytes = get(url) ?: return@withContext null
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    if (o.outWidth < 60 || o.outHeight < 60) null else o.outWidth.toFloat() / o.outHeight
}

/** A book jacket is about two thirds as wide as it is tall; a photograph of a book on a table, or a badly cropped scan, is not. */
private fun jacket(aspect: Float?) = aspect != null && aspect in .5f..0.8f

/** The English title of a book known by a foreign or romanised one (Sasameyuki -> The Makioka Sisters), from Wikipedia. */
private suspend fun englishTitle(query: String): String? {
    val j = json("https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrlimit=4" +
        "&gsrsearch=${q("$query novel")}&prop=description") as? JSONObject ?: return null
    val pages = j.optJSONObject("query")?.optJSONObject("pages") ?: return null
    val list = pages.keys().asSequence().mapNotNull { pages.optJSONObject(it) }.sortedBy { it.optInt("index", 99) }
    for (p in list) {
        val d = p.s("description").lowercase()
        if (Regex("novel|novella|book|memoir|short story|story collection|poem|play|manga").containsMatchIn(d)) {
            return p.s("title").replace(Regex("\\s*\\([^)]*\\)$"), "").trim().ifEmpty { null }
        }
    }
    return null
}

private suspend fun openLibrary(query: String, english: Boolean): List<JSONObject> {
    val qq = if (english) "$query language:eng" else query
    val fields = "key,title,author_name,first_publish_year,cover_i,number_of_pages_median,subject,editions,editions.key,editions.title,editions.language,editions.cover_i,editions.number_of_pages"
    val j = json("https://openlibrary.org/search.json?q=${q(qq)}&limit=10&lang=en&fields=${q(fields)}") as? JSONObject
    return j?.optJSONArray("docs")?.objs()?.filter { it.s("title").isNotEmpty() } ?: emptyList()
}

/** A cover from Google Books for the English edition of [title], when Open Library has no good one. */
private suspend fun googleCover(title: String, author: String): String? {
    val qs = "intitle:\"$title\"" + if (author.isNotEmpty()) " inauthor:\"$author\"" else ""
    val g = json("https://www.googleapis.com/books/v1/volumes?q=${q(qs)}&maxResults=5&langRestrict=en&printType=books") as? JSONObject ?: return null
    for (it in g.optJSONArray("items")?.objs() ?: emptyList()) {
        val img = it.optJSONObject("volumeInfo")?.optJSONObject("imageLinks")?.s("thumbnail")?.ifEmpty { null } ?: continue
        val big = img.replaceFirst("http://", "https://").replace("&edge=curl", "").replaceFirst("zoom=1", "zoom=2")
        if (jacket(coverShape(big))) return big
    }
    return null
}

/** True for a name written in a Latin script (Western names, with their accents); false for Japanese, Chinese, Korean, Cyrillic and the like. */
fun isLatin(s: String) = s.none { it.isLetter() && it.code > 0x24F && it.code !in 0x1E00..0x1EFF }

/** What Google Books says about the English edition of a book: the author as written in English, a description, publisher and page count. */
class GBook(val author: String, val desc: String, val pages: String, val publisher: String, val year: String, val genre: String)

private fun stripTags(t: String) = t.replace(Regex("<[^>]*>"), " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace(Regex("\\s+"), " ").trim()

suspend fun googleBook(title: String, author: String): GBook? {
    val qs = "intitle:\"$title\"" + if (author.isNotEmpty() && isLatin(author)) " inauthor:\"$author\"" else ""
    val g = json("https://www.googleapis.com/books/v1/volumes?q=${q(qs)}&maxResults=6&langRestrict=en&printType=books") as? JSONObject ?: return null
    val vols = g.optJSONArray("items")?.objs()?.mapNotNull { it.optJSONObject("volumeInfo") } ?: return null
    // the first edition that has an English description, else the first at all
    val v = vols.firstOrNull { it.s("description").isNotEmpty() && isLatin(it.optJSONArray("authors")?.optString(0) ?: "") } ?: vols.firstOrNull { isLatin(it.optJSONArray("authors")?.optString(0) ?: "x") } ?: return null
    return GBook(
        v.optJSONArray("authors")?.optString(0) ?: "", stripTags(v.s("description")).take(700), v.s("pageCount").takeIf { it != "0" } ?: "",
        v.s("publisher"), year(v.s("publishedDate")), v.optJSONArray("categories")?.optString(0) ?: "",
    )
}

/** One cover to choose between: where its picture is and who published that edition. */
class CoverOption(val url: String, val label: String)

private val HOUSES = listOf("penguin", "harpercollins", "harper", "vintage", "faber", "picador", "knopf", "modern library", "everyman", "pan macmillan", "bloomsbury", "oxford", "norton", "new directions", "farrar")

/**
 * Every English edition's jacket for a book that Open Library and Google Books know of, labelled with its publisher and year, so the
 * Penguin or HarperCollins edition can be picked out by eye. There is no publisher catalogue to ask directly; these are the sources
 * that carry their jackets. The big houses come first, and anything that is not shaped like a jacket is left out.
 */
suspend fun bookCovers(title: String, author: String): List<CoverOption> {
    val raw = ArrayList<CoverOption>()
    val key = (json("https://openlibrary.org/search.json?q=${q("title:\"$title\"" + if (author.isNotEmpty()) " author:\"$author\"" else "")}&limit=3&fields=key") as? JSONObject)
        ?.optJSONArray("docs")?.objs()?.firstOrNull()?.s("key")?.ifEmpty { null }
    if (key != null) {
        val eds = (json("https://openlibrary.org$key/editions.json?limit=60") as? JSONObject)?.optJSONArray("entries")?.objs() ?: emptyList()
        for (e in eds) {
            val langs = e.optJSONArray("languages")?.objs()?.map { it.s("key") } ?: emptyList()
            if (langs.isNotEmpty() && "/languages/eng" !in langs) continue
            val id = e.optJSONArray("covers")?.optInt(0, 0)?.takeIf { it > 0 } ?: continue
            val pub = e.optJSONArray("publishers")?.optString(0) ?: ""
            raw += CoverOption("https://covers.openlibrary.org/b/id/$id-L.jpg", listOf(pub, year(e.s("publish_date").takeLast(4))).filter { it.isNotEmpty() }.joinToString(" · "))
        }
    }
    val qs = "intitle:\"$title\"" + if (author.isNotEmpty()) " inauthor:\"$author\"" else ""
    val g = json("https://www.googleapis.com/books/v1/volumes?q=${q(qs)}&maxResults=20&langRestrict=en&printType=books") as? JSONObject
    for (it in g?.optJSONArray("items")?.objs() ?: emptyList()) {
        val v = it.optJSONObject("volumeInfo") ?: continue
        val img = v.optJSONObject("imageLinks")?.s("thumbnail")?.ifEmpty { null } ?: continue
        raw += CoverOption(img.replaceFirst("http://", "https://").replace("&edge=curl", "").replaceFirst("zoom=1", "zoom=2"), listOf(v.s("publisher"), v.s("publishedDate").take(4)).filter { it.isNotEmpty() }.joinToString(" · "))
    }
    val ok = coroutineScope { raw.distinctBy { it.url }.take(24).map { c -> async { c.takeIf { jacket(coverShape(c.url.replace("-L.jpg", "-M.jpg"))) } } }.awaitAll().filterNotNull() }
    return ok.sortedBy { c -> if (HOUSES.any { c.label.lowercase().contains(it) }) 0 else 1 }
}

/**
 * Books from Open Library, in English where there is an English edition (title and cover are the English edition's). A book known by
 * a foreign title is found through Wikipedia. Of the covers a book has, one shaped like a jacket wins over a photograph or a bad crop,
 * and Google Books fills in when Open Library has none that will do. Neither source needs a key.
 */
suspend fun searchBooks(query: String): List<Cand> {
    var docs = openLibrary(query, true)
    val foreign = query.any { it.isLetter() && it.code > 0x24F }
    if (docs.isEmpty() || foreign) {
        val en = englishTitle(query)
        if (en != null && !en.equals(query, true)) {
            val more = openLibrary(en, true)
            docs = if (foreign) more + docs else docs.ifEmpty { more }
        }
    }
    if (docs.isEmpty()) docs = openLibrary(query, false)
    if (docs.isNotEmpty()) {
        val out = coroutineScope {
            docs.distinctBy { it.s("key") }.take(8).map { d -> async {
                val ed = d.optJSONObject("editions")?.optJSONArray("docs")?.objs()?.firstOrNull()
                val english = ed?.optJSONArray("language")?.let { a -> (0 until a.length()).any { a.optString(it) == "eng" } } == true
                val title = if (english && ed?.s("title")?.isNotEmpty() == true) ed.s("title") else d.s("title")
                var au = d.optJSONArray("author_name")?.optString(0) ?: ""
                val yr = d.s("first_publish_year")
                // the English edition's details: the author's name in Latin letters, and what the book is about
                val gb = runCatching { googleBook(title, au) }.getOrNull()
                if (!isLatin(au) && gb != null && gb.author.isNotEmpty()) au = gb.author
                val ids = listOfNotNull(ed?.s("cover_i")?.ifEmpty { null }, d.s("cover_i").ifEmpty { null }).distinct()
                var cover: String? = null
                for (id in ids) { if (jacket(coverShape("https://covers.openlibrary.org/b/id/$id-M.jpg?default=false"))) { cover = "https://covers.openlibrary.org/b/id/$id-L.jpg"; break } }
                if (cover == null) cover = googleCover(title, au)
                if (cover == null && ids.isNotEmpty()) cover = "https://covers.openlibrary.org/b/id/${ids.first()}-L.jpg"
                val pages = ed?.s("number_of_pages")?.ifEmpty { null } ?: d.s("number_of_pages_median")
                Cand(title, "$au · $yr", cover, mapOf("author" to au, "year" to yr, "pages" to pages.ifEmpty { gb?.pages ?: "" }, "genre" to (d.optJSONArray("subject")?.optString(0) ?: gb?.genre ?: ""), "overview" to (gb?.desc ?: ""), "publisher" to (gb?.publisher ?: "")))
            } }.awaitAll()
        }
        // books with a cover first, keeping the relevance order otherwise
        return out.sortedBy { if (it.image == null) 1 else 0 }
    }
    val out = ArrayList<Cand>()
    val g = json("https://www.googleapis.com/books/v1/volumes?q=${q(query)}&maxResults=8&langRestrict=en&printType=books") as? JSONObject
    for (it in g?.optJSONArray("items")?.objs() ?: emptyList()) {
        val v = it.optJSONObject("volumeInfo") ?: continue
        if (v.s("title").isEmpty()) continue
        val au = v.optJSONArray("authors")?.optString(0) ?: ""
        val img = v.optJSONObject("imageLinks")?.s("thumbnail")?.replaceFirst("http://", "https://")?.replace("&edge=curl", "")?.replaceFirst("zoom=1", "zoom=2")?.ifEmpty { null }
        out += Cand(v.s("title"), "$au · ${year(v.s("publishedDate"))}", img,
            mapOf("author" to au, "year" to year(v.s("publishedDate")), "pages" to v.s("pageCount"), "genre" to (v.optJSONArray("categories")?.optString(0) ?: ""), "overview" to stripTags(v.s("description")).take(700), "publisher" to v.s("publisher")))
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

// ------------------------------------------------------------------ lyrics

/** Streaming services decorate titles ("- Remastered 2011", "(feat. X)", "[Live]"); a lyrics database has the bare title. */
private fun cleanTitle(t: String): String {
    var s = t
    s = s.replace(Regex("\\s*[(\\[][^)\\]]*(feat|ft\\.|with |remaster|live|version|edit|mix|from |official|lyric|video|audio|mono|stereo|deluxe|bonus|explicit|soundtrack)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("\\s+[-\u2013\u2014]\\s+(\\d{4}\\s+)?(remaster(ed)?|live|single|mono|stereo|radio|album|deluxe|bonus|from|feat|version|edit|mix).*$", RegexOption.IGNORE_CASE), "")
    s = s.replace(Regex("\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE), "")
    return s.trim()
}

private fun firstArtist(a: String) = a.split(Regex("\\s*(,|;|/|&|\\sx\\s|\\sfeat\\.?\\s|\\sft\\.?\\s|\\sfeaturing\\s)\\s*", RegexOption.IGNORE_CASE)).first().trim()

private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** One song a lyrics source offered. */
private class Hit(val title: String, val artist: String, val plain: String, val synced: String, val instrumental: Boolean = false)

/** How well a hit is the song asked for; below 3 it is some other song. */
private fun score(h: Hit, title: String, artist: String): Float {
    val ht = norm(cleanTitle(h.title)); val wt = norm(title)
    var sc = when {
        ht == wt -> 3f
        ht.isNotEmpty() && wt.isNotEmpty() && (ht.contains(wt) || wt.contains(ht)) -> 3f * minOf(ht.length, wt.length) / maxOf(ht.length, wt.length) + .4f
        else -> 0f
    }
    if (artist.isNotBlank()) {
        val ha = norm(h.artist); val wa = norm(firstArtist(artist))
        sc += if (ha.isNotEmpty() && wa.isNotEmpty() && (ha.contains(wa) || wa.contains(ha))) 2f else -1.5f
    }
    if (h.instrumental) sc -= 5f
    if (h.plain.isNotBlank() || h.synced.isNotBlank()) sc += .5f else sc -= 5f
    return sc
}

private fun best(hits: List<Hit>, title: String, artist: String): Hit? =
    hits.maxByOrNull { score(it, title, artist) }?.takeIf { score(it, title, artist) >= 3f }

/** Synced lyrics have a time on every line; the card only wants the words. */
private fun toLines(text: String): List<String> = text.lines()
    .map { it.replace(Regex("\\[[^\\]]*]"), "").replace(Regex("<[^>]*>"), "").trim() }
    .filter { it.isNotEmpty() && !Regex("^(\u4f5c\u8bcd|\u4f5c\u66f2|\u7f16\u66f2|\u5236\u4f5c\u4eba|lyrics by|composed by|music by)\\s*[:\uff1a]", RegexOption.IGNORE_CASE).containsMatchIn(it) }

private suspend fun lrclib(title: String, artist: String): Hit? {
    val tries = listOf(
        "track_name=${q(title)}&artist_name=${q(artist)}",
        "track_name=${q(title)}&artist_name=${q(firstArtist(artist))}",
        "q=${q("$title $artist".trim())}",
        "q=${q("$title ${firstArtist(artist)}".trim())}",
        "q=${q(title)}",
    ).distinct()
    for (qs in tries) {
        val arr = json("https://lrclib.net/api/search?$qs") as? JSONArray ?: continue
        val hits = arr.objs().map { Hit(it.s("trackName"), it.s("artistName"), it.s("plainLyrics"), it.s("syncedLyrics"), it.optBoolean("instrumental")) }
        best(hits, title, artist)?.let { return it }
    }
    return null
}

private suspend fun netease(title: String, artist: String): Hit? {
    val j = json("https://music.163.com/api/search/get/web?s=${q("$title $artist".trim())}&type=1&offset=0&limit=8") as? JSONObject ?: return null
    val songs = j.optJSONObject("result")?.optJSONArray("songs")?.objs() ?: return null
    val cands = songs.map { Triple(it.optLong("id"), it.s("name"), it.optJSONArray("artists")?.optJSONObject(0)?.s("name").orEmpty()) }
    val pick = cands.maxByOrNull { score(Hit(it.second, it.third, "x", ""), title, artist) }?.takeIf { score(Hit(it.second, it.third, "x", ""), title, artist) >= 3f } ?: return null
    val l = json("https://music.163.com/api/song/lyric?id=${pick.first}&lv=1&kv=1&tv=-1") as? JSONObject ?: return null
    val lrc = l.optJSONObject("lrc")?.s("lyric").orEmpty()
    return if (lrc.isBlank()) null else Hit(pick.second, pick.third, "", lrc)
}

private suspend fun lyricsOvh(title: String, artist: String): Hit? {
    if (artist.isBlank()) return null
    fun seg(x: String) = q(x).replace("+", "%20")
    val j = json("https://api.lyrics.ovh/v1/${seg(firstArtist(artist))}/${seg(title)}") as? JSONObject ?: return null
    val t = j.s("lyrics")
    return if (t.isBlank()) null else Hit(title, artist, t, "")
}

/**
 * The lines of a song, asked of several free sources in turn until one has it: LRCLIB (searched four ways, because its exact lookup
 * misses most songs), then NetEase, then lyrics.ovh. Titles are cleaned of "remastered" and "feat." trimmings first, and a result is
 * only taken when its title and artist actually match, so a different song is never passed off as this one.
 */
suspend fun fetchLyrics(artist: String, title: String): List<String> {
    val t = cleanTitle(title)
    if (t.isBlank()) return emptyList()
    val sources: List<suspend (String, String) -> Hit?> = listOf(::lrclib, ::netease, ::lyricsOvh)
    for (src in sources) {
        val hit = runCatching { src(t, artist) }.getOrNull() ?: continue
        val lines = toLines(hit.plain.ifBlank { hit.synced })
        if (lines.size >= 4) return lines
    }
    return emptyList()
}
