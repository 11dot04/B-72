package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatIndentIncrease
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A stretch of formatted text: [s, e) in the note's words, [c] one of b i u s hl h1 cur. */
data class Span(val s: Int, val e: Int, val c: String)

fun parseSpans(raw: String?): List<Span> = raw.orEmpty().split(';').mapNotNull { t ->
    val p = t.split(','); if (p.size != 3) null else Span(p[0].toIntOrNull() ?: return@mapNotNull null, p[1].toIntOrNull() ?: return@mapNotNull null, p[2])
}

fun spansToString(l: List<Span>) = l.joinToString(";") { "${it.s},${it.e},${it.c}" }

/** Joins touching spans of one code and drops empty ones. */
private fun tidy(l: List<Span>, len: Int): List<Span> {
    val out = ArrayList<Span>()
    for ((_, group) in l.map { it.copy(s = it.s.coerceIn(0, len), e = it.e.coerceIn(0, len)) }.filter { it.e > it.s }.groupBy { it.c }) {
        var cur: Span? = null
        for (sp in group.sortedBy { it.s }) {
            val c = cur
            if (c != null && sp.s <= c.e) cur = c.copy(e = maxOf(c.e, sp.e)) else { if (c != null) out += c; cur = sp }
        }
        cur?.let { out += it }
    }
    return out.sortedWith(compareBy({ it.s }, { it.c }))
}

/** Moves spans through an edit (found by comparing old and new text); what was just typed takes the [active] formats. */
fun adjustSpans(spans: List<Span>, old: String, new: String, active: Set<String>): List<Span> {
    if (old == new) return spans
    var p = 0
    while (p < old.length && p < new.length && old[p] == new[p]) p++
    var q = 0
    while (q < old.length - p && q < new.length - p && old[old.length - 1 - q] == new[new.length - 1 - q]) q++
    val removedEnd = old.length - q
    val ins = new.length - q - p
    val delta = ins - (removedEnd - p)
    val pure = removedEnd == p
    fun m(x: Int, start: Boolean): Int = when {
        start && pure && x == p && ins > 0 -> x + ins
        x <= p -> x
        x >= removedEnd -> x + delta
        else -> p
    }
    val out = spans.map { Span(m(it.s, true), m(it.e, false), it.c) }.toMutableList()
    if (ins > 0) for (c in active) out += Span(p, p + ins, c)
    return tidy(out, new.length)
}

fun styleOf(code: String, base: Float = 18f): SpanStyle? = when (code) {
    "b" -> SpanStyle(fontWeight = FontWeight.Bold)
    "i" -> SpanStyle(fontStyle = FontStyle.Italic)
    "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
    "s" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    "hl" -> SpanStyle(background = Color(0x66FFC928))
    "h1" -> SpanStyle(fontSize = (base * 1.45f).sp, fontWeight = FontWeight.Bold)
    "cur" -> SpanStyle(fontFamily = FontFamily.Cursive, fontSize = (base * 1.35f).sp)
    else -> null
}

fun styled(text: String, spans: List<Span>, base: Float = 18f): AnnotatedString = buildAnnotatedString {
    append(text)
    for (sp in spans) styleOf(sp.c, base)?.let { addStyle(it, sp.s.coerceIn(0, text.length), sp.e.coerceIn(0, text.length)) }
}

/** The text being edited plus its formatting. [active] are the formats the next typed letter gets. */
class RichState(text: String, spans: List<Span>, var indent: Boolean) {
    var tv by mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    var spans by mutableStateOf(spans)
    var active by mutableStateOf(emptySet<String>())
    var indentOn by mutableStateOf(indent)

    fun edit(v: TextFieldValue) {
        if (v.text != tv.text) { spans = adjustSpans(spans, tv.text, v.text, active); tv = v }
        else { tv = v; if (v.selection.collapsed) active = spans.filter { it.s < v.selection.start && v.selection.start <= it.e }.map { it.c }.toSet() }
    }

    fun has(code: String): Boolean {
        val sel = tv.selection
        return if (sel.collapsed) code in active
        else spans.any { it.c == code && it.s <= sel.min && it.e >= sel.max }
    }

    fun toggle(code: String) {
        val sel = tv.selection
        if (sel.collapsed) { active = if (code in active) active - code else active + code; return }
        spans = if (has(code)) tidy(spans.flatMap { sp ->
            if (sp.c != code || sp.e <= sel.min || sp.s >= sel.max) listOf(sp)
            else listOfNotNull(sp.takeIf { it.s < sel.min }?.copy(e = sel.min), sp.takeIf { it.e > sel.max }?.copy(s = sel.max))
        }, tv.text.length) else tidy(spans + Span(sel.min, sel.max, code), tv.text.length)
    }

    /** Puts a bullet at the start of the line the cursor is on, or takes it off. */
    fun bullet() {
        val t = tv.text; val at = tv.selection.min
        val ls = t.lastIndexOf('\n', at - 1) + 1
        val has = t.startsWith("• ", ls)
        val nt = if (has) t.removeRange(ls, ls + 2) else t.substring(0, ls) + "• " + t.substring(ls)
        val d = if (has) -2 else 2
        spans = adjustSpans(spans, t, nt, emptySet())
        tv = TextFieldValue(nt, TextRange((tv.selection.start + d).coerceIn(0, nt.length), (tv.selection.end + d).coerceIn(0, nt.length)))
    }
}

fun richTransformation(spans: List<Span>, base: Float) = VisualTransformation { t -> TransformedText(styled(t.text, spans, base), OffsetMapping.Identity) }

/** The formatting strip over the keyboard: bold, italic, underline, strike, highlight, headline, script, bullet, first-line indent, and a tick to finish. */
@Composable
fun FormatBar(st: RichState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    @Composable fun btn(icon: ImageVector?, label: String, on: Boolean, f: () -> Unit) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(if (on) cs.onTertiary else Color.Transparent).clickable(onClickLabel = label, onClick = f),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Icon(icon, label, tint = if (on) cs.tertiary else cs.onTertiary)
            else Text(label, color = if (on) cs.tertiary else cs.onTertiary, style = MaterialTheme.typography.titleMedium)
        }
    }
    Row(
        modifier.clip(RoundedCornerShape(28.dp)).background(cs.tertiary).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        btn(Icons.Rounded.FormatBold, "Bold", st.has("b")) { st.toggle("b") }
        btn(Icons.Rounded.FormatItalic, "Italic", st.has("i")) { st.toggle("i") }
        btn(Icons.Rounded.FormatUnderlined, "Underline", st.has("u")) { st.toggle("u") }
        btn(Icons.Rounded.FormatStrikethrough, "Strike", st.has("s")) { st.toggle("s") }
        btn(Icons.Rounded.Highlight, "Highlight", st.has("hl")) { st.toggle("hl") }
        btn(null, "H", st.has("h1")) { st.toggle("h1") }
        btn(null, "𝒜", st.has("cur")) { st.toggle("cur") }
        btn(Icons.Rounded.FormatListBulleted, "Bullet", false) { st.bullet() }
        btn(Icons.Rounded.FormatIndentIncrease, "Indent first lines", st.indentOn) { st.indentOn = !st.indentOn }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(42.dp).clip(CircleShape).background(cs.onTertiary).clickable(onClickLabel = "Done", onClick = onDone), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, "Done", tint = cs.tertiary)
        }
    }
}
