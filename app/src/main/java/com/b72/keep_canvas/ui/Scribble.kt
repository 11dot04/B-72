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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** A tiny seeded generator, so the same line is struck the same way every time it is drawn. */
private class Rng(seed: Int) {
    private var x = (seed * 747796405 - 1403630443) or 1
    fun f(): Float {
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        return (x and 0xffffff) / 16777216f
    }
    fun r(a: Float, b: Float) = a + (b - a) * f()
}

/** One mark of the pen, filled, with its alpha. */
private class Ink(val path: Path, val alpha: Float)

/**
 * A brush stroke: a centre line given width, tapering to a point at both ends, a little heavier at the end the pen pressed first and
 * wavering a touch as pressure does. Filled, so it has the dry thick-and-thin edge of a marker rather than the even width of a line.
 */
private fun brush(pts: List<Offset>, base: Float, rng: Rng, heavyStart: Boolean = false, tip: Float = .14f): Path {
    val n = pts.size
    val d = FloatArray(n)
    for (i in 1 until n) d[i] = d[i - 1] + hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
    val total = d[n - 1].coerceAtLeast(1f)
    val ph = rng.r(0f, 6.28f)
    val left = ArrayList<Offset>(n); val right = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val t = d[i] / total
        val a = min(1f, t / tip); val b = min(1f, (1f - t) / tip)
        val env = sin(a * PI.toFloat() / 2).pow(.8f) * sin(b * PI.toFloat() / 2).pow(.8f)
        val press = 1f + .18f * sin(t * 7f + ph) + (if (heavyStart) .25f * (1f - t) else .25f * t)
        val hw = base * env * press / 2f
        val p0 = pts[max(i - 1, 0)]; val p1 = pts[min(i + 1, n - 1)]
        val tx = p1.x - p0.x; val ty = p1.y - p0.y
        val len = hypot(tx, ty).coerceAtLeast(1e-3f)
        val nx = -ty / len; val ny = tx / len
        left.add(Offset(pts[i].x + nx * hw, pts[i].y + ny * hw)); right.add(Offset(pts[i].x - nx * hw, pts[i].y - ny * hw))
    }
    val path = Path()
    path.moveTo(left[0].x, left[0].y)
    for (i in 1 until n) path.lineTo(left[i].x, left[i].y)
    for (i in n - 1 downTo 0) path.lineTo(right[i].x, right[i].y)
    path.close()
    return path
}

/** A hand-drawn line from one point to another: it bows, and wanders a little off true. */
private fun line(x0: Float, y0: Float, x1: Float, y1: Float, bow: Float, rng: Rng, lh: Float, wob: Float = .03f, n: Int = 28): List<Offset> {
    val p1 = rng.r(0f, 6.28f); val p2 = rng.r(0f, 6.28f); val p3 = rng.r(1.5f, 3f)
    return List(n) { i ->
        val t = i / (n - 1f)
        val y = y0 + (y1 - y0) * t + bow * 4f * t * (1f - t) * lh + lh * wob * (sin(t * p3 * PI.toFloat() + p1) + .5f * sin(t * 7f + p2))
        Offset(x0 + (x1 - x0) * t, y)
    }
}

/**
 * The marks that strike off one line of text, from [x0] to [x1] across a line [top]..[bottom] tall. The [pen] is the note's habit
 * (a heavy marker with dry streaks, a shallow X, a dry brush, a loose hatch, a squiggle, a bundle of passes); every other thing about
 * the strike (angle, bow, thickness, where the streaks start and stop) is drawn from the line's own [seed], so no two look alike.
 */
