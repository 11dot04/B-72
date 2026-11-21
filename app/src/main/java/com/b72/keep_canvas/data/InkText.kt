package com.b72.keep_canvas.data

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import kotlinx.coroutines.withTimeout

/**
 * Handwriting to text with ML Kit's digital ink recognition, so a note drawn with a finger can be found by what it says. The recogniser's
 * model (about 20 MB, English) is fetched once from Google; [warm] does that while the drawing sheet is open and [read] never waits for it.
 */
object InkText {
    private var recognizer: DigitalInkRecognizer? = null
    private var model: DigitalInkRecognitionModel? = null

    private fun model(): DigitalInkRecognitionModel? {
        model?.let { return it }
        val id = runCatching { DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US") }.getOrNull() ?: return null
        return DigitalInkRecognitionModel.builder(id).build().also { model = it }
    }

    /** Fetches the handwriting model if it is not on the phone yet. Quietly does nothing without a connection. */
    suspend fun warm() {
        val m = model() ?: return
        runCatching {
            val mgr = RemoteModelManager.getInstance()
            if (!mgr.isModelDownloaded(m).await()) withTimeout(60_000) { mgr.download(m, DownloadConditions.Builder().build()).await() }
        }
    }

    /** The words in the strokes (each a list of x to y points, in the order drawn), or null when they could not be read. */
    suspend fun read(strokes: List<List<Pair<Float, Float>>>): String? {
        if (strokes.isEmpty()) return null
        val m = model() ?: return null
        return runCatching {
            if (!RemoteModelManager.getInstance().isModelDownloaded(m).await()) return@runCatching null
            val rec = recognizer ?: DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build()).also { recognizer = it }
            val ink = Ink.builder()
            var t = 0L
            for (s in strokes) {
                val sb = Ink.Stroke.builder()
                for ((x, y) in s) { sb.addPoint(Ink.Point.create(x, y, t)); t += 8 }
                ink.addStroke(sb.build())
                t += 150
            }
            rec.recognize(ink.build()).await().candidates.firstOrNull()?.text?.trim()
        }.getOrNull()
    }
}
