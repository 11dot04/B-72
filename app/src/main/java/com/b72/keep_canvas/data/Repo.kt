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
