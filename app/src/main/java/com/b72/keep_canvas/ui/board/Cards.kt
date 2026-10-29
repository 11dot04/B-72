@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.RECORD_KINDS
import com.b72.keep_canvas.data.dbl
import com.b72.keep_canvas.data.pairs
import com.b72.keep_canvas.data.parsePoster
import com.b72.keep_canvas.data.recAspect
import com.b72.keep_canvas.data.recSub
import com.b72.keep_canvas.data.seedOf
import com.b72.keep_canvas.ui.PhotoImage
import com.b72.keep_canvas.ui.Serif
import com.b72.keep_canvas.ui.flexStyle
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything that depends on the canvas width: unit size, density, and a way to measure text with the same styles the cards draw with. */
class Scale(val cw: Float, val d: Density, val tm: TextMeasurer) {
    val u = cw / WIDE
    fun sp(px: Float): TextUnit = with(d) { px.toSp() }
    fun dp(px: Float): Dp = with(d) { px.toDp() }
    fun h(text: String, st: TextStyle, wPx: Float, maxLines: Int = Int.MAX_VALUE): Float =
        if (text.isEmpty()) 0f else tm.measure(text, style = st, maxLines = maxLines, constraints = Constraints(maxWidth = max(1, wPx.toInt()))).size.height.toFloat()

    fun title(size: Float, c: Color = Color.Unspecified) = flexStyle(sp(u * size), wght = 650, lh = 1.1f).copy(color = c)
    fun body(c: Color = Color.Unspecified) = TextStyle(fontSize = sp(u * .034f), lineHeight = sp(u * .034f * 1.25f), color = c)
    fun serif(px: Float, c: Color, italic: Boolean = true, lh: Float = 1.28f) =
        TextStyle(fontFamily = Serif, fontSize = sp(px), lineHeight = sp(px * lh), fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, color = c)
    fun caps(px: Float, c: Color) = TextStyle(fontSize = sp(px), fontWeight = FontWeight.Bold, letterSpacing = sp(.8f), color = c)
    fun head(px: Float, c: Color) = flexStyle(sp(px), wght = 820, wdth = 118f, lh = 1f).copy(color = c)
}

/** Height of a card and what had to be cut to fit: lines of body, checklist rows, hidden count, and a quote's font size. */
class Lay(val h: Float, val lines: Int = 0, val rows: Int = 0, val hidden: Int = 0, val fs: Float = 0f)

fun cardWidth(b: Block, big: Boolean): Float = K_WIDE * when (b.kind) {
    // hero photos are big, supporting pieces small: scale contrast is what makes a collage read as composed
    Kind.Photo -> if (big) .44f + (seedOf(b.id) % 5) * .035f else .26f + (seedOf(b.id) % 3) * .04f
    Kind.Bare -> .5f
    Kind.Quote -> .46f
    Kind.Stat -> .30f
    Kind.Info -> .38f
    Kind.Link, Kind.Recipe -> .42f
    Kind.Film, Kind.Book -> .34f
    Kind.Track -> .38f
    Kind.Poster -> .56f
    else -> .36f
}

fun checkItemsOf(b: Block) = b.checkItems

