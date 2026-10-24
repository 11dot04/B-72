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

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

/** Reads the text in a photo on the device (a receipt, a poster, a whiteboard) so search can find it. */
object Ocr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** The text found, "" when there is none, null when the photo could not be read (tried again next launch). */
    suspend fun read(ctx: Context, path: String): String? {
        if (!File(path).exists()) return null
        return runCatching { recognizer.process(InputImage.fromFilePath(ctx, Uri.fromFile(File(path)))).await().text.trim() }.getOrNull()
    }
}
