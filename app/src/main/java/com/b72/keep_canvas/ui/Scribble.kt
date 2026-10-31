@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

private fun jitter(seed: Int, k: Int): Float {
    var h = seed * 31 + k * 17 + 7
    h = h xor (h shl 13); h = h xor (h ushr 17); h = h xor (h shl 5)
    return ((h and 0xffff) / 65535f)
}

/**
 * A heavy marker scribble through a line of text, drawn left to right up to [p] (0..1): one pen at a steady thickness that wanders
 * up and down the line at random, sometimes twice on the same side, never a regular zigzag, and runs a little past both ends.
 */
private fun DrawScope.marker(left: Float, right: Float, top: Float, bottom: Float, p: Float, color: Color, seed: Int) {
    if (p <= 0f || right - left < 4f) return
    val lh = bottom - top
    val x0 = left - lh * .15f
    val x1 = right + lh * .3f
    val hi = top + lh * .2f
    val lo = bottom - lh * .2f
    val mid = (hi + lo) / 2
    val n = (((x1 - x0) / (lh * 1.05f)).toInt()).coerceIn(3, 20)
    val pts = ArrayList<Offset>()
    var up = jitter(seed, 0) > .5f
    pts.add(Offset(x0, if (up) hi else lo))
    for (k in 1..n) {
        if (jitter(seed, k + 50) > .3f) up = !up
        val reach = .4f + .6f * jitter(seed, k + 90)
        val y = mid + (if (up) hi - mid else lo - mid) * reach
        pts.add(Offset(x0 + (x1 - x0) * k / n + lh * .22f * (jitter(seed, k + 10) - .5f), y))
    }
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val m = (pts[i - 1] + pts[i]) / 2f
        path.quadTo(pts[i - 1].x, pts[i - 1].y, m.x, m.y)
    }
    path.lineTo(pts.last().x, pts.last().y)
    val pm = PathMeasure()
    pm.setPath(path, false)
    val part = Path()
    pm.getSegment(0f, pm.length * p, part, true)
    drawPath(part, color, style = Stroke(width = lh * .2f, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = .92f)
}

/**
 * Text that is struck off with a marker stroke when [done]; every wrapped line gets its own stroke in turn.
 * The scribble is drawn in [color], the text's own ink; [accent] is kept for callers.
 */
@Composable
fun ScribbleText(
    text: String, done: Boolean, color: Color, accent: Color, style: TextStyle,
    modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE,
) {
    val p by animateFloatAsState(if (done) 1f else 0f, MaterialTheme.motionScheme.slowEffectsSpec<Float>(), label = "marker")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val seed = text.hashCode()
    BasicText(
        text, style = style.copy(color = if (done) color.copy(alpha = .7f) else color), maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            val lines = l.lineCount
            for (i in 0 until lines) {
                val lp = (p * lines - i).coerceIn(0f, 1f)
                marker(l.getLineLeft(i), l.getLineRight(i), l.getLineTop(i), l.getLineBottom(i), lp, color, seed + i)
            }
        },
    )
}
