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

private fun partOf(path: Path, p: Float): Path {
    val pm = PathMeasure()
    pm.setPath(path, false)
    val part = Path()
    pm.getSegment(0f, pm.length * p, part, true)
    return part
}

/** Points joined by straight runs: the round cap and join are what soften the turns. */
private fun poly(pts: List<Offset>): Path { val p = Path(); p.moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y); return p }

/**
 * A scribble through a line of text, drawn left to right up to [p] (0..1). Seven hand-made styles, each chosen per line from its text:
 * the heavy marker that wanders up and down at random (the commonest), looping cursive, a doubled wobbly line, a long slash with a hook,
 * a highlighter wipe with a thin pen line, a diagonal hatch. Every one drifts a little off level, and is drawn with a soft ink bleed
 * under a sharp stroke so it reads as pen, not as a vector line.
 */
private fun DrawScope.scribble(left: Float, right: Float, top: Float, bottom: Float, p: Float, color: Color, seed: Int) {
    if (p <= 0f || right - left < 4f) return
    val lh = bottom - top
    val x0 = left - lh * .15f
    val x1 = right + lh * .3f
    val w = x1 - x0
    val hi = top + lh * .2f
    val lo = bottom - lh * .2f
    val mid = (hi + lo) / 2
    val tilt = (jitter(seed, 3) - .5f) * lh * .3f
    fun drift(x: Float) = tilt * ((x - x0) / w - .5f) * 2f
    val style = when (((seed % 8) + 8) % 8) { 0, 1, 2 -> 0; 3 -> 1; 4 -> 2; 5 -> 3; 6 -> 4; else -> 5 }
    val base = lh * (.17f + .06f * jitter(seed, 5))
    class Layer(val path: Path, val width: Float, val alpha: Float)
    val layers = ArrayList<Layer>()
    when (style) {
        0 -> {
            val n = (w / (lh * 1.05f)).toInt().coerceIn(3, 20)
            var up = jitter(seed, 0) > .5f
            val pts = ArrayList<Offset>()
            pts.add(Offset(x0, if (up) hi else lo))
            for (k in 1..n) {
                if (jitter(seed, k + 50) > .3f) up = !up
                val reach = .4f + .6f * jitter(seed, k + 90)
                val x = x0 + w * k / n + lh * .22f * (jitter(seed, k + 10) - .5f)
                pts.add(Offset(x, mid + (if (up) hi - mid else lo - mid) * reach + drift(x)))
            }
            layers.add(Layer(poly(pts), base * 1.15f, .92f))
        }
        1 -> {
            // loops, like a looping cursive "e" run along the line
            val n = (w / (lh * 1.1f)).coerceIn(3f, 16f)
            val steps = (n * 26).toInt()
            val pts = (0..steps).map { i ->
                val t = i / steps.toFloat(); val th = 2f * Math.PI.toFloat() * n * t
                val x = x0 + w * t - lh * .5f * kotlin.math.sin(th)
                Offset(x, mid + lh * .3f * kotlin.math.cos(th) + drift(x))
            }
            layers.add(Layer(poly(pts), base * .85f, .9f))
        }
        2 -> {
            // two wobbly strokes, one a little above the other, going the same way
            for (k in 0..1) {
                val steps = 36
                val off = (if (k == 0) -1 else 1) * lh * (.12f + .06f * jitter(seed, 7 + k))
                val ph = jitter(seed, 11 + k) * 6.28f
                val pts = (0..steps).map { i ->
                    val t = i / steps.toFloat(); val x = x0 + w * t
                    Offset(x, mid + off + drift(x) + lh * .07f * kotlin.math.sin(t * 9f + ph))
                }
                layers.add(Layer(poly(pts), base * .7f, .9f))
            }
        }
        3 -> {
            // a long confident slash across, then a short hook back along the bottom
            val path = Path()
            path.moveTo(x0, lo + drift(x0))
            path.cubicTo(x0 + w * .3f, mid + lh * .25f, x0 + w * .7f, mid - lh * .35f, x1, hi + drift(x1))
            path.cubicTo(x1 - w * .12f, hi + lh * .5f, x1 - w * .2f, lo + lh * .05f, x1 - w * .34f, lo)
            layers.add(Layer(path, base * 1.05f, .92f))
        }
        4 -> {
            // highlighter wipe, with a thin pen line over it
            val bar = Path(); bar.moveTo(x0, mid + drift(x0)); bar.lineTo(x1, mid + drift(x1))
            layers.add(Layer(bar, lh * .66f, .3f))
            val steps = 28
            val pts = (0..steps).map { i -> val t = i / steps.toFloat(); val x = x0 + w * t; Offset(x, mid + drift(x) + lh * .08f * kotlin.math.sin(t * 13f + jitter(seed, 2) * 6f)) }
            layers.add(Layer(poly(pts), base * .55f, .95f))
        }
        else -> {
            // hatching: slanted strokes joined along the bottom
            val n = (w / (lh * .42f)).toInt().coerceIn(4, 40)
            val pts = ArrayList<Offset>()
            for (k in 0 until n) {
                val xa = x0 + w * k / n
                pts.add(Offset(xa, lo + drift(xa))); pts.add(Offset(xa + lh * .5f, hi + drift(xa)))
            }
            layers.add(Layer(poly(pts), base * .6f, .88f))
        }
    }
    for (l in layers) {
        val part = partOf(l.path, p)
        drawPath(part, color, style = Stroke(width = l.width * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = l.alpha * .16f)
        drawPath(part, color, style = Stroke(width = l.width, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = l.alpha)
    }
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
                scribble(l.getLineLeft(i), l.getLineRight(i), l.getLineTop(i), l.getLineBottom(i), lp, color, seed + i)
            }
        },
    )
}