fun lay(b: Block, wFrac: Float, s: Scale): Lay {
    val u = s.u
    val wpx = wFrac * s.cw
    if (b.kind == Kind.Photo) return Lay(wpx * b.aspect.toFloat())
    if (b.kind in RECORD_KINDS) return Lay(wpx * recAspect(b.kind).toFloat())
    val o = u * .012f
    when (b.kind) {
        Kind.Quote, Kind.Stat, Kind.Info, Kind.Poster -> {
            val pad = u * .035f
            val inner = wpx - 2 * o - 2 * pad
            val frame = 2 * o + 2 * pad
            when (b.kind) {
                Kind.Quote -> {
                    val base = frame + u * .112f + (if (b.title.isEmpty()) 0f else u * .02f + u * .036f * 1.25f)
                    val maxText = max(wpx * 1.15f - base, u * .1f)
                    var fs = u * .055f
                    fun th(f: Float) = s.h(b.body, s.serif(f, Color.Black), inner)
                    while (fs > u * .03f && th(fs) > maxText) fs -= u * .003f
                    return Lay(base + th(fs), fs = fs)
                }
                Kind.Stat -> return Lay(frame + u * .2f)
                Kind.Info -> {
                    val rowH = u * .1f
                    val n = pairs(b.body).size
                    val base = frame + (if (b.title.isEmpty()) 0f else u * .07f)
                    val fit = floor((wpx - base) / rowH).toInt()
                    var show = n
                    var hidden = 0
                    if (n > fit) { show = max(1, fit - 1); hidden = n - show }
                    return Lay(base + (show + (if (hidden > 0) 1 else 0)) * rowH, rows = show, hidden = hidden)
                }
                else -> {
                    val p = parsePoster(b.title, b.body)
                    val nf = p.facts.size
                    val headH = if (p.head.isEmpty()) 0f else s.h(p.head, s.head(u * .095f, Color.Black), inner, 3) + u * .02f
                    val h = frame + u * .065f + headH +
                        (if (p.big == null) 0f else u * .03f + u * .04f + u * .2f) +
                        (if (nf == 0) 0f else u * .03f + ((nf + 1) / 2) * u * .14f) +
                        (if (p.rows.isEmpty()) 0f else u * .03f + p.rows.size * u * .07f) +
                        (if (p.rest.isEmpty()) 0f else u * .03f + s.h(p.rest, s.serif(u * .04f, Color.Black, true, 1.3f), inner, 4)) +
                        u * .07f
                    return Lay(min(h, wpx * 1.5f))
                }
            }
        }
        else -> {}
    }
    // plain notes, checklists and bare titles: sized from their content, capped at a square
    val pad = u * .03f
    val inner = wpx - 2 * o - 2 * pad
    val bare = b.kind == Kind.Bare
    val size = if (bare) .07f else .05f
    val th = if (b.title.isBlank()) 0f else s.h(b.title, s.title(size), inner, if (bare) Int.MAX_VALUE else 6)
    val isList = b.kind == Kind.Checklist
    val items = if (isList) b.checkItems else emptyList()
    val hasBody = !bare && (if (isList) items.isNotEmpty() else b.body.isNotBlank())
    val base = 2 * o + 2 * pad + th + (if (hasBody && th > 0f) u * .008f else 0f)
    var lineH = u * .052f
    var natural = 0f
    if (hasBody && isList) natural = items.size * lineH
    else if (hasBody) {
        lineH = u * .034f * 1.25f
        natural = s.h(b.body, s.body(), inner)
    }
    var h = base + natural
    var lines = 0
    var rows = if (isList) items.size else 0
    var hidden = 0
    val cap = max(wpx, base + lineH)
    if (h > cap && !bare) {
        h = cap
        val fit = floor((cap - base) / lineH).toInt()
        if (isList) { rows = max(1, fit - 1); hidden = items.size - rows } else lines = max(1, fit)
    }
    return Lay(max(h, base + lineH), lines, rows, hidden)
}

/** Colours the canvas draws with, all derived from the tag's photos. */
class Tones(
    val bg: Color, val onBg: Color,
    val primary: Color, val onPrimary: Color,
    val pc: Color, val onPc: Color,
    val sc: Color, val onSc: Color,
    val tc: Color, val onTc: Color,
    val error: Color,
)

private fun kindIcon(k: Kind): ImageVector = when (k) {
    Kind.Film -> Icons.Rounded.Movie
    Kind.Track -> Icons.Rounded.MusicNote
    Kind.Book -> Icons.Rounded.MenuBook
    Kind.Recipe -> Icons.Rounded.Restaurant
    else -> Icons.Rounded.Link
}

private fun kindLabel(k: Kind) = when (k) { Kind.Film -> "Film"; Kind.Track -> "Track"; Kind.Book -> "Book"; Kind.Recipe -> "Recipe"; else -> "Link" }

/** The face of one card, filling the box it is given. [l] comes from [lay] at the same scale. */
@Composable
fun CardFace(b: Block, l: Lay, s: Scale, t: Tones) {
    val u = s.u
    when {
        b.kind == Kind.Photo -> if (b.path != null) PhotoImage(b.path, 800, false, t.primary, Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize().background(t.pc))
        b.kind in RECORD_KINDS -> RecFace(b, s, t)
        b.kind == Kind.Quote || b.kind == Kind.Stat || b.kind == Kind.Info || b.kind == Kind.Poster -> TplFace(b, l, s, t)
        else -> NoteFace(b, l, s, t)
    }
}

