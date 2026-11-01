package com.b72.keep_canvas.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

/** Lays recognised text out as a person would read it: blocks in order, wrapped lines rejoined into paragraphs, hyphens healed, specks dropped. */
internal fun tidy(t: com.google.mlkit.vision.text.Text): String {
    val blocks = t.textBlocks.sortedWith(compareBy({ (it.boundingBox?.top ?: 0) / 24 }, { it.boundingBox?.left ?: 0 }))
    return blocks.mapNotNull { b ->
        val lines = b.lines.filter { l -> l.confidence >= .45f && l.text.count { it.isLetterOrDigit() } >= 2 }
        if (lines.isEmpty()) return@mapNotNull null
        val widest = lines.maxOf { it.boundingBox?.width() ?: 0 }.coerceAtLeast(1)
        val sb = StringBuilder()
        for ((i, l) in lines.withIndex()) {
            val text = l.text.trim()
            sb.append(text)
            if (i == lines.lastIndex) break
            val full = (l.boundingBox?.width() ?: 0) >= widest * .8f
            when {
                text.endsWith("-") && full -> sb.setLength(sb.length - 1)
                full && !text.endsWith(".") && !text.endsWith(":") && !text.endsWith("!") && !text.endsWith("?") -> sb.append(' ')
                else -> sb.append('\n')
            }
        }
        sb.toString()
    }.joinToString("\n\n").trim()
}

/** Reads the text in a photo on the device (a receipt, a poster, a whiteboard) so search can find it. */
object Ocr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** The text found, "" when there is none, null when the photo could not be read (tried again next launch). */
    suspend fun read(ctx: Context, path: String): String? {
        if (!File(path).exists()) return null
        return runCatching { tidy(recognizer.process(InputImage.fromFilePath(ctx, Uri.fromFile(File(path)))).await()) }.getOrNull()
    }

    /** The same for a picture already in memory (a rendered PDF page). */
    suspend fun readBitmap(bmp: android.graphics.Bitmap): String? =
        runCatching { tidy(recognizer.process(InputImage.fromBitmap(bmp, 0)).await()) }.getOrNull()
}
