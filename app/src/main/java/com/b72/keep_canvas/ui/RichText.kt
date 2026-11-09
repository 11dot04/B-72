package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.FormatIndentDecrease
import androidx.compose.material.icons.rounded.FormatIndentIncrease
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.KeyboardTab
import androidx.compose.material.icons.rounded.Subscript
import androidx.compose.material.icons.rounded.Superscript
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
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
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
    "h2" -> SpanStyle(fontSize = (base * 1.2f).sp, fontWeight = FontWeight.SemiBold)
    "cur" -> SpanStyle(fontFamily = Script, fontSize = (base * 1.45f).sp)
    "sup" -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = (base * .72f).sp)
    "sub" -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = (base * .72f).sp)
    "trk" -> SpanStyle(letterSpacing = 1.6.sp, fontSize = (base * .84f).sp, fontWeight = FontWeight.Medium)
    else -> null
}

/** Codes that format a whole line (quote, indent, alignment...) rather than a stretch of letters. */
fun isPara(code: String) = code.length in 2..3 && code[0] == 'p'

private val NUM = Regex("^(\\d{1,3})\\. ")

/** Width, in the text's own size, that a list line's marker takes: the wrapped lines hang under the words, not under the marker. */
private fun hangOf(line: String, base: Float): Float = when {
    line.startsWith("\u2022 ") -> base * .66f
    else -> NUM.find(line)?.let { (it.groupValues[1].length * .56f + .55f) * base } ?: 0f
}

private fun paraStyle(codes: Set<String>, hang: Float, base: Float): ParagraphStyle? {
    val lvl = codes.firstNotNullOfOrNull { c -> if (c.startsWith("pi")) c.drop(2).toIntOrNull() else null } ?: 0
    var left = lvl * 26f
    if ("pq" in codes) left += 18f
    val pull = "pp" in codes
    var first = left
    var rest = left + hang
    if ("ph" in codes) rest += 24f
    if ("pf" in codes) first += 28f
    val align = when {
        pull || "pc" in codes -> TextAlign.Center
        "pr" in codes -> TextAlign.End
        "pj" in codes -> TextAlign.Justify
        else -> TextAlign.Unspecified
    }
    if (first == 0f && rest == 0f && align == TextAlign.Unspecified && !pull) return null
    return ParagraphStyle(textIndent = TextIndent(first.sp, rest.sp), textAlign = align, lineHeight = if (pull) (base * 1.5f * 1.22f).sp else TextUnit.Unspecified)
}

/** Letter formats first, then one paragraph style per line that has a code, a list marker or both. */
fun applyFormats(b: AnnotatedString.Builder, text: String, spans: List<Span>, base: Float) {
    for (sp in spans) if (!isPara(sp.c)) styleOf(sp.c, base)?.let { b.addStyle(it, sp.s.coerceIn(0, text.length), sp.e.coerceIn(0, text.length)) }
    var pos = 0
    while (pos < text.length) {
        val nl = text.indexOf('\n', pos)
        val end = if (nl < 0) text.length else nl + 1
        val lineEnd = if (nl < 0) text.length else nl
        val codes = spans.filter { isPara(it.c) && it.s < end && it.e > pos }.map { it.c }.toSet()
        paraStyle(codes, hangOf(text.substring(pos, lineEnd), base), base)?.let { b.addStyle(it, pos, end) }
        if (lineEnd > pos) {
            if ("pq" in codes) b.addStyle(SpanStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = (base * 1.12f).sp), pos, lineEnd)
            if ("pp" in codes) b.addStyle(SpanStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = (base * 1.5f).sp), pos, lineEnd)
        }
        pos = end
    }
}

fun styled(text: String, spans: List<Span>, base: Float = 18f): AnnotatedString = buildAnnotatedString {
    append(text)
    applyFormats(this, text, spans, base)
}

/** The text being edited plus its formatting. [active] are the formats the next typed letter gets. */
class RichState(text: String, spans: List<Span>, var indent: Boolean) {
    var tv by mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    var spans by mutableStateOf(spans)
    var active by mutableStateOf(emptySet<String>())
    var indentOn by mutableStateOf(indent)

