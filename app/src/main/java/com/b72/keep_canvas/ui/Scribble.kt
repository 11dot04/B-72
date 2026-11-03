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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

private fun jitter(seed: Int, k: Int): Float {
    var h = seed * 31 + k * 17 + 7
    h = h xor (h shl 13); h = h xor (h ushr 17); h = h xor (h shl 5)
    return ((h and 0xffff) / 65535f)
}

/**
 * One piece of a pen stroke. Its points run left to right (x 0..1 across the piece); y is -1 at the top of the line, 0 in the middle
 * and 1 at the bottom. A piece begins at the level it [from] and ends at the level it [to], so any piece can follow any piece that
 * ended where it begins: the end of one is the start of the next, and a line is a chain of them. [w] is how many line heights wide it
 * is at its natural size, [busy] is how many direction changes it holds (a pen's habit is to like busy pieces or long sweeps).
 */
private class Piece(val from: Int, val to: Int, val w: Float, val busy: Int, val pts: List<Pair<Float, Float>>)

private fun lv(y: Float) = when { y < -.5f -> 0; y > .5f -> 2; else -> 1 }

private fun piece(w: Float, vararg p: Float): Piece {
    val pts = (0 until p.size / 2).map { p[it * 2] to p[it * 2 + 1] }
    return Piece(lv(pts.first().second), lv(pts.last().second), w, pts.size - 2, pts)
}

/** Twenty-two pieces: diagonals, Z and S turns, zigzags, flat sweeps, a retrace, Vs and waves, in every start and end combination that chains. */
private val PIECES = listOf(
    piece(1.1f, 0f, -1f, 1f, 1f),                                                    // down diagonal, top to bottom
    piece(1.1f, 0f, 1f, 1f, -1f),                                                    // up diagonal, bottom to top
    piece(1.9f, 0f, -1f, .92f, -1f, .08f, 1f, 1f, 1f),                               // Z, top to bottom
    piece(1.9f, 0f, 1f, .92f, 1f, .08f, -1f, 1f, -1f),                               // reversed Z, bottom to top
    piece(2.2f, 0f, -1f, .25f, 1f, .5f, -1f, .75f, 1f, 1f, -1f),                     // zigzag, top to top
    piece(2.2f, 0f, 1f, .25f, -1f, .5f, 1f, .75f, -1f, 1f, 1f),                      // zigzag, bottom to bottom
    piece(1.8f, 0f, -1f, .33f, 1f, .66f, -1f, 1f, 1f),                               // three strokes, top to bottom
    piece(1.8f, 0f, 1f, .33f, -1f, .66f, 1f, 1f, -1f),                               // three strokes, bottom to top
    piece(2.2f, 0f, 0f, .25f, -.8f, .5f, 0f, .75f, .8f, 1f, 0f),                     // wave, middle to middle
    piece(2.2f, 0f, 0f, .25f, .8f, .5f, 0f, .75f, -.8f, 1f, 0f),                     // wave the other way
    piece(2.8f, 0f, 0f, .5f, .12f, 1f, 0f),                                          // long flat sweep
    piece(1.6f, 0f, 0f, 1f, -1f),                                                    // sweep rising to the top
    piece(1.6f, 0f, 0f, 1f, 1f),                                                     // sweep falling to the bottom
    piece(1.4f, 0f, -1f, 1f, 0f),                                                    // top easing to the middle
    piece(1.4f, 0f, 1f, 1f, 0f),                                                     // bottom easing to the middle
    piece(2.5f, 0f, 0f, 1f, -.35f, .06f, .35f, 1f, 0f),                              // retrace: out, back, out again
    piece(2.6f, 0f, -1f, 1f, -1f),                                                   // flat along the top
    piece(2.6f, 0f, 1f, 1f, 1f),                                                     // flat along the bottom
    piece(1.6f, 0f, -1f, .5f, 1f, 1f, -1f),                                          // a V
    piece(1.6f, 0f, 1f, .5f, -1f, 1f, 1f),                                           // an upside-down V
    piece(2.0f, 0f, 0f, .3f, -1f, .6f, 1f, 1f, 0f),                                  // up and down through the middle
    piece(2.0f, 0f, 0f, .3f, 1f, .6f, -1f, 1f, 0f),                                  // down and up through the middle
)

/** A pen is one person's habit, chosen once per note: how wide the strokes are, how tall they reach, how busy the pieces it favours. */
private class Pen(val busy: Float, val reach: Float, val width: Float)

private fun penOf(seed: Int) = when (((seed % 3) + 3) % 3) {
    0 -> Pen(busy = 1.0f, reach = .46f, width = .2f)    // quick and zigzaggy
    1 -> Pen(busy = .3f, reach = .4f, width = .24f)    // long confident sweeps
    else -> Pen(busy = .6f, reach = .43f, width = .22f) // a bit of everything
}

