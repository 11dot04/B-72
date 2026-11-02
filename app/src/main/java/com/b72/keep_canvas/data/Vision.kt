package com.b72.keep_canvas.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File

/** What is in a photo, worked out on the phone (ML Kit's bundled labeller). Nothing is uploaded. */
object Vision {
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(.55f).build()) }

    /** "Lamp:0.92,Furniture:0.80"; "" when nothing was recognised; null when the photo could not be read (tried again next launch). */
    suspend fun labels(ctx: Context, path: String): String? {
        if (!File(path).exists()) return null
        return runCatching {
            labeler.process(InputImage.fromFilePath(ctx, Uri.fromFile(File(path)))).await()
                .sortedByDescending { it.confidence }.take(10).joinToString(",") { "${it.text.replace(',', ' ').replace(':', ' ')}:${"%.2f".format(java.util.Locale.US, it.confidence)}" }
        }.getOrNull()
    }
}

fun Block.labels(): List<Pair<String, Float>> = field("lbl").split(',').mapNotNull { p ->
    val i = p.lastIndexOf(':'); if (i <= 0) null else p.substring(0, i).trim() to (p.substring(i + 1).toFloatOrNull() ?: return@mapNotNull null)
}

/**
 * The limits on what the assistant will suggest from a photo. People, faces, bodies, children, documents, tickets and
 * anything that looks like ID or a screenshot of text never produce suggestions; those photos are simply left alone.
 */
object Guard {
    private val PEOPLE = listOf(
        "face", "selfie", "smile", "skin", "child", "toddler", "baby", "infant", "nose", "eyebrow", "chin", "forehead", "cheek", "lip", "eyelash",
        "beard", "moustache", "muscle", "abdomen", "chest", "thigh", "undergarment", "swimwear", "bikini", "lingerie", "nude", "tattoo", "crowd", "wedding",
    )
    private val PAPER = listOf("document", "paper", "text", "font", "screenshot", "receipt", "ticket", "passport", "handwriting", "menu", "number", "website", "web page", "identity", "card", "invoice", "form")

    fun ok(b: Block): Boolean {
        val names = b.labels().map { it.first.lowercase() }
        if (names.any { n -> PEOPLE.any { n.contains(it) } }) return false
        if (names.any { n -> PAPER.any { n == it || n.contains(it) } }) return false
        if (b.field("ocr").length > 240) return false
        return true
    }
}

/** One thing worth doing with what a photo shows. [kind]: shop, recipe, care (open a search), tag (add a tag), tip (the assistant's own answer, no search) or ai (the assistant's, with a search to open). */
class Idea(val id: String, val blockId: String, val kind: String, val title: String, val detail: String, val url: String, val tag: String = "", val body: String = "")

private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
private fun shop(q: String) = "https://www.google.com/search?tbm=shop&q=${enc(q)}"
private fun web(q: String) = "https://www.google.com/search?q=${enc(q)}"

private val HOME = listOf("furniture", "chair", "table", "lamp", "couch", "sofa", "shelf", "bookcase", "desk", "bed", "lighting", "curtain", "rug", "vase", "chandelier", "mirror", "armchair", "cabinet", "stool", "bench", "decor", "rack", "stand")
private val WEAR = listOf("dress", "clothing", "shirt", "jeans", "jacket", "coat", "footwear", "shoe", "sneaker", "boot", "handbag", "bag", "sunglasses", "hat", "scarf", "skirt", "trousers", "suit", "watch", "jewellery", "jewelry", "necklace", "earring", "sweater", "hoodie", "denim")
private val FOOD = listOf("food", "dish", "cuisine", "meal", "dessert", "cake", "pizza", "salad", "fruit", "vegetable", "drink", "coffee", "baking", "sandwich", "soup", "burger", "bread", "pasta", "cookie", "curry", "breakfast")
private val PLANT = listOf("plant", "flower", "houseplant", "succulent", "flowerpot", "petal", "herb")
private val GADGET = listOf("electronics", "gadget", "laptop", "phone", "camera", "headphones", "speaker", "keyboard", "computer", "monitor", "tablet", "smartwatch", "console")

private fun has(words: List<String>, keys: List<String>) = words.any { w -> keys.any { w == it || w.startsWith(it) } }