    fun edit(v: TextFieldValue) {
        if (v.text != tv.text) {
            val old = tv.text
            spans = adjustSpans(spans, old, v.text, active); tv = v
            if (v.text.length == old.length + 1) continueList()
            if (v.text.count { it == '\n' } != old.count { it == '\n' }) renumber()
        } else { tv = v; if (v.selection.collapsed) active = spans.filter { it.s < v.selection.start && v.selection.start <= it.e }.map { it.c }.toSet() }
    }

    fun has(code: String): Boolean {
        if (isPara(code)) return paraOn(code)
        val sel = tv.selection
        return if (sel.collapsed) code in active
        else spans.any { it.c == code && it.s <= sel.min && it.e >= sel.max }
    }

    private fun cut(code: String, a: Int, b: Int, from: List<Span> = spans) = from.flatMap { sp ->
        if (sp.c != code || sp.e <= a || sp.s >= b) listOf(sp)
        else listOfNotNull(sp.takeIf { it.s < a }?.copy(e = a), sp.takeIf { it.e > b }?.copy(s = b))
    }

    fun toggle(code: String) {
        val sel = tv.selection
        val other = EXCLUSIVE[code]
        if (sel.collapsed) { active = if (code in active) active - code else (active + code).let { a -> if (other != null) a - other else a }; return }
        var base = spans
        if (other != null) base = cut(other, sel.min, sel.max, base)
        spans = if (has(code)) tidy(cut(code, sel.min, sel.max, base), tv.text.length) else tidy(base + Span(sel.min, sel.max, code), tv.text.length)
    }

    // ---- lines -----------------------------------------------------------------------------------------------------------------------

    private fun lineEnd(t: String, ls: Int) = t.indexOf('\n', ls).let { if (it < 0) t.length else it }

    /** Start offsets of the lines the selection touches. */
    private fun selectedLines(): List<Int> {
        val t = tv.text; val a = tv.selection.min; val b = tv.selection.max
        val first = t.lastIndexOf('\n', a - 1) + 1
        val out = arrayListOf(first)
        var nl = t.indexOf('\n', first)
        val stop = if (b > a && t[b - 1] == '\n') b - 1 else b
        while (nl in 0 until stop) { out += nl + 1; nl = t.indexOf('\n', nl + 1) }
        return out
    }

    /** One edit made by the app (a list marker, a rule): the formatting is carried through it and the cursor follows. */
    private fun replaceRange(at: Int, remove: Int, insert: String, caretAfter: Boolean = false) {
        val t = tv.text
        val nt = t.substring(0, at) + insert + t.substring(at + remove)
        spans = adjustSpans(spans, t, nt, emptySet())
        fun m(x: Int, end: Boolean) = when {
            x >= at + remove && !(x == at && remove == 0 && !caretAfter) -> x + insert.length - remove
            x > at -> at + insert.length
            else -> x
        }
        tv = TextFieldValue(nt, TextRange(m(tv.selection.start, false).coerceIn(0, nt.length), m(tv.selection.end, true).coerceIn(0, nt.length)))
    }

    private fun markerLen(line: String) = when {
        line.startsWith("\u2022 ") -> 2
        else -> NUM.find(line)?.value?.length ?: 0
    }

    /** A bullet, or a number, at the start of every selected line; a second tap takes it off. */
    fun list(numbered: Boolean) {
        val starts = selectedLines()
        val t0 = tv.text
        val want = { ls: Int -> val l = t0.substring(ls, lineEnd(t0, ls)); if (numbered) NUM.containsMatchIn(l) else l.startsWith("\u2022 ") }
        val off = starts.all(want)
        for (ls in starts.reversed()) {
            val t = tv.text
            val l = t.substring(ls, lineEnd(t, ls))
            replaceRange(ls, markerLen(l), if (off) "" else if (numbered) "1. " else "\u2022 ")
        }
        renumber()
    }

    fun bullet() = list(false)

