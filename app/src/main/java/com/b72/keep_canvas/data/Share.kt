package com.b72.keep_canvas.data

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** What another app sent us through the share sheet. */
class Share(val title: String, val text: String, val uris: List<Uri>, val token: Long = System.nanoTime())

fun Intent.toShare(): Share? {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
    val text = getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
    val title = (getStringExtra(Intent.EXTRA_SUBJECT) ?: getStringExtra(Intent.EXTRA_TITLE)).orEmpty().trim()
    val uris = buildList {
        if (action == Intent.ACTION_SEND) IntentCompat.getParcelableExtra(this@toShare, Intent.EXTRA_STREAM, Uri::class.java)?.let { add(it) }
        else IntentCompat.getParcelableArrayListExtra(this@toShare, Intent.EXTRA_STREAM, Uri::class.java)?.let { addAll(it) }
    }
    if (text.isEmpty() && uris.isEmpty()) return null
    return Share(title, text, uris)
}