private fun strike(left: Float, right: Float, top: Float, bottom: Float, seed: Int, pen: Int): List<Ink> {
    val rng = Rng(seed)
    val lh = bottom - top
    val mid = (top + bottom) / 2 + lh * .05f
    val x0 = left - lh * .12f; val x1 = right + lh * .15f; val w = x1 - x0
    val out = ArrayList<Ink>()
    fun add(pts: List<Offset>, base: Float, alpha: Float = .95f, heavyStart: Boolean = false, tip: Float = .14f) { out.add(Ink(brush(pts, base, rng, heavyStart, tip), alpha)) }
    val tilt = rng.r(-.1f, .1f) * lh
    fun sign() = if (rng.f() < .5f) 1f else -1f
    when (pen) {
        0 -> { // a heavy marker stroke with streaks that leave it
            val a = rng.r(-.1f, .1f) * lh
            add(line(x0, mid + a + tilt, x1, mid - a - tilt, rng.r(-.12f, .12f), rng, lh), lh * rng.r(.2f, .27f), heavyStart = true)
            repeat(if (rng.f() < .5f) 1 else 2) {
                val sx = x0 + w * rng.r(0f, .3f); val ex = x1 - w * rng.r(0f, .35f)
                val off = rng.r(.12f, .3f) * sign() * lh
                add(line(sx, mid + rng.r(-.03f, .03f) * lh, ex, mid + off, rng.r(-.06f, .06f), rng, lh), lh * rng.r(.03f, .055f), .8f, tip = .3f)
            }
        }
        1 -> { // a shallow X
            val s = rng.r(.18f, .3f) * lh
            add(line(x0, mid - s, x1, mid + s, rng.r(-.08f, .08f), rng, lh), lh * rng.r(.07f, .1f), tip = .12f)
            add(line(x0 + w * rng.r(0f, .08f), mid + s * rng.r(.5f, 1f), x1 - w * rng.r(0f, .08f), mid - s * rng.r(.6f, 1f), rng.r(-.08f, .08f), rng, lh), lh * rng.r(.09f, .13f), tip = .1f)
            if (rng.f() < .6f) {
                val sx = x0 + w * rng.r(.1f, .4f)
                add(line(sx, mid + rng.r(-.15f, .15f) * lh, x1 - w * rng.r(0f, .2f), mid + rng.r(-.15f, .15f) * lh, 0f, rng, lh), lh * .035f, .75f, tip = .3f)
            }
        }
        2 -> { // dry brush: one body, thin streaks beside it
            val a = rng.r(-.06f, .06f) * lh
            add(line(x0, mid + a, x1, mid - a + tilt, rng.r(-.08f, .08f), rng, lh), lh * rng.r(.16f, .22f), heavyStart = true)
            for (off in floatArrayOf(-.2f, .16f, .3f)) {
                if (rng.f() < .25f) continue
                val sx = x0 + w * rng.r(0f, .35f); val ex = x1 - w * rng.r(0f, .4f)
                add(line(sx, mid + off * lh, ex, mid + (off + rng.r(-.08f, .08f)) * lh + tilt, rng.r(-.05f, .05f), rng, lh), lh * rng.r(.025f, .05f), .75f, tip = .35f)
            }
        }
        3 -> { // a loose slanting hatch, closed with a line back across
            val step = lh * rng.r(.8f, 1.1f)
            val pts = ArrayList<Offset>()
            var x = x0; var k = 0
            while (x <= x1 && k < 400) {
                val up = k % 2 == 0
                pts.add(Offset(x + (if (up) lh * .18f else -lh * .1f), mid + (if (up) -1f else 1f) * lh * rng.r(.2f, .46f) + rng.r(-.05f, .05f) * lh))
                x += step / 2 * rng.r(.6f, 1.4f); k++
            }
            if (pts.size >= 2) add(pts, lh * rng.r(.12f, .17f), .93f, tip = .03f)
            if (rng.f() < .85f) add(line(x1, mid + rng.r(-.12f, .12f) * lh, x0 + w * rng.r(0f, .25f), mid + rng.r(-.12f, .12f) * lh, rng.r(-.08f, .08f), rng, lh), lh * rng.r(.06f, .09f), tip = .2f)
        }
        4 -> { // an irregular squiggle with a hairline under it
            val n = 90; val ph = rng.r(0f, 6.28f); val wl0 = lh * rng.r(.8f, 1.3f)
            var phase = ph
            val pts = List(n) { k ->
                val t = k / (n - 1f)
                val wl = wl0 * (1f + .35f * sin(t * 4f + ph))
                phase += w / n / wl * 2f * PI.toFloat()
                val amp = lh * (.12f + .14f * (.5f + .5f * sin(t * 6f + ph * 2f)))
                Offset(x0 + w * t, mid + amp * sin(phase) + tilt * (t - .5f) * 2f)
            }
            add(pts, lh * rng.r(.09f, .13f), tip = .06f)
            if (rng.f() < .7f) {
                val sx = x0 + w * rng.r(0f, .3f)
                add(line(sx, mid + rng.r(.2f, .3f) * lh, x1 - w * rng.r(0f, .2f), mid + rng.r(.15f, .3f) * lh, rng.r(-.05f, .05f), rng, lh), lh * .035f, .75f, tip = .3f)
            }
        }
        else -> { // a bundle: three or four passes, back and forth
            val k = rng.r(3f, 5.99f).toInt()
            for (i in 0 until k) {
                val off = (i - (k - 1) / 2f) * lh * rng.r(.07f, .13f)
                val sx = x0 + w * rng.r(0f, .18f); val ex = x1 - w * rng.r(0f, .2f)
                val fwd = i % 2 == 0
                add(line(if (fwd) sx else ex, mid + off + tilt * .5f, if (fwd) ex else sx, mid + off + rng.r(-.1f, .1f) * lh - tilt * .5f, rng.r(-.07f, .07f), rng, lh), lh * rng.r(.05f, .1f), .9f, tip = .15f)
            }
        }
    }
    return out
}

