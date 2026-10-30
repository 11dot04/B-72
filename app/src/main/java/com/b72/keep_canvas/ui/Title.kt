package com.b72.keep_canvas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.seedOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class Accent { None, Serif, Flip, Marker, Outline }

/** One personality for a title; each note picks one from its id so a grid reads like a magazine rack. */
class TitleLook(val wght: Int, val rond: Float, val slnt: Float, val upper: Boolean = false, val accent: Accent = Accent.None)

private val Looks = listOf(
    TitleLook(800, 100f, 0f),                                   // soft heavy
    TitleLook(900, 0f, 0f, upper = true),                       // hard poster caps
    TitleLook(300, 0f, 0f, accent = Accent.Flip),               // light, last word heavy
    TitleLook(720, 100f, -10f),                                 // slanted, round
    TitleLook(560, 45f, 0f, accent = Accent.Marker),            // highlighter on the last word
    TitleLook(880, 0f, 0f, accent = Accent.Serif),              // the one serif-italic look
    TitleLook(420, 100f, 0f, upper = true, accent = Accent.Outline), // rounded caps, outlined last word
    TitleLook(650, 0f, -8f),                                    // slanted, sharp
)

fun lookFor(seed: String) = Looks[seedOf(seed) % Looks.size]

private val Tail = setOf("a", "an", "the", "of", "in", "on", "at", "to", "for", "and", "or", "with", "as", "by", "from") // never end a line on these
private val Lead = setOf("as", "of", "in", "on", "with", "for", "from", "by", "and", "to")                              // good words to start a line with

/** Balanced line breaks that respect phrasing: "Motion Blur" / "as an Art Form". */
fun breakTitle(raw: String, maxLines: Int = 3): List<String> {
    val t = raw.trim()
    val w = t.split(Regex("\\s+"))
    if (w.size < 3 || t.length < 14) return listOf(t)
    val n = if (t.length > 34 && w.size > 5 && maxLines >= 3) 3 else 2
    var best = listOf(t)
    var bc = 1e9
    fun tryCut(cuts: List<Int>) {
        val parts = mutableListOf<String>()
        var s = 0
        for (c in cuts + w.size) { parts.add(w.subList(s, c).joinToString(" ")); s = c }
        val mean = t.length.toDouble() / parts.size
        var cost = 0.0
        for (p in parts) cost += abs(p.length - mean)
        for (c in cuts) {
            if (w[c - 1].lowercase() in Tail) cost += 10
            if (w[c].lowercase() in Lead) cost -= 4
        }
        if (cost < bc) { bc = cost; best = parts }
    }
    if (n == 2) for (i in 1 until w.size) tryCut(listOf(i))
    else for (i in 1 until w.size - 1) for (j in i + 1 until w.size) tryCut(listOf(i, j))
    return best
}

class Piece(val text: AnnotatedString, val style: TextStyle)

/** One title line in a given look. Measuring and drawing both go through here. */
fun mk(line: String, sPx: Float, wd: Float, k: TitleLook, grade: Float, color: Color, accent: Boolean, d: Density): Piece {
    val t = if (k.upper) line.uppercase() else line
    val sz = with(d) { sPx.toSp() }
    val base = TextStyle(
        fontFamily = flex(k.wght, wd, k.rond, k.slnt, grade, sPx / d.density),
        fontSize = sz,
        lineHeight = (sz.value * 0.88f).sp,
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
        ),
        letterSpacing = with(d) { (-sPx * .01f).toSp() },
        color = color,
        // the width axis animates, so glyphs are drawn as paths instead of snapped to hinted pixels
        textMotion = androidx.compose.ui.text.style.TextMotion.Animated,
    )
    val i = t.lastIndexOf(' ')
    if (!accent || i < 0 || k.accent == Accent.None) return Piece(AnnotatedString(t), base)
    val last = when (k.accent) {
        Accent.Serif -> SpanStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = sz * 1.1f)
        Accent.Flip -> SpanStyle(fontFamily = flex(if (k.wght < 500) 900 else 200, wd, k.rond, k.slnt, grade, sPx / d.density))
        Accent.Marker -> SpanStyle(background = color.copy(alpha = .22f))
        else -> SpanStyle(drawStyle = Stroke(width = max(1.2f, sPx * .02f)))
    }
    val text = buildAnnotatedString {
        append(t.substring(0, i + 1))
        withStyle(last) { append(t.substring(i + 1)) }
    }
    return Piece(text, base)
}

