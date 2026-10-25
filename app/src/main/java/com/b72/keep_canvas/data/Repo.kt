package com.b72.keep_canvas.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The notes the UI shows. Every change is written straight back to files/b72/notes.json. */
class Repo(private val ctx: Context, private val scope: CoroutineScope) {
    var loaded by mutableStateOf<Loaded?>(null)
        private set

    suspend fun init() { loaded = Store.load(ctx) }

    fun note(id: String): Note? = loaded?.library?.notes?.firstOrNull { it.id == id }

    fun upsert(n: Note) {
        val l = loaded?.library ?: return
        val notes = if (l.notes.any { it.id == n.id }) l.notes.map { if (it.id == n.id) n else it } else l.notes + n
        commit(l.copy(notes = notes))
    }

    /**
     * Background work on pictures, once each: the words in a photo (for search) and the subject's cut-out (for the pop-out
     * and for canvases that overlap cards). Each result is stored with its block, one note write per picture.
     */
    suspend fun prepare() {
        val todo = loaded?.library?.notes.orEmpty().flatMap { n -> n.blocks.filter { needsWork(it) }.map { n.id to it.id } }
        for ((nid, bid) in todo) {
            val b = note(nid)?.blocks?.firstOrNull { it.id == bid } ?: continue
            val path = b.path ?: continue
            var d = b.data
            if (b.kind == Kind.Photo && "ocr" !in d) Ocr.read(ctx, path)?.let { d = d + ("ocr" to JsonPrimitive(it)) }
            if (b.kind in CUT_KINDS && "cutV" !in d) {
                makeCut(path, imageDir(ctx)).onSuccess { c ->
                    d = if (c == null) d + mapOf("cut" to JsonPrimitive(""), "cutV" to JsonPrimitive(2))
                    else d + mapOf(
                        "cut" to JsonPrimitive(c.path), "cutBox" to JsonArray(c.box.map { JsonPrimitive(it) }),
                        "cutAspect" to JsonPrimitive(c.aspect), "cutFrac" to JsonPrimitive(c.frac), "cutV" to JsonPrimitive(2),
                    )
                }
            }
            if (d == b.data) continue
            val n = note(nid) ?: continue
            upsert(n.copy(blocks = n.blocks.map { if (it.id == bid) it.copy(data = d) else it }))
        }
    }

    /** Stickers are kept in the library as {i, p} objects, newest first, exactly as the old app wrote them. */
    fun addStickers(paths: List<String>) {
        val l = loaded?.library ?: return
        val made = paths.map { kotlinx.serialization.json.buildJsonObject { put("i", JsonPrimitive(it.substringAfterLast('/'))); put("p", JsonPrimitive(it)) } }
        commit(l.copy(stickers = made + l.stickers))
    }

    fun removeSticker(path: String) {
        val l = loaded?.library ?: return
        commit(l.copy(stickers = l.stickers.filter { (it as? kotlinx.serialization.json.JsonObject)?.get("p")?.let { p -> (p as? JsonPrimitive)?.content } != path }))
        runCatching { File(path).delete() }
    }

    /** Crosses an item of a note's checklist off, or back on. Items are marked "x " as in the old app. */
    fun toggleItem(noteId: String, blockId: String, index: Int) {
        val n = note(noteId) ?: return
        fun flip(s: String) = if (s.startsWith("x ")) s.substring(2) else "x $s"
        upsert(n.copy(blocks = n.blocks.map { b ->
            when {
                b.id != blockId -> b
                b.items.isNotEmpty() -> b.copy(items = b.items.mapIndexed { i, s -> if (i == index) flip(s) else s })
                else -> b.copy(body = b.body.lines().filter { it.isNotBlank() }.mapIndexed { i, s -> if (i == index) flip(s) else s }.joinToString("\n"))
            }
        }))
    }

    /** Adds the notes of a restored backup that are not here yet; existing notes are never overwritten. Returns how many were added. */
    fun merge(lib: Library): Int {
        val cur = loaded?.library ?: return 0
        val have = cur.notes.map { it.id }.toSet()
        val fresh = lib.notes.filter { it.id !in have }
        if (fresh.isNotEmpty()) commit(cur.copy(notes = cur.notes + fresh))
        return fresh.size
    }

    /** Several notes in one write (the canvas stores every card it had to move at once). */
    fun upsertAll(ns: List<Note>) {
        val l = loaded?.library ?: return
        val by = ns.associateBy { it.id }
        commit(l.copy(notes = l.notes.map { by[it.id] ?: it }))
    }

    fun delete(id: String) {
        val l = loaded?.library ?: return
        commit(l.copy(notes = l.notes.filter { it.id != id }))
    }

    private fun commit(lib: Library) {
        loaded = Loaded(lib, Source.Own)
        scope.launch { Store.save(ctx, lib) }
    }

    /** Copies a picked photo into our own folder and measures it (height / width, clamped like the old cards). */
    suspend fun importPhoto(uri: Uri, id: String): Block? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.filesDir, "b72/photos").apply { mkdirs() }
            val f = File(dir, "${System.currentTimeMillis()}_${id.takeLast(4)}.jpg")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            val turned = runCatching { ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1) in setOf(5, 6, 7, 8)
            val w = (if (turned) o.outHeight else o.outWidth).coerceAtLeast(1)
            val h = (if (turned) o.outWidth else o.outHeight).coerceAtLeast(1)
            newBlock(Kind.Photo, id, path = f.path, aspect = (h.toDouble() / w).coerceIn(.6, 1.4))
        }.getOrNull()
    }
}

/** Where downloaded posters, covers and link images are kept. */
fun imageDir(ctx: Context) = File(ctx.filesDir, "b72/photos").apply { mkdirs() }

private val CUT_KINDS = setOf(Kind.Photo, Kind.Recipe)

/** True when a picture still has its text read or its subject cut. */
fun needsWork(b: Block) = b.path != null && ((b.kind == Kind.Photo && "ocr" !in b.data) || (b.kind in CUT_KINDS && "cutV" !in b.data))