/** Which pens a note can have; the four line styles are the common ones, the hatch and the squiggle turn up now and then. */
private val PENS = intArrayOf(0, 1, 2, 5, 0, 1, 2, 5, 3, 4)

/**
 * Text that is struck off: the words go quieter and the pen goes through them, line by line, left to right. [seed] gives the note one
 * pen (a habit of stroke), so every strike-off in a note looks like the same hand, while no two lines are the same.
 */
@Composable
fun ScribbleText(
    text: String, done: Boolean, color: Color, accent: Color, style: TextStyle,
    modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, seed: Int = 0,
) {
    val p by animateFloatAsState(if (done) 1f else 0f, MaterialTheme.motionScheme.slowEffectsSpec<Float>(), label = "marker")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val cache = remember { HashMap<String, List<Ink>>() }
    val own = text.hashCode()
    val pen = PENS[((seed % PENS.size) + PENS.size) % PENS.size]
    BasicText(
        text, style = style.copy(color = if (done) color.copy(alpha = .6f) else color), maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            if (p <= 0f) return@drawWithContent
            val lines = l.lineCount
            for (i in 0 until lines) {
                val lp = (p * lines - i).coerceIn(0f, 1f)
                if (lp <= 0f) continue
                val lf = l.getLineLeft(i); val rt = l.getLineRight(i); val tp = l.getLineTop(i); val bt = l.getLineBottom(i)
                if (rt - lf < 4f) continue
                val marks = cache.getOrPut("$i|$lf|$rt|$tp|$bt|$own|$pen") { strike(lf, rt, tp, bt, own + i * 977, pen) }
                val lh = bt - tp
                // the pen travels left to right: show what it has reached
                val x0 = lf - lh * .2f
                clipRect(left = x0, top = tp - lh, right = x0 + (rt - lf + lh * .6f) * lp, bottom = bt + lh) {
                    for (m in marks) drawPath(m.path, accent.copy(alpha = accent.alpha * m.alpha))
                }
            }
        },
    )
}
