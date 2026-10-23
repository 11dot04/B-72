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
}

@Serializable
data class Note(
    val id: String,
    val blocks: List<Block> = emptyList(),
    val tags: List<String> = emptyList(),
    val places: Map<String, List<Double>> = emptyMap(),
) {
    /** First block that has a title, else the first line of a text block. */
    val title: String
        get() = blocks.firstOrNull { it.kind != Kind.Checklist && it.title.isNotBlank() }?.title
            ?: blocks.firstOrNull { it.kind == Kind.Text && it.body.isNotBlank() }?.body?.lineSequence()?.first()?.take(80)
            ?: ""

    val photos: List<Block> get() = blocks.filter { it.kind == Kind.Photo && it.path != null }

    /** The block whose picture represents the note: first photo, else a record's poster/cover. */
    val cover: Block? get() = photos.firstOrNull() ?: blocks.firstOrNull { it.path != null }
}

@Serializable
data class Library(
    val notes: List<Note> = emptyList(),
    /** Stickers are carried through untouched until the canvas is ported. */
    val stickers: List<JsonElement> = emptyList(),
)
