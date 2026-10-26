package com.b72.keep_canvas.data

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder

/**
 * The optional on-device language model: a small sentence embedder (MediaPipe's Universal Sentence Encoder) that turns a note
 * into a vector of its meaning. The model file is packaged in assets by the build; without it everything still works, just on
 * word overlap alone. Nothing leaves the phone.
 */
class Brain(private val ctx: Context) {
    private val file = "sentence_encoder.tflite"
    private val embedder: TextEmbedder? by lazy {
        runCatching {
            if (ctx.assets.list("")?.contains(file) != true) return@runCatching null
            val base = BaseOptions.builder().setModelAssetPath(file).build()
            TextEmbedder.createFromOptions(ctx, TextEmbedder.TextEmbedderOptions.builder().setBaseOptions(base).build())
        }.getOrNull()
    }
    private val cache = HashMap<String, Pair<Int, FloatArray>>()

    val available: Boolean get() = embedder != null

    /** What a note says, as one vector; null when no model is packaged or the note has no words. */
    @Synchronized
    fun vector(n: Note): FloatArray? {
        val e = embedder ?: return null
        val text = buildString {
            append(n.title).append(". ")
            for (b in n.blocks) { append(b.body).append(' '); b.items.forEach { append(it).append(' ') } }
            n.tags.forEach { append(it).append(' ') }
        }.trim().take(1200)
        if (text.length < 4) return null
        val h = text.hashCode()
        cache[n.id]?.let { if (it.first == h) return it.second }
        val v = runCatching { e.embed(text).embeddingResult().embeddings().first().floatEmbedding() }.getOrNull() ?: return null
        cache[n.id] = h to v
        return v
    }
}
