package com.b72.keep_canvas.data

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * "What else do I know about this?": notes ranked by how much they have in common with one note. Always works offline with
 * TF-IDF word overlap; when a sentence-embedding model is packaged with the app, meaning counts as well as words.
 */
object Related {
    private val STOP = setOf(
        "the", "and", "for", "with", "that", "this", "from", "have", "was", "are", "you", "your", "but", "not", "all", "can",
        "had", "has", "her", "his", "its", "our", "out", "she", "they", "them", "then", "there", "what", "when", "who", "will",
        "would", "about", "into", "just", "like", "more", "some", "than", "very", "been", "were", "also", "http", "https", "www", "com",
    )
    private val WORD = Regex("[\\p{L}\\p{N}]{3,}")

    private fun tokens(t: String): List<String> = WORD.findAll(t).map { it.value }.filter { it !in STOP }.toList()

    private fun cos(a: Map<String, Float>, b: Map<String, Float>): Float {
        var dot = 0f
        for ((k, v) in a) b[k]?.let { dot += v * it }
        var na = 0f
        for (v in a.values) na += v * v
        var nb = 0f
        for (v in b.values) nb += v * v
        return if (na == 0f || nb == 0f) 0f else dot / (sqrt(na) * sqrt(nb))
    }

    private fun cos(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0f || nb == 0f) 0f else dot / (sqrt(na) * sqrt(nb))
    }

    /** The [k] notes most related to [of], best first. [vector] gives a note's meaning vector when a model is available. */
    fun rank(all: List<Note>, of: Note, k: Int = 6, vector: ((Note) -> FloatArray?)? = null): List<Note> {
        if (all.size < 2) return emptyList()
        val docs = all.associate { it.id to tokens(it.searchText()) }
        val df = HashMap<String, Int>()
        for (d in docs.values) for (t in d.toSet()) df[t] = (df[t] ?: 0) + 1
        val n = all.size.toFloat()
        fun vec(id: String): Map<String, Float> {
            val tf = HashMap<String, Float>()
            for (t in docs[id].orEmpty()) tf[t] = (tf[t] ?: 0f) + 1f
            return tf.mapValues { (t, c) -> (1f + ln(c)) * ln(1f + n / (df[t] ?: 1)) }
        }
        val me = vec(of.id)
        val myVec = vector?.invoke(of)
        // notes that link to this one by hand ([[Title]]) come first
        val needle = "[[" + of.title.trim().lowercase() + "]]"
        val linked = if (of.title.isBlank()) emptySet() else all.filter { it.id != of.id && needle in it.searchText() }.map { it.id }.toSet()
        val scored = all.filter { it.id != of.id }.map { o ->
            val lexical = cos(me, vec(o.id))
            val semantic = if (myVec != null) vector?.invoke(o)?.let { cos(myVec, it) } else null
            var score = if (semantic != null) .6f * semantic.coerceAtLeast(0f) + .4f * lexical else lexical
            score += .1f * o.tags.count { it in of.tags }.coerceAtMost(2)
            if (o.id in linked) score += 1f
            o to score
        }
        val floor = if (myVec != null) .3f else .08f
        return scored.filter { it.second >= floor || it.first.id in linked }.sortedByDescending { it.second }.take(k).map { it.first }
    }
}