@Composable
private fun NoteFace(b: Block, l: Lay, s: Scale, t: Tones) {
    val u = s.u
    val bare = b.kind == Kind.Bare
    val isNote = b.kind == Kind.Text
    val bg = if (bare) Color.Transparent else if (isNote) t.tc else t.sc
    val fg = if (bare) t.onBg else if (isNote) t.onTc else t.onSc
    Box(Modifier.fillMaxSize().background(bg).padding(s.dp(u * .03f))) {
        Column(Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Top, unbounded = true)) {
            if (b.title.isNotBlank()) Text(b.title, style = s.title(if (bare) .07f else .05f, fg), maxLines = if (bare) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
            if (!bare) {
                if (b.kind == Kind.Checklist) {
                    val items = b.checkItems
                    Spacer(Modifier.height(s.dp(u * .008f)))
                    for (it in items.take(if (l.rows > 0) l.rows else items.size)) {
                        val done = it.startsWith("x ")
                        Row(Modifier.fillMaxWidth().height(s.dp(u * .052f)), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (done) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(s.dp(u * .04f)), tint = fg)
                            Spacer(Modifier.width(s.dp(u * .015f)))
                            com.b72.keep_canvas.ui.ScribbleText(if (done) it.substring(2) else it, done, fg, t.primary, s.body(fg), maxLines = 1)
                        }
                    }
                    if (l.hidden > 0) Text("+${l.hidden} more", style = s.body(fg))
                } else if (b.body.isNotBlank()) {
                    if (b.title.isNotBlank()) Spacer(Modifier.height(s.dp(u * .008f)))
                    Text(b.body, maxLines = if (l.lines == 0) Int.MAX_VALUE else l.lines, overflow = TextOverflow.Ellipsis, style = s.body(fg))
                }
            }
        }
    }
}

@Composable
private fun RecFace(b: Block, s: Scale, t: Tones) {
    val u = s.u
    val has = b.path != null
    val fg = if (has) Color.White else t.onPc
    val sub = b.recSub()
    val rating = b.dbl("rating")?.roundToInt() ?: 0
    Box(Modifier.fillMaxSize().background(if (has) Color.Black else t.pc)) {
        if (has) PhotoImage(b.path!!, 700, false, t.primary, Modifier.fillMaxSize())
        if (has) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.42f to Color.Transparent, 1f to Color.Black.copy(alpha = .78f))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = s.dp(u * .04f), end = s.dp(u * .04f), bottom = s.dp(u * .035f))) {
            Text(b.title.ifEmpty { kindLabel(b.kind) }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = flexStyle(s.sp(u * .06f), wght = 720, lh = 1.05f).copy(color = fg))
            if (sub.isNotEmpty()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = s.sp(u * .035f), color = fg.copy(alpha = .84f)))
        }
        Box(Modifier.align(Alignment.TopStart).padding(s.dp(u * .035f)).clip(CircleShape).background(Color.Black.copy(alpha = .26f)).padding(s.dp(u * .02f))) {
            Icon(kindIcon(b.kind), null, Modifier.size(s.dp(u * .045f)), tint = Color.White)
        }
        if (rating > 0) Row(
            Modifier.align(Alignment.TopEnd).padding(s.dp(u * .035f)).clip(RoundedCornerShape(s.dp(u * .03f))).background(Color.Black.copy(alpha = .3f)).padding(horizontal = s.dp(u * .02f), vertical = s.dp(u * .008f)),
        ) { for (i in 1..rating) Icon(Icons.Rounded.Star, null, Modifier.size(s.dp(u * .04f)), tint = Color(0xFFFFC107)) }
    }
}

@Composable
private fun TplFace(b: Block, l: Lay, s: Scale, t: Tones) {
    val u = s.u
    val (bg, fg) = when (b.kind) {
        Kind.Quote -> t.primary to t.onPrimary
        Kind.Stat -> t.tc to t.onTc
        Kind.Info -> t.sc to t.onSc
        else -> t.onPc to t.pc
    }
    Box(Modifier.fillMaxSize().background(bg).padding(s.dp(u * .035f))) {
        Column(Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Top, unbounded = true)) {
            when (b.kind) {
                Kind.Quote -> {
                    Text("“", style = s.serif(u * .16f, fg.copy(alpha = .6f), false, .7f))
                    Text(b.body, style = s.serif(if (l.fs > 0f) l.fs else u * .055f, fg))
                    if (b.title.isNotEmpty()) Text("— ${b.title}", style = TextStyle(fontSize = s.sp(u * .036f), color = fg.copy(alpha = .8f)), modifier = Modifier.padding(top = s.dp(u * .02f)))
                }
                Kind.Stat -> {
                    Text(b.title.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = s.caps(u * .032f, fg.copy(alpha = .66f)))
                    // the figure shrinks until it fits on one line
                    val inner = s.cw * cardWidth(b, false) - 2 * u * .047f
                    var f = u * .13f
                    var st = s.serif(f, fg, false, 1f).copy(fontWeight = FontWeight.Bold)
                    val wd = s.tm.measure(b.body, style = st, maxLines = 1).size.width
                    if (wd > inner) { f *= inner / wd; st = s.serif(f, fg, false, 1f).copy(fontWeight = FontWeight.Bold) }
                    Text(b.body, maxLines = 1, softWrap = false, style = st, modifier = Modifier.padding(top = s.dp(u * .01f)))
                }
                Kind.Info -> {
                    if (b.title.isNotEmpty()) Box(Modifier.height(s.dp(u * .07f)), contentAlignment = Alignment.CenterStart) { Text(b.title, maxLines = 1, style = s.serif(u * .05f, fg, false)) }
                    val ps = pairs(b.body)
                    for ((lab, v) in ps.take(if (l.rows > 0) l.rows else ps.size)) Column(Modifier.height(s.dp(u * .1f))) {
                        if (lab.isNotEmpty()) Text(lab.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = s.caps(u * .028f, fg.copy(alpha = .6f)))
                        Text(v, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = s.sp(u * .04f), fontWeight = FontWeight.SemiBold, color = fg))
                    }
                    if (l.hidden > 0) Box(Modifier.height(s.dp(u * .1f)), contentAlignment = Alignment.CenterStart) { Text("+${l.hidden} more", style = s.caps(u * .03f, fg.copy(alpha = .66f))) }
                }
                else -> Zine(b, s, t, fg)
            }
        }
    }
}

