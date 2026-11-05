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
class ArtInfo(val name: String, val kind: String, val facts: List<Pair<String, String>>)

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
            cache[p] = ArtInfo(s("n"), s("k"), facts)
        }
    }

    fun get(ctx: Context, path: String): ArtInfo? { load(ctx); return cache[path] }

    private fun save(ctx: Context) {
        val o = buildJsonObject {
            for ((p, i) in cache) put(p, buildJsonObject {
                put("n", i.name); put("k", i.kind)
                put("f", buildJsonArray { for ((l, v) in i.facts) add(buildJsonObject { put("l", l); put("v", v) }) })
            })
        }
        runCatching { file(ctx).also { it.parentFile?.mkdirs() }.writeText(o.toString()) }
    }

    private const val PROMPT = """A die-cut sticker was cut out of someone's photo. Say what it is. You are given ONLY this evidence from the phone:
labels (with confidence): %s
text printed on it: %s
Reply with ONLY a JSON object, nothing else:
{"name": a specific name of 1 to 4 words (the brand and product when the printed text shows them, otherwise the plainest accurate noun; never a vague word like Sticker or Product when a better one fits), "kind": one lowercase word for its category, "facts": up to 4 objects {"l": label, "v": value} about the subject, such as Brand, Text, Type, Style}
Use only the evidence. If it is not enough to be sure, give the most general accurate name and fewer facts. Never invent a brand, a price or a date."""

    /** Names every artifact in [paths] that has not been named, one model load for the lot. [onEach] is told as each is done. */
    suspend fun run(ctx: Context, paths: List<String>, onEach: () -> Unit) = withContext(Dispatchers.Default) {
        load(ctx)
        val todo = paths.filter { it !in cache && File(it).exists() && !File(it).name.startsWith("st_") }
        if (todo.isEmpty() || !Assistant.installed) return@withContext
        val session = Assistant.open(ctx) ?: return@withContext
        try {
            for (p in todo) {
                val labels = Vision.labels(ctx, p)?.takeIf { it.isNotBlank() } ?: "none"
                val text = Ocr.read(ctx, p)?.replace('\n', ' ')?.trim()?.take(240)?.takeIf { it.isNotBlank() } ?: "none"
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
                }.take(4)
                cache[p] = ArtInfo(name.replaceFirstChar { it.uppercase() }, s("kind").lowercase().take(20), facts)
                save(ctx)
                onEach()
            }
        } finally { session.close() }
    }
}
