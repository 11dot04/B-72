package com.b72.keep_canvas.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
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
            return@withContext runCatching { Loaded(parse(mine.readText()), Source.Own) }
                .getOrElse { Loaded(Library(), Source.Own, "Could not read notes: ${it.message}") }
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

    suspend fun save(ctx: Context, lib: Library) = withContext(Dispatchers.IO) {
        val f = own(ctx)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "notes.json.tmp")
        tmp.writeText(AppJson.encodeToString(Library.serializer(), lib))
        tmp.renameTo(f)
    }

    /** The Flutter file is either a bare list of notes (very old) or {notes, stickers}. */
    private fun parse(text: String): Library {
        val root = AppJson.parseToJsonElement(text)
        return when (root) {
            is JsonArray -> Library(notes = root.map { AppJson.decodeFromJsonElement<Note>(it) })
            is JsonObject -> Library(
                notes = root["notes"]?.jsonArray?.map { AppJson.decodeFromJsonElement<Note>(it) } ?: emptyList(),
                stickers = root["stickers"]?.jsonArray?.toList() ?: emptyList(),
            )
            else -> Library()
        }
    }
}
