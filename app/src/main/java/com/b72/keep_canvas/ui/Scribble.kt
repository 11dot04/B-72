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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.floor
import kotlin.math.min

private fun jitter(seed: Int, k: Int): Float {
    var h = seed * 31 + k * 17 + 7
    h = h xor (h shl 13); h = h xor (h ushr 17); h = h xor (h shl 5)
    return ((h and 0xffff) / 65535f)
}

/** A hand-drawn zig-zag struck through one line of text, drawn from left to right up to [p] (0..1). */
private fun DrawScope.scribble(left: Float, right: Float, top: Float, bottom: Float, p: Float, color: Color, seed: Int) {
    if (p <= 0f || right - left < 4f) return
    val mid = (top + bottom) / 2f + (bottom - top) * .06f
    val amp = (bottom - top) * .26f
    val n = (((right - left) / (size.minDimension.coerceAtLeast(1f) * .02f + 14f)).toInt()).coerceIn(4, 40)
    fun pt(k: Int): Offset {
        val x = left - 2f + (right - left + 4f) * k / n
        val y = mid + (if (k % 2 == 0) -amp else amp) * (.7f + .5f * jitter(seed, k))
        return Offset(x, y)
    }
    val upto = p * n
    val whole = floor(upto).toInt()
    val stroke = ((bottom - top) * .09f).coerceAtLeast(2.5f)
    for (k in 0 until min(whole, n)) drawLine(color, pt(k), pt(k + 1), stroke, StrokeCap.Round)
    if (whole < n) {
        val a = pt(whole)
        val b = pt(whole + 1)
        val f = upto - whole
        drawLine(color, a, Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f), stroke, StrokeCap.Round)
    }
}

/** Text that is crossed out with a scribble when [done]; every wrapped line is struck in turn. */
@Composable
fun ScribbleText(text: String, done: Boolean, color: Color, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    val p by animateFloatAsState(if (done) 1f else 0f, MaterialTheme.motionScheme.slowEffectsSpec<Float>(), label = "scribble")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val seed = text.hashCode()
    BasicText(
        text, style = style.copy(color = if (done) color.copy(alpha = .62f) else color), maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            val lines = l.lineCount
            for (i in 0 until lines) {
                val lp = (p * lines - i).coerceIn(0f, 1f)
                scribble(l.getLineLeft(i), l.getLineRight(i), l.getLineTop(i), l.getLineBottom(i), lp, color, seed + i)
            }
        },
    )
}
