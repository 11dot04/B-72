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

/** One thing worth doing with what a photo shows. [kind]: shop, style, recipe, care, review, similar (open a search), tag (add a tag) or ai (the local model's own). */
class Idea(val id: String, val blockId: String, val kind: String, val title: String, val detail: String, val url: String, val tag: String = "")

private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
private fun shop(q: String) = "https://www.google.com/search?tbm=shop&q=${enc(q)}"
private fun web(q: String) = "https://www.google.com/search?q=${enc(q)}"
private fun pins(q: String) = "https://www.pinterest.com/search/pins/?q=${enc(q)}"

private val HOME = listOf("furniture", "chair", "table", "lamp", "couch", "sofa", "shelf", "bookcase", "desk", "bed", "lighting", "interior design", "curtain", "rug", "vase", "light fixture", "lampshade", "chandelier", "mirror", "armchair", "cabinetry", "stool", "bench", "home decor")
private val WEAR = listOf("dress", "clothing", "shirt", "t-shirt", "jeans", "jacket", "coat", "footwear", "shoe", "sneaker", "boot", "handbag", "bag", "sunglasses", "hat", "scarf", "fashion", "skirt", "trousers", "suit", "outerwear", "watch", "jewellery", "jewelry", "necklace", "earring", "sweater", "hoodie", "denim")
private val FOOD = listOf("food", "dish", "cuisine", "meal", "dessert", "cake", "pizza", "salad", "fruit", "vegetable", "drink", "coffee", "baking", "sandwich", "soup", "burger", "bread", "ingredient", "pasta", "cookie", "curry", "breakfast")
private val PLANT = listOf("plant", "flower", "houseplant", "tree", "succulent", "leaf", "garden", "flowerpot", "petal", "herb")
private val GADGET = listOf("electronics", "gadget", "laptop", "phone", "camera", "headphones", "speaker", "keyboard", "computer", "monitor", "tablet", "smartwatch", "console")
private val ART = listOf("poster", "art", "illustration", "graphic design", "painting", "typography", "drawing", "pattern", "visual arts")

private fun pick(names: List<Pair<String, Float>>, keys: List<String>): String? =
    names.firstOrNull { (n, _) -> keys.any { n.lowercase() == it || n.lowercase().contains(it) } }?.first

/** The suggestions a photo's labels point to; none at all when [Guard] says the photo should be left alone. */
fun Block.photoIdeas(): List<Idea> {
    if (kind != Kind.Photo || !Guard.ok(this)) return emptyList()
    val ls = labels().filter { it.second >= .6f }
    if (ls.isEmpty()) return emptyList()
    val out = ArrayList<Idea>()
    fun add(kind: String, key: String, title: String, detail: String, url: String, tag: String = "") = out.add(Idea("$kind:$key", id, kind, title, detail, url, tag))
    pick(ls, HOME)?.let { s -> val l = s.lowercase()
        add("shop", l, "Find a similar $l", "Search shops for something like this.", shop(l))
        add("style", l, "Rooms with a $l", "Styling ideas to see it in context.", pins("$l interior ideas")) }
    pick(ls, WEAR)?.let { s -> val l = s.lowercase()
        add("style", l, "Outfits with a $l", "Ways to put it together.", pins("outfit ideas with $l"))
        add("shop", l, "Shop a similar $l", "Search shops for something like this.", shop(l)) }
    pick(ls, FOOD)?.let { s -> val l = s.lowercase()
        add("recipe", l, "Find a $l recipe", "How to make something like this.", web("$l recipe"))
        add("tag", "food", "Tag it #food", "File this with your food notes.", "", "food") }
    pick(ls, PLANT)?.let { s -> val l = s.lowercase()
        add("care", l, "Care guide for a $l", "Light, water and soil, in one search.", web("$l plant care guide"))
        add("tag", "plants", "Tag it #plants", "File this with your plants.", "", "plants") }
    pick(ls, GADGET)?.let { s -> val l = s.lowercase()
        add("review", l, "Reviews and prices", "What people say about a $l like this.", web("best $l review")) }
    pick(ls, ART)?.let { s -> val l = s.lowercase()
        add("similar", l, "More like this", "Find work in the same spirit.", pins("$l inspiration"))
        add("tag", "inspiration", "Tag it #inspiration", "Keep it with your references.", "", "inspiration") }
    return out.take(3)
}

/** The local model's own suggestions for a photo, as stored by the assistant. */
fun Block.aiIdeas(): List<Idea> = (data["aiIdeas"] as? JsonArray).orEmpty().mapIndexedNotNull { i, e ->
    val o = e as? JsonObject ?: return@mapIndexedNotNull null
    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val t = s("t"); val q = s("q")
    if (t.isEmpty() || q.isEmpty()) null else Idea("ai:$i:${t.hashCode()}", id, "ai", t, s("d"), web(q))
}

fun ideasToData(list: List<Triple<String, String, String>>): JsonArray = JsonArray(list.map { (t, d, q) -> buildJsonObject { put("t", t); put("d", d); put("q", q) } })

/** Everything suggested for a note, minus what the person dismissed. */
fun Note.ideas(): List<Idea> {
    val off = blocks.flatMap { it.field("ideaOff").split('|') }.toSet()
    return blocks.filter { it.kind == Kind.Photo && Guard.ok(it) }.flatMap { it.photoIdeas() + it.aiIdeas() }
        .filter { it.id !in off && !(it.kind == "tag" && it.tag in tags) }.distinctBy { it.id }.take(5)
}

/** Photos whose labels are known and which still need the local model's ideas. */
fun ideasStale(b: Block): Boolean = Assistant.installed && b.kind == Kind.Photo && b.path != null && "lbl" in b.data && b.dbl("ideasV") != 1.0 && Guard.ok(b) && b.labels().isNotEmpty()
