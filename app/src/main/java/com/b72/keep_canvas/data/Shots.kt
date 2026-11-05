package com.b72.keep_canvas.data

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File

/** A screenshot as the media store knows it. */
class Shot(val id: Long, val uri: Uri, val taken: Long, val w: Int, val h: Int)

/** What the phone worked out about one screenshot, and what was decided about it. [state]: "new", "kept" or "drop" (queued for deletion). */
class ShotInfo(
    val title: String, val summary: String, val category: String, val oneTime: Boolean, val tags: List<String>, val text: String,
    val state: String = "new", val ai: Boolean = false,
)

/**
 * Pixel-Screenshots-style handling of the phone's screenshots, all on the device: the pictures are read from the media store, their text
 * is read with ML Kit, and the assistant (when installed) or a set of plain rules says what each is, what to call it, and whether it was
 * only useful once (a payment confirmation, a one-time code, a delivery status). Nothing is deleted without the system's own confirmation.
 */
object Shots {
    /** Everything known about the screenshots, by media id. Observable, so screens follow it. */
    val infos = mutableStateMapOf<Long, ShotInfo>()
    private var loaded = false
    private fun file(ctx: Context) = File(ctx.filesDir, "b72/shots.json")

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val o = runCatching { AppJson.parseToJsonElement(file(ctx).readText()) as? JsonObject }.getOrNull() ?: return
        for ((k, e) in o) {
            val i = e as? JsonObject ?: continue
            val id = k.toLongOrNull() ?: continue
            fun s(n: String) = (i[n] as? JsonPrimitive)?.contentOrNull.orEmpty()
            infos[id] = ShotInfo(
                s("t"), s("s"), s("c"), (i["o"] as? JsonPrimitive)?.booleanOrNull == true,
                (i["g"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }, s("x"), s("st").ifEmpty { "new" }, (i["a"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }
    }

    fun save(ctx: Context) {
        val o = buildJsonObject {
            for ((id, i) in infos.toMap()) put(id.toString(), buildJsonObject {
                put("t", i.title); put("s", i.summary); put("c", i.category); put("o", i.oneTime); put("x", i.text.take(1500)); put("st", i.state); put("a", i.ai)
                put("g", buildJsonArray { i.tags.forEach { add(JsonPrimitive(it)) } })
            })
        }
        runCatching { file(ctx).also { it.parentFile?.mkdirs() }.writeText(o.toString()) }
    }

    fun setState(ctx: Context, id: Long, state: String) {
        val i = infos[id] ?: ShotInfo("Screenshot", "", "other", false, emptyList(), "")
        infos[id] = ShotInfo(i.title, i.summary, i.category, i.oneTime, i.tags, i.text, state, i.ai)
        save(ctx)
    }

    /** The newest screenshots in the media store (those in a folder called Screenshots), up to [limit]. */
    suspend fun scan(ctx: Context, limit: Int = 400): List<Shot> = withContext(Dispatchers.IO) {
        val out = ArrayList<Shot>()
        val sel = if (Build.VERSION.SDK_INT >= 29) "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" else "${MediaStore.Images.Media.DATA} LIKE ?"
        val arg = if (Build.VERSION.SDK_INT >= 29) "%Screenshots%" else "%/Screenshots/%"
        runCatching {
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT),
                sel, arrayOf(arg), "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(0)
                    out += Shot(id, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), c.getLong(1) * 1000, c.getInt(2), c.getInt(3))
                }
            }
        }
        out
    }

    private val ONE_TIME = setOf("payment", "receipt", "otp", "delivery", "ride")

    private fun count(t: String, words: List<String>) = words.count { t.contains(it) }

    /** Plain rules, used when the assistant is not installed and as its fallback: a category, whether it was only useful once, and a few tags. */
    fun guess(text: String): Triple<String, Boolean, List<String>> {
        val t = text.lowercase()
        val cat = when {
            count(t, listOf("otp", "verification code", "one-time password", "one time password", "do not share this code")) >= 1 -> "otp"
            count(t, listOf("payment successful", "paid to", "paid successfully", "transaction id", "txn", "upi", "debited", "credited", "payment of", "sent to", "transfer successful", "utr")) >= 2 -> "payment"
            count(t, listOf("invoice", "receipt", "order total", "subtotal", "gst", "bill amount", "order id", "order confirmed", "order placed", "thank you for your order")) >= 2 -> "receipt"
            count(t, listOf("out for delivery", "arriving", "track order", "track your order", "delivery partner", "estimated delivery", "your ride", "driver is", "pickup", "eta")) >= 2 ->
                if (count(t, listOf("ride", "driver", "pickup")) >= 1) "ride" else "delivery"
            count(t, listOf("boarding pass", "pnr", "seat", "gate", "departure", "booking id", "e-ticket", "ticket")) >= 2 -> "ticket"
            count(t, listOf("ingredients", "tablespoon", "preheat", "serves", "recipe")) >= 2 -> "recipe"
            count(t, listOf("followers", "likes", "retweet", "reply", "comments", "share")) >= 3 -> "social"
            count(t, listOf("fun ", "function", "import ", "error", "exception", "return ", "class ")) >= 3 -> "code"
            count(t, listOf("add to cart", "buy now", "mrp", "free delivery", "in stock", "reviews", "price")) >= 2 -> "product"
            Regex("""\b\d{1,2}:\d{2}\s?(am|pm)?\b""").findAll(t).count() >= 3 -> "chat"
            t.length < 12 -> "image"
            else -> "other"
        }
        val tags = buildList {
            add(cat)
            Regex("""[a-z]{5,}""").findAll(t).map { it.value }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
                .filter { it !in setOf("which", "their", "there", "these", "would", "about", "other", "after", "where") }.take(3).forEach { add(it) }
        }.distinct()
        return Triple(cat, cat in ONE_TIME, tags)
    }

    private const val PROMPT = """Here is the text read from a phone screenshot. Say what the screenshot is. Reply with ONLY a JSON object, nothing else:
{"title": 2 to 6 words naming it, "summary": one plain sentence of at most 20 words saying what it holds, "category": one of payment, receipt, otp, delivery, ride, ticket, chat, map, article, recipe, product, social, code, settings, other, "one_time": true only when it stops being useful soon (a payment confirmation, a one-time code, a delivery or ride status, a temporary offer) and false for anything worth looking up later, "tags": up to 4 short lowercase topic words}
Use only the text. Do not invent details.
Text:
"""

    /** Reads one screenshot: its text, then what it is. [session] is the assistant, when there is one; the rules answer otherwise. */
    suspend fun analyse(ctx: Context, shot: Shot, session: Assistant.Session?): ShotInfo {
        val text = Ocr.readUri(ctx, shot.uri).orEmpty().trim()
        val (gc, go, gt) = guess(text)
        val title0 = text.lineSequence().map { it.trim() }.firstOrNull { it.count { c -> c.isLetter() } >= 4 }?.take(40) ?: "Screenshot"
        if (session != null && text.length >= 12) {
            val reply = withContext(Dispatchers.Default) { session.ask(PROMPT + text.take(1200)) }
            val a = reply?.indexOf('{') ?: -1; val z = reply?.lastIndexOf('}') ?: -1
            if (reply != null && a >= 0 && z > a) {
                val o = runCatching { AppJson.parseToJsonElement(reply.substring(a, z + 1)) as? JsonObject }.getOrNull()
                if (o != null) {
                    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                    val cat = s("category").lowercase().ifEmpty { gc }
                    val one = (o["one_time"] as? JsonPrimitive)?.booleanOrNull ?: go
                    val tags = (o["tags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase()?.trim('#', ' ') }.filter { it.length in 2..24 }.take(4)
                    val title = s("title").trim('"', ' ', '.').take(48).ifEmpty { title0 }
                    return ShotInfo(title, s("summary").take(160), cat, one, tags.ifEmpty { gt }, text, "new", true)
                }
            }
        }
        val summary = when (gc) {
            "otp" -> "A one-time code."; "payment" -> "A payment confirmation."; "receipt" -> "A receipt or order summary."
            "delivery" -> "A delivery status."; "ride" -> "A ride status."; "ticket" -> "A ticket or booking."
            else -> text.replace('\n', ' ').take(100)
        }
        return ShotInfo(title0, summary, gc, go, gt, text, "new", false)
    }

    /** The system's own "delete these?" prompt for [shots] (Android 11 and later); null where the phone has no such prompt. */
    fun deleteRequest(ctx: Context, shots: List<Shot>): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30 && shots.isNotEmpty()) MediaStore.createDeleteRequest(ctx.contentResolver, shots.map { it.uri }).intentSender else null

    /** Where there is no system prompt, delete directly; returns how many went. */
    fun deleteDirect(ctx: Context, shots: List<Shot>): Int = shots.count { runCatching { ctx.contentResolver.delete(it.uri, null, null) > 0 }.getOrDefault(false) }

    fun forget(ctx: Context, ids: Collection<Long>) { ids.forEach { infos.remove(it) }; save(ctx) }
}
