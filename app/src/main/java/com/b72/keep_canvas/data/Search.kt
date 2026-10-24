package com.b72.keep_canvas.data

import kotlinx.serialization.json.JsonPrimitive

/** Everything a note says, lower-cased: titles, text, list items, tags, record fields and the words read from its photos. */
fun Note.searchText(): String = buildString {
    tags.forEach { append(it).append(' ') }
    for (b in blocks) {
        append(b.title).append(' ').append(b.body).append(' ')
        b.items.forEach { append(it).append(' ') }
        for ((k, v) in b.data) {
            if (k in CUT_KEYS) continue
            (v as? JsonPrimitive)?.takeIf { it.isString }?.let { append(it.content).append(' ') }
        }
    }
}.lowercase()

/** Every word of the query has to appear somewhere in the note. */
fun matchesQuery(text: String, q: String): Boolean = q.lowercase().split(' ').filter { it.isNotBlank() }.all { it in text }
