package com.b72.keep_canvas.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Same order as the Flutter app's enum: kinds were stored by index, so only ever append. */
enum class Kind { Photo, Text, Checklist, Bare, Quote, Stat, Info, Link, Poster, Film, Track, Recipe, Book }

/** On-disk format is identical to the Flutter app's notes.json so the two stay interchangeable. */
@Serializable
data class Block(
    @SerialName("i") val id: String = "",
    @SerialName("k") val k: Int = 0,
    @SerialName("t") val title: String = "",
    @SerialName("b") val body: String = "",
    @SerialName("p") val path: String? = null,
    @SerialName("f") val focal: List<Double> = listOf(.3, .3, .7, .7),
    /** height / width */
    @SerialName("a") val aspect: Double = 1.0,
    @SerialName("d") val data: Map<String, JsonElement> = emptyMap(),
    val items: List<String> = emptyList(),
) {
    val kind: Kind get() = Kind.entries.getOrElse(k) { Kind.Text }

    /** Checklist rows: older notes kept them in `body` (one per line), newer ones in `items`. */
    val checkItems: List<String> get() = items.ifEmpty { body.lines().filter { it.isNotBlank() } }
}

fun newBlock(kind: Kind, id: String, title: String = "", body: String = "", path: String? = null, aspect: Double = 1.0, items: List<String> = emptyList()) =
    Block(id = id, k = kind.ordinal, title = title, body = body, path = path, aspect = aspect, items = items)

@Serializable
data class Note(
    val id: String,
    val blocks: List<Block> = emptyList(),
    val tags: List<String> = emptyList(),
    val places: Map<String, List<Double>> = emptyMap(),
    /** When to be reminded about this note (epoch millis); 0 = no reminder. */
    @SerialName("r") val remind: Long = 0,
    /** When the words, photos or tags were last changed by the person (epoch millis); 0 = never since it was made. */
    @SerialName("m") val edited: Long = 0,
    /** Starred notes can be pinned to the home screen widget and filtered to. */
    @SerialName("st") val starred: Boolean = false,
) {
    /**
     * The title the person gave the note; failing that, the one the assistant suggested (when it is installed).
     * Nothing is ever promoted out of the body: a note with no title simply has none.
     */
    val title: String
        get() = blocks.firstOrNull { it.kind != Kind.Checklist && it.title.isNotBlank() }?.title
            ?: blocks.firstNotNullOfOrNull { b -> b.str("aiTitle")?.takeIf { it.isNotBlank() } }
            ?: ""

    /** A line of the note's own words, for places that need something to show when there is no title. */
    fun snippet(max: Int = 70): String {
        val t = blocks.firstOrNull { it.kind == Kind.Text && it.body.isNotBlank() }?.body
            ?: blocks.firstOrNull { it.kind == Kind.Poster && it.body.isNotBlank() }?.body
            ?: blocks.firstOrNull { it.kind == Kind.Quote && it.body.isNotBlank() }?.body
            ?: blocks.firstOrNull { it.kind == Kind.Checklist }?.checkItems?.take(3)?.joinToString(", ") { it.removePrefix("x ") }
            ?: ""
        val line = t.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (line.length > max) line.take(max).trimEnd() + "…" else line
    }

    val photos: List<Block> get() = blocks.filter { it.kind == Kind.Photo && it.path != null }

    /** The block whose picture represents the note: first photo, else a record's poster/cover. */
    val cover: Block? get() = photos.firstOrNull() ?: blocks.firstOrNull { it.path != null }
}

@Serializable
data class Library(
    val notes: List<Note> = emptyList(),
    /** Stickers are carried through untouched until the canvas is ported. */
    val stickers: List<JsonElement> = emptyList(),
    /** Marks and stickers a person put on a canvas by hand, per tag. */
    val placed: Map<String, List<Stuck>> = emptyMap(),
)

/** One hand-placed item on a canvas. [k] is "m:<Mark>" for a drawn mark or "s:<path>" for a sticker; x, y and w are fractions of the canvas width. */
@Serializable
data class Stuck(val id: String, val k: String, val x: Float, val y: Float, val w: Float, val r: Float = 0f, val c: Int = 0)

// ---- helpers for the loose `data` map the Flutter app used (cut-outs, ratings, record fields) ----
fun Block.str(key: String): String? = (data[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
fun Block.dbl(key: String): Double? = (data[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
fun Block.nums(key: String): List<Double>? =
    (data[key] as? kotlinx.serialization.json.JsonArray)?.map { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0 }

/** Path of the transparent subject cut-out, if the Flutter build made one and the file is still there. */
fun Block.cutPath(): String? = str("cut")?.takeIf { it.isNotEmpty() && java.io.File(it).exists() }
fun Block.cutFrac(): Double = if (cutPath() == null) 0.0 else (dbl("cutFrac") ?: .3)

/** Stable across runs and identical to the Flutter app's hash, so each note keeps its look. */
fun seedOf(s: String): Int {
    var h = 7L
    for (c in s) h = (h * 31 + c.code) and 0x7fffffffL
    return h.toInt()
}

/** Kinds that hold structured data and can be filled from the web. */
val RECORD_KINDS = setOf(Kind.Film, Kind.Track, Kind.Recipe, Kind.Link, Kind.Book)

/** Canvas/tile height / width: music is always square, films and books are 2:3 portraits. */
fun recAspect(k: Kind) = when (k) { Kind.Film, Kind.Book -> 1.5; Kind.Track -> 1.0; Kind.Recipe -> 1.15; else -> .85 }

/** Data keys that belong to the image, not to the record's text fields. */
val CUT_KEYS = setOf(
    "cut", "cutV", "cutBox", "cutAspect", "cutFrac", "cutOk", "cutWhy", "pop",
    // what the readers and the assistant worked out; never shown as a field
    "trip", "tripH", "aiH", "aiSum", "aiTags", "aiFacts", "aiTitle",
)

/** What counts as a change by the person: not canvas positions, reminders, or the background readers' results. */
fun Note.contentSig(): List<Any?> = blocks.map { listOf(it.k, it.title, it.body, it.path, it.items) } + listOf(tags)

fun Block.field(key: String): String = when (val v = data[key]) {
    is kotlinx.serialization.json.JsonPrimitive -> v.content.trim()
    else -> ""
}

/** The one-line subtitle under a record's title. */
fun Block.recSub(): String {
    val parts = when (kind) {
        Kind.Film -> listOf(field("year"), field("director"))
        Kind.Track -> listOf(field("artist"), field("album"))
        Kind.Book -> listOf(field("author"), field("year"))
        Kind.Recipe -> listOfNotNull(field("time").takeIf { it.isNotEmpty() && it != "0" }?.let { "$it min" }, field("servings").takeIf { it.isNotEmpty() }?.let { "serves $it" })
        else -> listOf(field("site"))
    }
    return parts.filter { it.isNotEmpty() }.joinToString(" · ")
}
