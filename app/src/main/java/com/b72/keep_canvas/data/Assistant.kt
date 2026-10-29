package com.b72.keep_canvas.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId

/** What the language model made of one block of text. */
class Analysis(val summary: String, val tags: List<String>, val facts: List<AiFact>, val trip: Trip?, val title: String = "")
class AiFact(val label: String, val value: String, val star: Boolean)

/**
 * The optional on-device assistant: a small language model (LiteRT-LM) that runs on the phone, with nothing sent anywhere.
 * It suggests tags, writes a one-line summary, reads out the facts of a note (marking the one that matters) and reads tickets.
 * Without the model file the app works exactly as before, on its built-in readers.
 */
object Assistant {
    /** Ungated Qwen2.5 1.5B, 8-bit, in LiteRT-LM format (about 1.6 GB). */
    const val MODEL_URL = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"

    var installed by mutableStateOf(false)
        private set
    /** 0..1 while downloading, -1 when idle. */
    var progress by mutableFloatStateOf(-1f)
        private set
    var message by mutableStateOf("")
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private fun file(ctx: Context) = File(ctx.filesDir, "b72/model/assistant.litertlm")

    fun refresh(ctx: Context) { installed = file(ctx).let { it.exists() && it.length() > 50_000_000 } }

    val downloading get() = job?.isActive == true

