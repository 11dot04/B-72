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
 * One bold marker stroke through a line of text, drawn from left to right up to [p] (0..1): a single fat felt-tip
 * pass that wanders a little and rises toward the end, like a hand striking the item off. It sits over the words
 * and is translucent, so they stay readable underneath.
 */
private fun DrawScope.marker(left: Float, right: Float, top: Float, bottom: Float, p: Float, color: Color, seed: Int) {
    if (p <= 0f || right - left < 4f) return
    val lh = bottom - top
    val mid = (top + bottom) / 2f + lh * .04f
    val x0 = left - lh * .25f
    val x1 = right + lh * .3f
    val rise = lh * (.06f + .1f * jitter(seed, 0))
    val path = Path()
    path.moveTo(x0, mid + lh * .1f * (jitter(seed, 1) - .5f))
    val n = (((x1 - x0) / (lh * 2.4f)).toInt()).coerceIn(1, 14)
    for (k in 1..n) {
        val f0 = (k - .5f) / n
        val f1 = k.toFloat() / n
        val wob = lh * .13f * (jitter(seed, k + 2) - .5f) * 2f
        path.cubicTo(
            x0 + (x1 - x0) * (f0 - .2f / n), mid - rise * f0 + wob,
            x0 + (x1 - x0) * (f0 + .2f / n), mid - rise * f0 - wob,
            x0 + (x1 - x0) * f1, mid - rise * f1 + lh * .05f * (jitter(seed, k + 40) - .5f),
        )
    }
    val m = PathMeasure()
    m.setPath(path, false)
    val part = Path()
    m.getSegment(0f, m.length * p, part, true)
    drawPath(part, color, style = Stroke(width = lh * .52f, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = .78f)
}

/**
 * Text that is struck off with a marker stroke when [done]; every wrapped line gets its own stroke in turn.
 * [accent] is the marker's colour, [color] the text's.
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
                marker(l.getLineLeft(i), l.getLineRight(i), l.getLineTop(i), l.getLineBottom(i), lp, accent, seed + i)
            }
        },
    )
}