@Composable
private fun Zine(b: Block, s: Scale, t: Tones, ink: Color) {
    val u = s.u
    val hair = ink.copy(alpha = .3f)
    val p = remember(b.title, b.body) { parsePoster(b.title, b.body) }
    val no = "№ ${(b.title.length * 7 + b.body.length) % 90 + 10}"
    val n = p.facts.size + (if (p.big == null) 0 else 1)
    fun micro(x: String) = x.uppercase()
    Row(Modifier.fillMaxWidth().height(s.dp(u * .045f)), verticalAlignment = Alignment.CenterVertically) {
        Text(no, style = s.caps(u * .026f, ink.copy(alpha = .7f)), modifier = Modifier.weight(1f))
        Text(micro(p.facts.firstOrNull()?.label ?: "Note"), maxLines = 1, style = s.caps(u * .026f, ink.copy(alpha = .7f)))
    }
    HorizontalDivider(color = hair)
    Spacer(Modifier.height(s.dp(u * .02f)))
    if (p.head.isNotEmpty()) {
        Text(p.head, maxLines = 3, overflow = TextOverflow.Ellipsis, style = s.head(u * .095f, ink))
        Spacer(Modifier.height(s.dp(u * .02f)))
    }
    p.big?.let {
        Spacer(Modifier.height(s.dp(u * .03f)))
        Text(micro(it.label), maxLines = 1, style = s.caps(u * .026f, ink.copy(alpha = .7f)))
        Text(it.value, maxLines = 1, softWrap = false, style = s.serif(u * .17f, t.tc, false, 1f).copy(fontWeight = FontWeight.Black), modifier = Modifier.height(s.dp(u * .2f)))
    }
    if (p.facts.isNotEmpty()) {
        Spacer(Modifier.height(s.dp(u * .03f)))
        p.facts.chunked(2).forEachIndexed { r, pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEachIndexed { c, f ->
                    Column(Modifier.weight(1f).height(s.dp(u * .14f)).padding(end = s.dp(u * .02f))) {
                        HorizontalDivider(color = hair)
                        Text("${(r * 2 + c + 1).toString().padStart(2, '0')} · ${f.label.uppercase()}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = s.caps(u * .024f, ink.copy(alpha = .6f)), modifier = Modifier.padding(top = s.dp(u * .012f)))
                        Text(f.value, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = s.sp(u * .04f), lineHeight = s.sp(u * .048f), fontWeight = FontWeight.SemiBold, color = ink))
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (p.rows.isNotEmpty()) {
        Spacer(Modifier.height(s.dp(u * .03f)))
        for ((lab, v) in p.rows) {
            Row(Modifier.fillMaxWidth().height(s.dp(u * .07f - 1f)), verticalAlignment = Alignment.CenterVertically) {
                Text(lab, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = s.sp(u * .03f), color = ink.copy(alpha = .82f)), modifier = Modifier.weight(1f))
                Text(v, maxLines = 1, style = TextStyle(fontSize = s.sp(u * .038f), fontWeight = FontWeight.Bold, color = ink))
            }
            HorizontalDivider(color = hair)
        }
    }
    if (p.rest.isNotEmpty()) {
        Spacer(Modifier.height(s.dp(u * .03f)))
        Text(p.rest, maxLines = 4, overflow = TextOverflow.Ellipsis, style = s.serif(u * .04f, ink.copy(alpha = .86f), true, 1.3f))
    }
    Spacer(Modifier.height(s.dp(u * .03f)))
    Row(Modifier.fillMaxWidth().height(s.dp(u * .04f))) {
        Text("B-72", style = s.caps(u * .026f, ink.copy(alpha = .7f)), modifier = Modifier.weight(1f))
        Text("$n ${if (n == 1) "fact" else "facts"}".uppercase(), style = s.caps(u * .026f, ink.copy(alpha = .7f)))
    }
}