    /** After Enter on a list line the next line starts with the next marker; Enter on an empty marker ends the list. */
    private fun continueList() {
        val t = tv.text; val caret = tv.selection.start
        if (!tv.selection.collapsed || caret < 1 || t[caret - 1] != '\n') return
        val ps = t.lastIndexOf('\n', caret - 2) + 1
        val prev = t.substring(ps, caret - 1)
        val n = NUM.find(prev)
        when {
            prev == "\u2022 " || (n != null && prev == n.value) -> replaceRange(ps, caret - ps, "")
            prev.startsWith("\u2022 ") -> replaceRange(caret, 0, "\u2022 ", caretAfter = true)
            n != null -> replaceRange(caret, 0, "${n.groupValues[1].toInt() + 1}. ", caretAfter = true)
        }
    }

    /** Numbered runs count on from their first line, so a deleted or inserted line never leaves a gap. */
    private fun renumber() {
        var pos = 0; var prev = 0
        while (pos <= tv.text.length) {
            val t = tv.text
            val end = lineEnd(t, pos)
            val m = NUM.find(t.substring(pos, end))
            if (m != null) {
                val n = if (prev > 0) prev + 1 else m.groupValues[1].toInt()
                if (m.groupValues[1].toInt() != n) replaceRange(pos, m.value.length, "$n. ")
                prev = n
            } else prev = 0
            pos = lineEnd(tv.text, pos) + 1
        }
    }

    // ---- paragraph formats -----------------------------------------------------------------------------------------------------------

    private fun covers(code: String, ls: Int, t: String): Boolean {
        val le = lineEnd(t, ls) + 1
        return spans.any { it.c == code && it.s < le && it.e > ls }
    }

    private fun paraOn(code: String): Boolean {
        val t = tv.text
        val starts = selectedLines()
        return if (starts.size == 1 && lineEnd(t, starts[0]) == starts[0]) code in active else starts.all { covers(code, it, t) }
    }

    private fun apply(code: String, on: Boolean, group: List<String> = emptyList()) {
        val t = tv.text
        val starts = selectedLines()
        val a = starts.first(); val b = minOf(lineEnd(t, starts.last()) + 1, t.length)
        if (b <= a) { active = if (on) (active - group.toSet()) + code else active - code - group.toSet(); return }
        var l = spans
        for (g in group) l = cut(g, a, b, l)
        l = cut(code, a, b, l)
        spans = tidy(if (on) l + Span(a, b, code) else l, t.length)
    }

    /** Switches a line format on for the selected lines, or off when they all have it. [group] are formats it replaces (alignments). */
    fun para(code: String, group: List<String> = emptyList()) = apply(code, !paraOn(code), group)

    fun align(): String = when { paraOn("pc") -> "pc"; paraOn("pr") -> "pr"; paraOn("pj") -> "pj"; else -> "" }

    fun cycleAlign() {
        val g = listOf("pc", "pr", "pj")
        when (align()) {
            "" -> apply("pc", true, g)
            "pc" -> apply("pr", true, g)
            "pr" -> apply("pj", true, g)
            else -> apply("pj", false, g)
        }
    }

    fun indentLevel(): Int = (1..4).lastOrNull { paraOn("pi$it") } ?: 0

    /** Pushes the selected lines in from the left, a step at a time (up to four). */
    fun indentBy(d: Int) {
        val n = (indentLevel() + d).coerceIn(0, 4)
        val all = (1..4).map { "pi$it" }
        if (n == 0) { for (c in all) apply(c, false) } else apply("pi$n", true, all)
    }

    /** A centred rule of asterisks on its own line, the way a book marks a break in the text. */
    fun rule() {
        val t = tv.text
        val at = tv.selection.max
        val ls = t.lastIndexOf('\n', at - 1) + 1
        val lead = if (at == ls) "" else "\n"
        val ins = lead + "* * *\n"
        replaceRange(at, 0, ins, caretAfter = true)
        val t2 = tv.text
        val rs = at + lead.length
        spans = tidy(spans + Span(rs, minOf(rs + 6, t2.length), "pc"), t2.length)
    }