/** The name the person gave the thing: the note's title, else the first short line of text read from the photo. */
fun subjectOf(noteTitle: String, ocr: String): String {
    if (noteTitle.isNotBlank()) return noteTitle.trim().take(60)
    val line = ocr.lines().map { it.trim() }.firstOrNull { l -> l.count { it.isLetter() } >= 4 && l.split(' ').size in 1..6 } ?: return ""
    return line.take(60)
}

/**
 * Search suggestions for something that can only be answered on the web (where to buy it, what it costs, how to cook it when no assistant is
 * installed). They name the thing the way the person did: the title or the words on the photo come first, the photo's labels only when there is neither.
 */
fun Block.photoIdeas(subject: String): List<Idea> {
    if (kind != Kind.Photo || !Guard.ok(this)) return emptyList()
    val ls = labels().filter { it.second >= .75f }
    val words = (if (subject.isNotBlank()) subject.lowercase().split(Regex("[^a-z]+")) else ls.flatMap { it.first.lowercase().split(' ') }).filter { it.length > 2 }
    if (words.isEmpty()) return emptyList()
    val name = subject.ifBlank { ls.firstOrNull { (n, _) -> has(listOf(n.lowercase()), HOME + WEAR + FOOD + PLANT + GADGET) }?.first.orEmpty() }.trim()
    if (name.isBlank()) return emptyList()
    val out = ArrayList<Idea>()
    fun add(kind: String, key: String, title: String, detail: String, url: String, tag: String = "") = out.add(Idea("$kind:$key", id, kind, title, detail, url, tag))
    val key = name.lowercase()
    if (has(words, HOME) || has(words, WEAR) || has(words, GADGET)) add("shop", key, "Shop for ${name.take(34)}", "Opens a shopping search for exactly this.", shop(name))
    // the assistant answers the rest itself; without it these two still need the web
    if (!Assistant.installed) {
        if (has(words, FOOD)) { add("recipe", key, "Find a recipe", "How to make ${name.take(34)}.", web("$name recipe")); add("tag", "food", "Tag it #food", "File this with your food notes.", "", "food") }
        if (has(words, PLANT)) { add("care", key, "Care guide", "Light, water and soil for ${name.take(30)}.", web("$name plant care guide")); add("tag", "plants", "Tag it #plants", "File this with your plants.", "", "plants") }
    } else {
        if (has(words, FOOD)) add("tag", "food", "Tag it #food", "File this with your food notes.", "", "food")
        if (has(words, PLANT)) add("tag", "plants", "Tag it #plants", "File this with your plants.", "", "plants")
    }
    return out.take(3)
}

/** The local model's own ideas for a photo, as stored by the assistant: an answer to read, and sometimes a search to open. */
fun Block.aiIdeas(): List<Idea> = (data["aiIdeas"] as? JsonArray).orEmpty().mapIndexedNotNull { i, e ->
    val o = e as? JsonObject ?: return@mapIndexedNotNull null
    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val t = s("t"); val q = s("q"); val d = s("d")
    if (t.isEmpty() || (q.isEmpty() && d.isEmpty())) null
    else Idea("ai:$i:${t.hashCode()}", id, if (q.isEmpty()) "tip" else "ai", t, "", if (q.isEmpty()) "" else if (Regex("""\b(buy|price|shop|cheap|deal|where to)\b""", RegexOption.IGNORE_CASE).containsMatchIn(q)) shop(q) else web(q), body = d)
}

fun ideasToData(list: List<Triple<String, String, String>>): JsonArray = JsonArray(list.map { (t, d, q) -> buildJsonObject { put("t", t); put("d", d); put("q", q) } })

/** Everything suggested for a note, minus what the person dismissed. */
fun Note.ideas(): List<Idea> {
    val off = blocks.flatMap { it.field("ideaOff").split('|') }.toSet()
    return blocks.filter { it.kind == Kind.Photo && Guard.ok(it) }.flatMap { b -> b.aiIdeas() + b.photoIdeas(subjectOf(title, b.field("ocr"))) }
        .filter { it.id !in off && !(it.kind == "tag" && it.tag in tags) }.distinctBy { it.id }.take(5)
}

/** Photos whose labels are known and which still need the local model's ideas. */
fun ideasStale(b: Block): Boolean = Assistant.installed && b.kind == Kind.Photo && b.path != null && "lbl" in b.data && b.dbl("ideasV") != IDEAS_V && Guard.ok(b)

const val IDEAS_V = 2.0