/** Picks the width axis (and, if needed, the size) so a line fills [w] exactly. */
private fun fit(width: (Float, Float) -> Float, w: Float, size: Float, grow: Boolean): Pair<Float, Float> {
    var lo = 25f
    var hi = 151f
    val widest = width(size, hi)
    if (widest < w) return if (grow) minOf(size * w / max(widest, 1f), size * 2.4f) to hi else size to hi
    val narrowest = width(size, lo)
    if (narrowest > w) return size * w / narrowest to lo
    repeat(8) {
        val mid = ((lo + hi) / 2).roundToInt().toFloat()
        if (mid != lo && mid != hi) { if (width(size, mid) > w) hi = mid else lo = mid }
    }
    return size to lo
}

/** The measured title: balanced lines, each with the size and width axis that fill the width. */
class TitleLay(
    val look: TitleLook,
    val lines: List<String>,
    val sizes: List<Float>,
    val wdths: List<Float>,
    val heights: List<Float>,
    val grade: Float,
    val density: Density,
) {
    fun accentAt(i: Int) = look.accent != Accent.None && i == lines.lastIndex && lines[i].contains(' ')

    /** [flex] 0..1 animates the width axis from condensed to the full-width fit. Steps are quantised so fonts are reused. */
    fun piece(i: Int, color: Color, flex: Float): Piece {
        val f = flex.coerceIn(0f, 1f)
        val wd = 25 + (wdths[i] - 25) * f
        val q = if (f >= .999f) wdths[i].roundToInt().toFloat() else (wd / 3).roundToInt() * 3f
        return mk(lines[i], sizes[i], q, look, grade, color, accentAt(i), density)
    }
}

private val layCache = HashMap<String, TitleLay>()

fun titleLay(text: String, w: Float, seed: String, maxLines: Int, grade: Float, m: TextMeasurer, d: Density): TitleLay {
    val key = "$text|${w.toInt()}|$seed|$maxLines|${grade.toInt()}|${d.density}"
    layCache[key]?.let { return it }
    val k = lookFor(seed.ifEmpty { text })
    val lines = breakTitle(if (k.upper) text.uppercase() else text, maxLines)
    val size = w * (if (lines.size == 1) .26f else if (lines.size == 2) .19f else .15f)
    val sz = ArrayList<Float>()
    val wd = ArrayList<Float>()
    val hs = ArrayList<Float>()
    for (line in lines) {
        val acc = k.accent != Accent.None && line === lines.last() && line.contains(' ')
        fun piece(s: Float, x: Float) = mk(line, s, x, k, grade, Color.Unspecified, acc, d)
        val f = fit({ s, x -> val p = piece(s, x); m.measure(p.text, p.style, softWrap = false, maxLines = 1).size.width.toFloat() }, w, size, lines.size == 1)
        sz.add(f.first); wd.add(f.second)
        val p = piece(f.first, f.second)
        hs.add(m.measure(p.text, p.style, softWrap = false, maxLines = 1).size.height.toFloat())
    }
    return TitleLay(k, lines, sz, wd, hs, grade, d).also {
        if (layCache.size > 400) layCache.clear()
        layCache[key] = it
    }
}

@Composable
fun rememberTitleLay(text: String, wPx: Float, seed: String, maxLines: Int = 3): TitleLay {
    val m = rememberTextMeasurer()
    val d = LocalDensity.current
    val grade = if (isSystemInDarkTheme()) -25f else 0f
    return remember(text, wPx, seed, maxLines, grade, d.density) { titleLay(text, wPx, seed, maxLines, grade, m, d) }
}

/** A title that always fills its width. [flex] (0..1) is read per frame; the layout itself is computed once. */
@Composable
fun FitTitle(text: String, color: Color, seed: String, modifier: Modifier = Modifier, maxLines: Int = 3, flex: () -> Float = { 1f }) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val lay = rememberTitleLay(text, constraints.maxWidth.toFloat(), seed, maxLines)
        val f = flex()
        Column {
            for (i in lay.lines.indices) {
                val p = lay.piece(i, color, f)
                Text(p.text, style = p.style, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** One line of text that shrinks (down to [min]) until it fits its width, so a long word is never broken across lines. */
@Composable
fun FitLine(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, min: Float = 12f) {
    val m = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth
        val shown = remember(text, style, w) {
            var size = style.fontSize.value
            var s = style
            while (size > min) {
                s = style.copy(fontSize = size.sp, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)
                if (m.measure(text, s, softWrap = false, maxLines = 1).size.width <= w) break
                size *= .93f
            }
            s.copy(fontSize = size.coerceAtLeast(min).sp)
        }
        Text(text, style = shown, color = color, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