/** The chain of pieces that fills [w] pixels, each starting at the level the last one ended on. */
private fun chain(w: Float, lh: Float, pen: Pen, seed: Int): List<Pair<Piece, Float>> {
    var level = (jitter(seed, 1) * 3).toInt().coerceIn(0, 2)
    val out = ArrayList<Piece>()
    var sum = 0f
    var k = 0
    var prev: Piece? = null
    while (sum < w && k < 60) {
        val options = PIECES.filter { it.from == level && it !== prev }
        // weights: pieces whose busyness is near the pen's liking win, and a little chance for anything
        val ws = options.map { 1f / (.4f + kotlin.math.abs(it.busy - pen.busy)) + .15f }
        var r = jitter(seed, 10 + k) * ws.sum()
        var pick = options.last()
        for ((i, o) in options.withIndex()) { r -= ws[i]; if (r <= 0f) { pick = o; break } }
        out.add(pick); prev = pick; level = pick.to
        sum += pick.w * lh * 1.5f * (.9f + .2f * jitter(seed, 40 + k))
        k++
    }
    // scale to land exactly on the far end
    val raw = out.mapIndexed { i, p -> p.w * lh * 1.5f * (.9f + .2f * jitter(seed, 40 + i)) }
    val f = w / raw.sum()
    return out.mapIndexed { i, p -> p to raw[i] * f }
}

/** A scribble through a line of text, drawn left to right up to [p] (0..1), as one pen stroke made of chained pieces. */
private fun DrawScope.scribble(left: Float, right: Float, top: Float, bottom: Float, p: Float, color: Color, seed: Int, penSeed: Int) {
    if (p <= 0f || right - left < 4f) return
    val lh = bottom - top
    val pen = penOf(penSeed)
    val x0 = left - lh * .12f
    val w = (right - left) + lh * .45f
    val mid = (top + bottom) / 2 + lh * .06f
    val amp = lh * pen.reach
    val tilt = (jitter(seed, 3) - .5f) * lh * .2f
    val parts = chain(w, lh, pen, seed)
    val path = Path()
    var x = x0
    var cur: Offset? = null
    var n = 0
    for ((pc, pw) in parts) {
        // the same piece twice never looks the same: every point is nudged a little
        val pts = pc.pts.mapIndexed { i, (px, py) ->
            val nx = x + px * pw + (if (i == 0 || i == pc.pts.lastIndex) 0f else (jitter(seed, 100 + n * 7 + i) - .5f) * pw * .2f)
            val reach = if (kotlin.math.abs(py) > .5f) .65f + .45f * jitter(seed, 200 + n * 9 + i) else 1f
            val ny = mid + py * amp * reach + tilt * ((nx - x0) / w - .5f) * 2f + (jitter(seed, 300 + n * 5 + i) - .5f) * amp * .12f
            Offset(nx, ny)
        }
        if (cur == null) path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val a = if (i == 1 && cur != null) cur!! else pts[i - 1]; val b = pts[i]
            // each run bows a little, as a hand's does, while the turns stay sharp
            val dx = b.x - a.x; val dy = b.y - a.y
            val len = hypot(dx, dy).coerceAtLeast(1f)
            val bow = (jitter(seed, 400 + n * 5 + i) - .5f) * len * .2f
            path.quadraticTo((a.x + b.x) / 2 - dy / len * bow, (a.y + b.y) / 2 + dx / len * bow, b.x, b.y)
        }
        cur = pts.last()
        x += pw
        n++
    }
    // pressure: the pen is lighter at the start and the end and presses in the middle, drawn as short overlapping runs
    val pm = PathMeasure(); pm.setPath(path, false)
    val total = pm.length * p
    val step = (lh * .35f).coerceAtLeast(4f)
    var d = 0f
    val base = lh * pen.width
    while (d < total) {
        val t = (d / pm.length).coerceIn(0f, 1f)
        val seg = Path()
        pm.getSegment(d, (d + step * 1.3f).coerceAtMost(total), seg, true)
        drawPath(seg, color, style = Stroke(width = base * (.72f + .5f * sin(PI.toFloat() * t)), cap = StrokeCap.Round, join = StrokeJoin.Round))
        d += step
    }
}

/**
 * Text that is struck off: the words go quieter and a scribble is drawn through them, line by line. [seed] gives the note one pen
 * (a habit of stroke), so every strike-off in a note looks like the same hand, while no two lines are the same.
 */
@Composable
fun ScribbleText(
    text: String, done: Boolean, color: Color, accent: Color, style: TextStyle,
    modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, seed: Int = 0,
) {
    val p by animateFloatAsState(if (done) 1f else 0f, MaterialTheme.motionScheme.slowEffectsSpec<Float>(), label = "marker")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val own = text.hashCode()
    BasicText(
        text, style = style.copy(color = if (done) color.copy(alpha = .6f) else color), maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            val lines = l.lineCount
            for (i in 0 until lines) {
                val lp = (p * lines - i).coerceIn(0f, 1f)
                scribble(l.getLineLeft(i), l.getLineRight(i), l.getLineTop(i), l.getLineBottom(i), lp, accent, own + i * 977, seed)
            }
        },
    )
}
