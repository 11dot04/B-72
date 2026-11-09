package com.b72.keep_canvas.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

enum class Source { Empty, Own, ImportedFromFlutter }

data class Loaded(val library: Library, val source: Source, val error: String? = null)

/**
 * Notes live in files/b72/notes.json. On first run, if the Flutter build left its notes.json in
 * app_flutter/ (same package, so the files are still ours), that file is imported once. The Flutter
 * files are never modified or deleted, so they remain a backup and the Flutter build can still run.
 */
object Store {
    private fun own(ctx: Context) = File(ctx.filesDir, "b72/notes.json")
    private fun flutter(ctx: Context) = File(ctx.filesDir.parentFile, "app_flutter/notes.json")

    suspend fun load(ctx: Context): Loaded = withContext(Dispatchers.IO) {
        val mine = own(ctx)
        if (mine.exists()) {
            val text = mine.readText()
            return@withContext runCatching { Loaded(parse(text), Source.Own) }
                .getOrElse { first ->
                    // Two saves at once used to be able to leave the tail of the longer one after the end of the shorter. Everything up to the end
                    // of the first complete document is intact, so that is read, the damaged file is kept beside it, and the clean copy is written back.
                    val head = firstValue(text)
                    val lib = if (head != null && head.length < text.length) runCatching { parse(head) }.getOrNull() else null
                    if (lib != null) {
                        runCatching { File(mine.parentFile, "notes.damaged.json").writeText(text) }
                        runCatching { save(ctx, lib) }
                        Loaded(lib, Source.Own)
                    } else Loaded(Library(), Source.Own, "Could not read notes: ${first.message}")
                }
        }
        val old = flutter(ctx)
        if (old.exists()) {
            return@withContext runCatching {
                val text = old.readText()
                val lib = parse(text)
                save(ctx, lib)
                Loaded(lib, Source.ImportedFromFlutter)
            }.getOrElse { Loaded(Library(), Source.Empty, "Could not import the old notes: ${it.message}") }
        }
        Loaded(Library(), Source.Empty)
    }

    /** One save at a time, each through its own temporary file, so two writers can never share a file and leave a torn one behind. */
    private val saving = kotlinx.coroutines.sync.Mutex()

    suspend fun save(ctx: Context, lib: Library) = withContext(Dispatchers.IO) {
        val f = own(ctx)
        f.parentFile?.mkdirs()
        saving.withLock {
            val tmp = File(f.parentFile, "notes.json.tmp")
            tmp.writeText(AppJson.encodeToString(Library.serializer(), lib))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    /** The text of the first complete top-level object or array in [t], or null if there is none. */
    private fun firstValue(t: String): String? {
        var depth = 0; var inStr = false; var esc = false; var start = -1
        for (i in t.indices) {
            val c = t[i]
            if (inStr) { if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false; continue }
            when (c) {
                '"' -> inStr = true
                '{', '[' -> { if (depth == 0) start = i; depth++ }
                '}', ']' -> { depth--; if (depth == 0 && start >= 0) return t.substring(start, i + 1) }
            }
        }
        return null
    }

    /** The Flutter file is either a bare list of notes (very old) or {notes, stickers}. */
    private fun parse(text: String): Library {
        val root = AppJson.parseToJsonElement(text)
        return when (root) {
            is JsonArray -> Library(notes = root.map { AppJson.decodeFromJsonElement<Note>(it) })
            is JsonObject -> runCatching { AppJson.decodeFromJsonElement(Library.serializer(), root) }.getOrNull() ?: Library(
                notes = root["notes"]?.jsonArray?.map { AppJson.decodeFromJsonElement<Note>(it) } ?: emptyList(),
                stickers = root["stickers"]?.jsonArray?.toList() ?: emptyList(),
            )
            else -> Library()
        }
    }
}