    companion object { private val EXCLUSIVE = mapOf("sup" to "sub", "sub" to "sup", "h1" to "h2", "h2" to "h1") }
}

fun richTransformation(spans: List<Span>, base: Float) = VisualTransformation { t -> TransformedText(styled(t.text, spans, base), OffsetMapping.Identity) }

/** The formatting strip over the keyboard: bold, italic, underline, strike, highlight, headline, script, bullet, first-line indent, and a tick to finish. */
@Composable
fun FormatBar(st: RichState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    @Composable fun btn(icon: ImageVector?, label: String, on: Boolean, script: Boolean = false, f: () -> Unit) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(if (on) cs.onTertiary else Color.Transparent).clickable(onClickLabel = label, onClick = f),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Icon(icon, label, tint = if (on) cs.tertiary else cs.onTertiary)
            else Text(label, color = if (on) cs.tertiary else cs.onTertiary, style = if (script) androidx.compose.ui.text.TextStyle(fontFamily = Script, fontSize = 26.sp) else MaterialTheme.typography.titleMedium)
        }
    }
    @Composable fun gap() { Box(Modifier.padding(vertical = 4.dp).height(1.dp).width(22.dp).background(cs.onTertiary.copy(alpha = .28f))) }
    // a floating toolbar that stands on the right edge, beside the words, so the whole set fits without sliding sideways
    Column(
        modifier.width(54.dp).clip(RoundedCornerShape(28.dp)).background(cs.tertiary).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(cs.onTertiary).clickable(onClickLabel = "Done", onClick = onDone), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, "Done", tint = cs.tertiary)
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            btn(Icons.Rounded.FormatBold, "Bold", st.has("b")) { st.toggle("b") }
            btn(Icons.Rounded.FormatItalic, "Italic", st.has("i")) { st.toggle("i") }
            btn(Icons.Rounded.FormatUnderlined, "Underline", st.has("u")) { st.toggle("u") }
            btn(Icons.Rounded.FormatStrikethrough, "Strike", st.has("s")) { st.toggle("s") }
            gap()
            btn(Icons.Rounded.Highlight, "Highlight", st.has("hl")) { st.toggle("hl") }
            btn(null, "H", st.has("h1")) { st.toggle("h1") }
            btn(null, "h", st.has("h2")) { st.toggle("h2") }
            btn(null, "A", st.has("cur"), script = true) { st.toggle("cur") }
            btn(Icons.Rounded.Superscript, "Superscript", st.has("sup")) { st.toggle("sup") }
            btn(Icons.Rounded.Subscript, "Subscript", st.has("sub")) { st.toggle("sub") }
            btn(null, "AA", st.has("trk")) { st.toggle("trk") }
            gap()
            btn(Icons.Rounded.FormatListBulleted, "Bullets", false) { st.list(false) }
            btn(Icons.Rounded.FormatListNumbered, "Numbered list", false) { st.list(true) }
            btn(Icons.Rounded.FormatQuote, "Quote", st.has("pq")) { st.para("pq", listOf("pp")) }
            btn(null, "\u201C", st.has("pp")) { st.para("pp", listOf("pq")) }
            gap()
            btn(Icons.Rounded.FormatIndentDecrease, "Indent less", false) { st.indentBy(-1) }
            btn(Icons.Rounded.FormatIndentIncrease, "Indent more", st.indentLevel() > 0) { st.indentBy(1) }
            btn(Icons.Rounded.KeyboardTab, "First-line indent", st.indentOn) { st.indentOn = !st.indentOn }
            btn(
                when (st.align()) { "pc" -> Icons.Rounded.FormatAlignCenter; "pr" -> Icons.Rounded.FormatAlignRight; "pj" -> Icons.Rounded.FormatAlignJustify; else -> Icons.Rounded.FormatAlignLeft },
                "Alignment", st.align() != "",
            ) { st.cycleAlign() }
            btn(Icons.Rounded.HorizontalRule, "Break", false) { st.rule() }
        }
    }
}
