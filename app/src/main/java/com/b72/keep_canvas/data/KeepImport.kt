package com.b72.keep_canvas.data

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Brings notes over from Google Keep. Export them with Google Takeout (Keep only), then pick the zip here: text notes, lists with
 * their ticks, labels (as tags), pinned (as starred), pictures, and the original dates. Trashed notes are left behind. A note
 * keeps its Keep creation time as its id, so importing the same file twice adds nothing twice.
 */
object KeepImport {
    private val IMG = setOf("jpg", "jpeg", "png", "webp", "gif", "heic")

    fun read(ctx: Context, input: InputStream): Library? {
        val dir = imageDir(ctx)
        val jsons = ArrayList<JsonObject>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name.substringAfterLast('/')
                val ext = name.substringAfterLast('.', "").lowercase()
                when {
                    ext == "json" && "keep" in e.name.lowercase() -> runCatching { AppJson.parseToJsonElement(zip.readBytes().decodeToString()).jsonObject }.getOrNull()?.let { jsons.add(it) }
                    ext in IMG && "keep" in e.name.lowercase() -> {
                        val target = File(dir, "keep_$name")
                        if (!target.exists()) target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val notes = jsons.mapNotNull { j -> runCatching { toNote(j, dir) }.getOrNull() }
        return if (notes.isEmpty()) null else Library(notes = notes)
    }

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun toNote(o: JsonObject, dir: File): Note? {
        if ((o["isTrashed"] as? JsonPrimitive)?.booleanOrNull == true) return null
        val created = (o["createdTimestampUsec"] as? JsonPrimitive)?.longOrNull ?: return null
        val edited = ((o["userEditedTimestampUsec"] as? JsonPrimitive)?.longOrNull ?: created) / 1000
        val id = created.toString()
        val title = str(o, "title").trim()
        val text = str(o, "textContent").trim()
        val list = (o["listContent"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val blocks = ArrayList<Block>()
        if (list.isNotEmpty()) {
            if (title.isNotEmpty()) blocks.add(newBlock(Kind.Bare, "t$id", title = title))
            blocks.add(newBlock(Kind.Checklist, "in_$id", title = "List", items = list.map { r ->
                val t = str(r, "text").trim()
                if ((r["isChecked"] as? JsonPrimitive)?.booleanOrNull == true) "x $t" else t
            }.filter { it.isNotBlank() && it != "x " }))
        } else if (title.isNotEmpty() || text.isNotEmpty()) {
            blocks.add(newBlock(Kind.Text, "in_$id", title = title, body = text))
        }
        for ((i, a) in (o["attachments"] as? JsonArray).orEmpty().withIndex()) {
            val f = File(dir, "keep_" + str(a as? JsonObject ?: continue, "filePath").substringAfterLast('/'))
            if (!f.exists()) continue
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, opts)
            val aspect = if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth.toDouble() / opts.outHeight else 1.0
            blocks.add(newBlock(Kind.Photo, "p$id$i", path = f.absolutePath, aspect = aspect))
        }
        if (blocks.isEmpty()) return null
        val tags = (o["labels"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { l -> str(l, "name").trim().takeIf { n -> n.isNotEmpty() } } }
        return Note(id = id, blocks = blocks, tags = tags, edited = edited, starred = (o["isPinned"] as? JsonPrimitive)?.booleanOrNull == true)
    }
}
