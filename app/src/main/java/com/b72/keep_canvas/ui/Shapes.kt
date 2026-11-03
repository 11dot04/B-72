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
            val poly0 = simplify(pts, diag * .07f)
            // an arrow: one long straight run, then a short head folded back on it
            if (poly0.size in 4..6) {
                val segs = poly0.zipWithNext { a, b -> dist(a, b) }
                val total = segs.sum()
                if (segs[0] > .6f * total && segs.drop(1).all { it < .24f * total } && dist(poly0[0], poly0[1]) > .04f) {
                    val a = poly0[0]; val b = poly0[1]
                    val len = dist(a, b)
                    return Stuck(id, "m:Dart", (a.x + b.x) / 2 - len / 2, (a.y + b.y) / 2 - .01f, len, atan2(b.y - a.y, b.x - a.x) * 180f / Math.PI.toFloat(), color, .02f)
                }
            }
            // a tick: down a little, then up a long way
            if (poly0.size == 3) {
                val d1 = dist(poly0[0], poly0[1]); val d2 = dist(poly0[1], poly0[2])
                if (d2 > d1 * 1.4f && poly0[1].y > poly0[0].y && poly0[1].y > poly0[2].y && bw > .03f) {
                    val s2 = max(bw, bh) + .01f
                    return Stuck(id, "m:Check", (minX + maxX) / 2 - s2 / 2, (minY + maxY) / 2 - s2 / 2, s2, 0f, color)
                }
            }
            // a squiggle: many turns along a thin band
            var turns = 0
            var dir = 0
            for (i in 1 until pts.size) { val dx = pts[i].x - pts[i - 1].x; val d = if (dx > 0) 1 else if (dx < 0) -1 else 0; if (d != 0 && dir != 0 && d != dir && abs(dx) > diag * .01f) turns++; if (d != 0) dir = d }
            if (turns >= 3 && bh < bw * .5f && bw > .05f) return Stuck(id, "m:Squiggle", minX, (minY + maxY) / 2 - bw * .16f, bw, 0f, color)
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
