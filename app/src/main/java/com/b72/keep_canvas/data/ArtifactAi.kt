package com.b72.keep_canvas.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File

/** What the assistant made of one artifact: a specific name, a one-word kind, and a few plain facts that the evidence supports. */
class ArtInfo(val name: String, val kind: String, val facts: List<Pair<String, String>>, val text: List<String> = emptyList(), val nut: Nutrition? = null, val v: Int = 2)

/** Per 100 g, from Open Food Facts, for a packaged food or drink the assistant could name. [match] is the product the figures belong to. */
class Nutrition(
    val match: String, val grade: String, val qty: String, val kcal: Double?, val fat: Double?, val sat: Double?,
    val sugar: Double?, val salt: Double?, val protein: Double?, val fibre: Double?, val ingredients: String,
)

/**
 * The on-device labeller only knows a few hundred coarse words (Product, Sticker, Musical instrument). The assistant is handed what the
 * phone can read from the cut-out (those labels with their confidence, and any printed text) and asked to say what it most likely is.
 * It is told to use only that evidence, so a guess is a general word rather than a made-up brand. Done once per artifact and kept.
 */
object ArtifactAi {
    private val cache = HashMap<String, ArtInfo>()
    private var loaded = false
    private fun file(ctx: Context) = File(ctx.filesDir, "b72/artifact_info.json")

    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val o = runCatching { AppJson.parseToJsonElement(file(ctx).readText()) as? JsonObject }.getOrNull() ?: return
        for ((p, e) in o) {
            val i = e as? JsonObject ?: continue
            fun s(k: String) = (i[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val facts = (i["f"] as? JsonArray).orEmpty().mapNotNull { f -> (f as? JsonObject)?.let { (it["l"] as? JsonPrimitive)?.contentOrNull.orEmpty() to (it["v"] as? JsonPrimitive)?.contentOrNull.orEmpty() } }
            val text = (i["t"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val nu = i["nu"] as? JsonObject
            fun d(k: String) = (nu?.get(k) as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
            fun ns(k: String) = (nu?.get(k) as? JsonPrimitive)?.contentOrNull.orEmpty()
            val nut = nu?.let { Nutrition(ns("m"), ns("g"), ns("q"), d("kcal"), d("fat"), d("sat"), d("sug"), d("salt"), d("pro"), d("fib"), ns("ing")) }
            cache[p] = ArtInfo(s("n"), s("k"), facts, text, nut, (i["v"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1)
        }
    }

    fun get(ctx: Context, path: String): ArtInfo? { load(ctx); return cache[path] }

    private fun save(ctx: Context) {
        val o = buildJsonObject {
            for ((p, i) in cache) put(p, buildJsonObject {
                put("n", i.name); put("k", i.kind); put("v", i.v)
                put("t", buildJsonArray { for (t in i.text) add(JsonPrimitive(t)) })
                i.nut?.let { n ->
                    put("nu", buildJsonObject {
                        put("m", n.match); put("g", n.grade); put("q", n.qty); put("ing", n.ingredients)
                        n.kcal?.let { put("kcal", it) }; n.fat?.let { put("fat", it) }; n.sat?.let { put("sat", it) }; n.sugar?.let { put("sug", it) }
                        n.salt?.let { put("salt", it) }; n.protein?.let { put("pro", it) }; n.fibre?.let { put("fib", it) }
                    })
                }
                put("f", buildJsonArray { for ((l, v) in i.facts) add(buildJsonObject { put("l", l); put("v", v) }) })
            })
        }
        runCatching { file(ctx).also { it.parentFile?.mkdirs() }.writeText(o.toString()) }
    }

    private const val PROMPT = """A die-cut sticker was cut out of someone's photo. Say what it is. You are given ONLY this evidence from the phone:
labels (with confidence): %s
text printed on it: %s
Reply with ONLY a JSON object, nothing else:
{"name": a specific name of 1 to 4 words (the brand and product when the printed text shows them, otherwise the plainest accurate noun; never a vague word like Sticker or Product when a better one fits),
 "kind": exactly one of: food, drink, plant, clothing, electronics, household, toy, tool, book, other,
 "lookup": for a packaged food or drink, the brand and product name as printed on it; otherwise an empty string,
 "facts": up to 4 objects like {"l": "Brand", "v": "Sara Clean"}, where "l" is the name of the fact and "v" is its value; never write the word label or value themselves. Suited to the kind: food or drink: Brand, Variety, Size. plant: Light, Water. clothing: Type, Material. electronics: Brand, Model. household, toy or tool: Brand, Use. Only what the evidence supports. Never repeat the name, never use "Text" as a label}
Use only the evidence. If it is not enough to be sure, give the most general accurate name and fewer facts. Never invent a brand, a price or a date."""

    /** Labels a small model writes when it copies the prompt's placeholders instead of naming the fact. */
    val JUNK_LABELS = setOf("text", "label", "value", "l", "v", "fact", "name")

    /** The printed words worth showing: short lines of real letters, once each. */
    private fun printed(raw: String?): List<String> = raw.orEmpty().lines().map { it.trim().trim('.', ',', ':', ';') }
        .filter { it.length in 3..28 && it.count { c -> c.isLetter() } >= 3 }.distinctBy { it.lowercase() }.take(8)

    /** Open Food Facts: the first hit whose name shares a real word with [query] and that has nutrition figures. */
    suspend fun nutrition(query: String): Nutrition? {
        val words = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
        if (words.isEmpty()) return null
        val url = "https://world.openfoodfacts.org/cgi/search.pl?search_simple=1&action=process&json=1&page_size=6" +
            "&fields=product_name,brands,quantity,nutriscore_grade,nutriments,ingredients_text&search_terms=" + java.net.URLEncoder.encode(query, "UTF-8")
        val text = fetchText(url, "B72/2.0 (Android notes app)") ?: return null
        val prods = runCatching { org.json.JSONObject(text).optJSONArray("products") }.getOrNull() ?: return null
        for (i in 0 until prods.length()) {
            val o = prods.optJSONObject(i) ?: continue
            val name = o.optString("product_name"); val brand = o.optString("brands")
            val hay = "$name $brand".lowercase()
            val n = o.optJSONObject("nutriments") ?: continue
            if (!n.has("energy-kcal_100g") || words.none { it in hay }) continue
            fun d(k: String) = if (n.has(k)) n.optDouble(k).takeIf { !it.isNaN() } else null
            return Nutrition(
                listOf(brand.substringBefore(',').trim(), name).filter { it.isNotEmpty() }.joinToString(" "), o.optString("nutriscore_grade").uppercase().takeIf { it.length == 1 && it[0] in 'A'..'E' }.orEmpty(),
                o.optString("quantity"), d("energy-kcal_100g"), d("fat_100g"), d("saturated-fat_100g"), d("sugars_100g"), d("salt_100g"), d("proteins_100g"), d("fiber_100g"),
                o.optString("ingredients_text").take(400),
            )
        }
        return null
    }

    /** Names every artifact in [paths] that has not been named, one model load for the lot. [onEach] is told as each is done. */
    suspend fun run(ctx: Context, paths: List<String>, onEach: () -> Unit) = withContext(Dispatchers.Default) {
        load(ctx)
        val todo = paths.filter { (cache[it]?.v != 2 || cache[it]?.facts?.any { f -> f.first.lowercase() in JUNK_LABELS } == true) && File(it).exists() && !File(it).name.startsWith("st_") }
        if (todo.isEmpty() || !Assistant.installed) return@withContext
        val session = Assistant.open(ctx) ?: return@withContext
        try {
            for (p in todo) {
                val labels = Vision.labels(ctx, p)?.takeIf { it.isNotBlank() } ?: "none"
                val raw = Ocr.read(ctx, p)
                val text = raw?.replace('\n', ' ')?.trim()?.take(240)?.takeIf { it.isNotBlank() } ?: "none"
                val shown = printed(raw)
                val reply = session.ask(PROMPT.format(labels, text)) ?: continue
                val a = reply.indexOf('{'); val z = reply.lastIndexOf('}')
                if (a < 0 || z <= a) continue
                val o = runCatching { AppJson.parseToJsonElement(reply.substring(a, z + 1)) as? JsonObject }.getOrNull() ?: continue
                fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                val name = s("name").trim('"', ' ', '.').take(40)
                if (name.isEmpty()) continue
                val facts = (o["facts"] as? JsonArray).orEmpty().mapNotNull { f ->
                    val fo = f as? JsonObject ?: return@mapNotNull null
                    val l = (fo["l"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(); val v = (fo["v"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                    if (l.isEmpty() || v.isEmpty() || l.length > 20 || v.length > 60) null else l.replaceFirstChar { it.uppercase() } to v
                }.filter { it.first.lowercase() !in JUNK_LABELS && !it.second.equals(name, true) }.take(4)
                val kind = s("kind").lowercase().take(20)
                val look = s("lookup").takeIf { kind == "food" || kind == "drink" }.orEmpty()
                val nut = if (look.isNotBlank()) runCatching { nutrition(look) }.getOrNull() else null
                cache[p] = ArtInfo(name.replaceFirstChar { it.uppercase() }, kind, facts, shown, nut)
                save(ctx)
                onEach()
            }
        } finally { session.close() }
    }
}