    /** Downloads the model (resuming a partial file). Runs on its own scope so closing the dialog does not stop it. */
    fun download(ctx: Context, url: String = MODEL_URL) {
        if (downloading) return
        val app = ctx.applicationContext
        job = scope.launch {
            message = ""
            progress = 0f
            val dest = file(app).also { it.parentFile?.mkdirs() }
            val part = File(dest.path + ".part")
            try {
                val have = if (part.exists()) part.length() else 0L
                val c = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000; readTimeout = 30_000
                    if (have > 0) setRequestProperty("Range", "bytes=$have-")
                }
                val resumed = c.responseCode == 206
                if (c.responseCode !in 200..299) error("The server answered ${c.responseCode}")
                val total = (if (resumed) have else 0L) + c.contentLengthLong
                var got = if (resumed) have else 0L
                c.inputStream.use { i ->
                    java.io.FileOutputStream(part, resumed).use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = i.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            got += n
                            if (total > 0) progress = (got.toDouble() / total).toFloat().coerceIn(0f, 1f)
                        }
                    }
                }
                if (!part.renameTo(dest)) error("Could not save the model")
                refresh(app)
                message = "Assistant ready"
            } catch (e: Throwable) {
                message = "Download stopped (${e.message ?: "no connection"}). Try again to resume."
            }
            progress = -1f
        }
    }

    /** Uses a model file the person already has. */
    suspend fun import(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dest = file(ctx).also { it.parentFile?.mkdirs() }
            ctx.contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            refresh(ctx)
            installed
        }.getOrDefault(false)
    }

    fun remove(ctx: Context) {
        job?.cancel()
        runCatching { file(ctx).delete(); File(file(ctx).path + ".part").delete() }
        progress = -1f
        refresh(ctx)
    }

    /** One loaded model, used for a batch of notes and then closed (it holds a lot of memory). */
    class Session internal constructor(private val engine: Engine) : AutoCloseable {
        fun ask(prompt: String): String? = try {
            engine.createConversation().use { it.sendMessage(prompt).toString() }
        } catch (e: Throwable) { null }

        override fun close() { runCatching { engine.close() } }
    }

    fun open(ctx: Context): Session? {
        if (!installed) return null
        return try {
            val e = Engine(EngineConfig(modelPath = file(ctx).path, backend = Backend.CPU(), cacheDir = ctx.cacheDir.path))
            e.initialize()
            Session(e)
        } catch (t: Throwable) { null }
    }

    private const val PROMPT = """You read one personal note and reply with ONLY a JSON object, nothing else.
Keys:
"title": a title of 2 to 6 words for the note, or "" when it is too short to need one
"summary": one plain sentence of at most 25 words, or "" when the note is shorter than 60 words
"tags": up to 4 short lowercase topic words
"facts": up to 6 objects {"l": label, "v": value, "s": true or false}. Labels are one or two words (Amount, Deadline, Seat, Dose, Score, Address...). Copy values exactly as written in the note. Set "s" to true for the single most important value, and for no other fact.
"trip": null, or when the note is a travel ticket or booking: {"mode":"train or flight or bus","number":"","name":"","pnr":"","from":"","fromCode":"","to":"","toCode":"","dep":"YYYY-MM-DD HH:mm","arr":"YYYY-MM-DD HH:mm"}
Note:
"""

    fun parse(reply: String): Analysis? {
        val a = reply.indexOf('{'); val z = reply.lastIndexOf('}')
        if (a < 0 || z <= a) return null
        val o = runCatching { AppJson.parseToJsonElement(reply.substring(a, z + 1)) as? JsonObject }.getOrNull() ?: return null
        fun s(e: JsonElement?) = (e as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val facts = (o["facts"] as? JsonArray).orEmpty().mapNotNull { f ->
            val fo = f as? JsonObject ?: return@mapNotNull null
            val l = s(fo["l"]); val v = s(fo["v"])
            if (l.isEmpty() || v.isEmpty() || l.length > 24 || v.length > 80) null
            else AiFact(l.replaceFirstChar { it.uppercase() }, v, (fo["s"] as? JsonPrimitive)?.booleanOrNull == true)
        }.let { fs -> var seen = false; fs.map { if (it.star && !seen) { seen = true; it } else AiFact(it.label, it.value, false) } }
        val tags = (o["tags"] as? JsonArray).orEmpty().map { s(it).lowercase().trim('#', ' ') }.filter { it.length in 2..24 }.distinct().take(4)
        return Analysis(s(o["summary"]), tags, facts, (o["trip"] as? JsonObject)?.let { tripOf(it) }, s(o["title"]).trim('"', ' ', '.').take(60))
    }

    private fun tripOf(o: JsonObject): Trip? {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim().orEmpty()
        fun ms(k: String) = runCatching { LocalDateTime.parse(s(k).replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrDefault(0L)
        val mode = s("mode").lowercase().takeIf { it in setOf("train", "flight", "bus") } ?: return null
        val dep = ms("dep")
        if (dep == 0L) return null
        val arr = ms("arr").takeIf { it > dep } ?: 0L
        return Trip(mode, s("number").uppercase().replace(" ", ""), s("name"), s("pnr").uppercase(), s("from"), s("fromCode").uppercase(), s("to"), s("toCode").uppercase(), dep, arr)
    }

    /** One free-form answer (the brainstorm): loads the model, asks, and lets go of it again. */
    suspend fun generate(ctx: Context, prompt: String): String? = withContext(Dispatchers.Default) {
        val s = open(ctx) ?: return@withContext null
        try { s.ask(prompt)?.trim()?.takeIf { it.isNotEmpty() } } finally { s.close() }
    }

    fun analyse(session: Session, text: String): Analysis? = session.ask(PROMPT + "\"\"\"\n" + text.take(3000) + "\n\"\"\"")?.let { parse(it) }
}

/** Kinds whose text is worth sending to the assistant. */
private val AI_KINDS = setOf(Kind.Text, Kind.Poster, Kind.Info, Kind.Photo)

fun aiStale(b: Block): Boolean = Assistant.installed && b.kind in AI_KINDS && b.tripText().length >= 40 && b.dbl("aiH") != tripHash(b).toDouble()

/** The facts the assistant found for a block, in the order it gave them. */
fun Block.aiFacts(): List<AiFact> = (data["aiFacts"] as? JsonArray).orEmpty().mapNotNull { f ->
    val o = f as? JsonObject ?: return@mapNotNull null
    val l = (o["l"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
    val v = (o["v"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
    AiFact(l, v, (o["s"] as? JsonPrimitive)?.booleanOrNull == true)
}

fun Block.aiTags(): List<String> = (data["aiTags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

fun Analysis.toData(b: Block): Map<String, JsonElement> = buildMap {
    put("aiH", JsonPrimitive(tripHash(b)))
    put("aiSum", JsonPrimitive(summary))
    put("aiTitle", JsonPrimitive(title))
    put("aiTags", JsonArray(tags.map { JsonPrimitive(it) }))
    put("aiFacts", JsonArray(facts.map { f -> buildJsonObject { put("l", f.label); put("v", f.value); put("s", f.star) } }))
}

/** Suggested tags for a note: what the assistant proposed for its blocks, minus those the note already has. */
fun Note.suggestedTags(): List<String> {
    val have = tags.map { it.lowercase() }.toSet()
    return blocks.flatMap { it.aiTags() }.distinct().filter { it.lowercase() !in have }.take(4)
}

fun Note.summary(): String = blocks.firstNotNullOfOrNull { b -> (b.data["aiSum"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }.orEmpty()
