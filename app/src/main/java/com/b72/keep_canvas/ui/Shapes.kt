package com.b72.keep_canvas.ui

import androidx.compose.ui.geometry.Offset
import com.b72.keep_canvas.data.Stuck
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a rough stroke into the clean shape it was meant to be: a straight line, a circle or oval, a box, a triangle.
 * [pts] are canvas coordinates in fractions of the canvas width. Returns null when the stroke is none of these.
 */
object Shapes {
    private fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)

    private fun simplify(p: List<Offset>, eps: Float): List<Offset> {
        if (p.size < 3) return p
        var dm = 0f; var idx = 0
        val a = p.first(); val b = p.last()
        val len = dist(a, b).coerceAtLeast(1e-6f)
        for (i in 1 until p.size - 1) {
            val d = abs((b.x - a.x) * (a.y - p[i].y) - (a.x - p[i].x) * (b.y - a.y)) / len
            if (d > dm) { dm = d; idx = i }
        }
        return if (dm > eps) simplify(p.subList(0, idx + 1), eps).dropLast(1) + simplify(p.subList(idx, p.size), eps) else listOf(a, b)
    }

    fun recognise(pts: List<Offset>, id: String, color: Int): Stuck? {
        if (pts.size < 6) return null
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val bw = maxX - minX; val bh = maxY - minY
        val diag = hypot(bw, bh)
        if (diag < .02f) return null
        val pathLen = pts.zipWithNext { a, b -> dist(a, b) }.sum()
        val first = pts.first(); val last = pts.last()
        val closed = dist(first, last) < diag * .3f && pathLen > diag * 1.6f
        if (!closed) {
            // a line: the stroke stays close to the straight run between its ends
            val chord = dist(first, last)
            if (chord > .03f) {
                val dev = pts.maxOf { abs((last.x - first.x) * (first.y - it.y) - (first.x - it.x) * (last.y - first.y)) / chord }
                if (dev < chord * .09f) {
                    val mid = Offset((first.x + last.x) / 2, (first.y + last.y) / 2)
                    val h = .02f
                    return Stuck(id, "m:Line", mid.x - chord / 2, mid.y - h / 2, chord, atan2(last.y - first.y, last.x - first.x) * 180f / Math.PI.toFloat(), color, h)
                }
            }
            return null
        }
        val poly = simplify(pts + pts.first(), diag * .09f)
        val corners = poly.size - 1
        val pad = .01f
        var w = bw + pad; var h = bh + pad
        val kind = when {
            corners == 3 -> "Tri"
            corners == 4 -> "Rect"
            else -> "Oval"
        }
        // nearly square or nearly round shapes are made exactly so
        if (abs(w - h) < max(w, h) * .18f) { val s = (w + h) / 2; w = s; h = s }
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        return Stuck(id, "m:$kind", cx - w / 2, cy - h / 2, w, 0f, color, h)
    }
}
