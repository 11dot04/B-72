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
    /**
     * Gemma 3n E2B, 4-bit, in LiteRT-LM format (about 3.4 GB). Google's repository is gated: the licence has to be accepted on its Hugging
     * Face page once, and a read token from that account is sent with the download. A file already on the phone can be used instead.
     */
    const val MODEL_URL = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm"
    const val MODEL_ID = "gemma-3n-e2b"

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

    private fun idFile(ctx: Context) = File(ctx.filesDir, "b72/model/assistant.id")

    fun refresh(ctx: Context) {
        installed = file(ctx).let { it.exists() && it.length() > 50_000_000 }
        legacy = installed && runCatching { idFile(ctx).readText().trim() }.getOrNull() != MODEL_ID && !imported
    }

    /** True while the model on the phone is the old Qwen one, so the dialog can offer the switch to Gemma. */
    var legacy by mutableStateOf(false)
        private set
    private var imported = false

    fun token(ctx: Context): String = ctx.getSharedPreferences("b72_ai", Context.MODE_PRIVATE).getString("hf", "").orEmpty()
    fun setToken(ctx: Context, t: String) { ctx.getSharedPreferences("b72_ai", Context.MODE_PRIVATE).edit().putString("hf", t.trim()).apply() }

    val downloading get() = job?.isActive == true

    /** Downloads the model (resuming a partial file). Runs on its own scope so closing the dialog does not stop it. */
    fun download(ctx: Context, url: String = MODEL_URL, token: String = token(ctx)) {
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
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                }
                val resumed = c.responseCode == 206
                if (c.responseCode == 401 || c.responseCode == 403) error("Hugging Face refused it: accept the Gemma licence on its page and paste a read token")
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
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) error("Could not save the model")
                runCatching { idFile(app).writeText(MODEL_ID) }
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
            imported = true
            runCatching { idFile(ctx).writeText(MODEL_ID) }
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
Rules for facts: never list the same label twice; one fact per distinct thing; do not use "To" or "From" unless the value is a place; for a trip or event use labels like Destination, Stay, Travel date, Stay dates, Event, Time, Venue; join several dates of one thing into one fact; for money use Total, Balance, Paid, Due only for a single figure, and never repeat Amount or Date for each line (the note's own lines already show them). Do not make a date the starred fact unless it is the only fact that matters.
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

    private const val IDEAS = """Someone saved a photo to their notes. Work out what it is, then give up to 3 helpful things. Reply with ONLY a JSON array, nothing else.
Each item: {"t": a title of 3 to 6 words, "d": the actual answer or tip in one or two plain sentences (max 160 characters), "q": a web search phrase, or "" when you can answer it yourself}.
Answer things yourself whenever you can: how to care for it, how to cook it, what goes with it, what it is, styling or pairing ideas, a useful fact. Only give a "q" for what needs the web, such as where to buy it or what it costs, and then use the exact name the person gave it.
The person's own words (title, text on the photo) are the truth. The photo labels are guesses from a small model and are often wrong; if they clash with the words, ignore them. If you are not sure what it is, reply [].
Never say anything about a person's body, face, health, age or identity.
"""

    /** Ideas for a photo from the person's words and what the labeller saw. Titles, answers and search phrases only. */
    fun ideas(session: Session, labels: List<String>, noteTitle: String, ocr: String): List<Triple<String, String, String>> {
        val prompt = IDEAS + "Title: ${noteTitle.take(80).ifBlank { "(none)" }}\nText on the photo: ${ocr.take(300).ifBlank { "(none)" }}\nLabels: ${labels.take(6).joinToString(", ")}\n"
        val reply = session.ask(prompt) ?: return emptyList()
        val a = reply.indexOf('['); val z = reply.lastIndexOf(']')
        if (a < 0 || z <= a) return emptyList()
        val arr = runCatching { AppJson.parseToJsonElement(reply.substring(a, z + 1)) as? JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val t = s("t"); val q = s("q"); val d = s("d")
            if (t.length !in 3..60 || (q.isEmpty() && d.length < 10) || q.length > 90) null else Triple(t, d.take(200), q)
        }.take(3)
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
