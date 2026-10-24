package com.b72.keep_canvas.data

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

    /** Reads the text out of every photo that has not been read yet and stores it with the photo, one note write each. */
    suspend fun indexPhotos() {
        val todo = loaded?.library?.notes.orEmpty().flatMap { n -> n.blocks.filter { it.kind == Kind.Photo && it.path != null && "ocr" !in it.data }.map { n.id to it.id } }
        for ((nid, bid) in todo) {
            val b = note(nid)?.blocks?.firstOrNull { it.id == bid } ?: continue
            val text = Ocr.read(ctx, b.path!!) ?: continue
            val n = note(nid) ?: continue
            upsert(n.copy(blocks = n.blocks.map { if (it.id == bid) it.copy(data = it.data + ("ocr" to kotlinx.serialization.json.JsonPrimitive(text))) else it }))
        }
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
