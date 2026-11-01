package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Fact
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.pairs
import com.b72.keep_canvas.data.posterFor

private fun caps(c: Color, s: Int = 11) = TextStyle(fontSize = s.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, color = c)
private fun serif(s: Int, c: Color, italic: Boolean = true, lh: Float = 1.25f) =
    TextStyle(fontFamily = Serif, fontSize = s.sp, lineHeight = (s * lh).sp, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, color = c)

/** Quote, figure, details and smart-layout (zine) cards on the note page. */
@Composable
fun TplCard(b: Block, noteTitle: String, pal: NotePalette, modifier: Modifier = Modifier) {
    val fg = pal.fg
    val named = b.title.isNotEmpty() && b.title != noteTitle
    when (b.kind) {
        Kind.Quote -> {
            val ink = pal.bg
            val size = when { b.body.length < 70 -> 34; b.body.length < 160 -> 28; b.body.length < 320 -> 23; else -> 19 }
            Column(modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(fg).padding(24.dp)) {
                Text("“", style = serif(72, ink.copy(alpha = .6f), false, .7f))
                Text(b.body, style = serif(size, ink))
                if (named) Text("— ${b.title}", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = .8f), modifier = Modifier.padding(top = 14.dp))
            }
        }
        Kind.Stat -> Column(modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(pal.primary.copy(alpha = .2f)).padding(24.dp)) {
            if (named) Text(b.title.uppercase(), style = caps(fg.copy(alpha = .7f), 12))
            Text(b.body, style = flexStyle(64.sp, wght = 820, wdth = 110f, rond = 30f, lh = 1f), color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
        Kind.Info -> Column(modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(fg.copy(alpha = .08f)).padding(22.dp)) {
            if (named) Text(b.title, style = serif(28, fg, false), modifier = Modifier.padding(bottom = 8.dp))
            for ((l, v) in pairs(b.body)) Column(Modifier.padding(vertical = 7.dp)) {
                if (l.isNotEmpty()) Text(l.uppercase(), style = caps(fg.copy(alpha = .6f)))
                Text(v, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = fg)
            }
        }
        else -> {
            val p = posterFor(if (b.title == noteTitle) "" else b.title, b)
            when {
                p.rows.size >= 2 -> Receipt(p, noteTitle, modifier)
                p.facts.size + (if (p.big != null) 1 else 0) >= 3 -> Rail(p, noteTitle, pal, modifier)
                else -> Zine(b, noteTitle, pal, modifier)
            }
        }
    }
}

/** A till receipt: the title across the top, small facts, priced lines with dashed rules, a total. */
@Composable
private fun Receipt(p: com.b72.keep_canvas.data.Poster, noteTitle: String, modifier: Modifier) {
    val paper = Color(0xFFFBF7EE)
    val ink = Color(0xFF1B1713)
    val mono = FontFamily.Monospace
    @Composable fun rule() = androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(1.dp).padding(vertical = 0.dp)) {
        drawLine(ink.copy(alpha = .55f), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(paper).padding(horizontal = 22.dp, vertical = 24.dp)) {
        Text(p.head.ifEmpty { noteTitle }.uppercase(), style = TextStyle(fontFamily = mono, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ink), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        for (f in p.facts.take(3)) Text("${f.label}: ${f.value}", style = TextStyle(fontFamily = mono, fontSize = 12.sp, color = ink.copy(alpha = .7f)), modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(14.dp)); rule(); Spacer(Modifier.height(10.dp))
        for ((l, v) in p.rows) Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(l, style = TextStyle(fontFamily = mono, fontSize = 14.sp, color = ink), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(v, style = TextStyle(fontFamily = mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink))
        }
        Spacer(Modifier.height(8.dp)); rule()
        p.big?.let { Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("TOTAL", style = TextStyle(fontFamily = mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ink), modifier = Modifier.weight(1f))
            Text(it.value, style = TextStyle(fontFamily = mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ink))
        } }
        Spacer(Modifier.height(14.dp)); rule()
        Text("THANK YOU", style = TextStyle(fontFamily = mono, fontSize = 12.sp, letterSpacing = 2.sp, color = ink.copy(alpha = .7f)), modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Labels ride the left edge in small type; the values stand off-centre in large type beside them. */
@Composable
private fun Rail(p: com.b72.keep_canvas.data.Poster, noteTitle: String, pal: NotePalette, modifier: Modifier) {
    val ink = pal.fg
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
            Text("Details", style = flexStyle(22.sp, wght = 300, lh = 1f), color = ink.copy(alpha = .6f), modifier = Modifier.weight(.32f))
            Text(p.head.ifEmpty { noteTitle }, style = flexStyle(34.sp, wght = 700, wdth = 100f, lh = 1f), color = ink, modifier = Modifier.weight(.68f))
        }
        p.big?.let { Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.Bottom) {
            Text(it.label, style = TextStyle(fontSize = 13.sp, color = ink.copy(alpha = .6f)), modifier = Modifier.weight(.32f))
            Text(it.value, style = flexStyle(48.sp, wght = 800, wdth = 108f, lh = 1f), color = pal.primary, modifier = Modifier.weight(.68f))
        } }
        for (f in p.facts) Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
            Text(f.label, style = TextStyle(fontSize = 13.sp, color = ink.copy(alpha = .6f)), modifier = Modifier.weight(.32f))
            Text(f.value, style = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp, color = ink), modifier = Modifier.weight(.68f))
        }
        if (p.rest.isNotBlank()) Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text("About", style = TextStyle(fontSize = 13.sp, color = ink.copy(alpha = .6f)), modifier = Modifier.weight(.32f))
            Text(p.rest, style = serif(17, ink.copy(alpha = .88f), true, 1.3f), modifier = Modifier.weight(.68f))
        }
    }
}

@Composable
private fun Zine(b: Block, noteTitle: String, pal: NotePalette, modifier: Modifier) {
    val ink = pal.bg; val bg = pal.fg
    val hair = ink.copy(alpha = .3f)
    val p = posterFor(if (b.title == noteTitle) "" else b.title, b)
    val n = p.facts.size + (if (p.big == null) 0 else 1)
    val no = "№ ${(b.title.length * 7 + b.body.length) % 90 + 10}"
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(bg).padding(22.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(no, style = caps(ink.copy(alpha = .7f)), modifier = Modifier.weight(1f))
            Text((p.facts.firstOrNull()?.label ?: "Note").uppercase(), style = caps(ink.copy(alpha = .7f)))
        }
        HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 14.dp), color = hair)
        if (p.head.isNotEmpty()) Text(p.head, style = flexStyle(36.sp, wght = 820, wdth = 118f, lh = 1f), color = ink)
        p.big?.let {
            Text(it.label.uppercase(), style = caps(ink.copy(alpha = .7f)), modifier = Modifier.padding(top = 20.dp))
            val size = when { it.value.length <= 10 -> 64; it.value.length <= 18 -> 44; it.value.length <= 32 -> 32; else -> 24 }
            Text(it.value, style = TextStyle(fontFamily = Serif, fontSize = size.sp, lineHeight = (size + 2).sp, color = pal.primary))
        }
        if (p.facts.isNotEmpty()) Spacer(Modifier.height(14.dp))
        p.facts.chunked(2).forEachIndexed { r, pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEachIndexed { c, f -> ZCell(f, r * 2 + c, ink, hair, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (p.rows.isNotEmpty()) Column(Modifier.padding(top = 14.dp)) {
            for ((l, v) in p.rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = ink.copy(alpha = .85f)), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(v, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink))
                }
                HorizontalDivider(color = hair)
            }
        }
        if (p.rest.isNotEmpty()) Text(p.rest, style = serif(17, ink.copy(alpha = .88f), true, 1.3f), modifier = Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Text("B-72", style = caps(ink.copy(alpha = .7f)), modifier = Modifier.weight(1f))
            Text("$n ${if (n == 1) "fact" else "facts"}".uppercase(), style = caps(ink.copy(alpha = .7f)))
        }
    }
}

@Composable
private fun ZCell(f: Fact, i: Int, ink: Color, hair: Color, modifier: Modifier) {
    Column(modifier.padding(end = 10.dp, bottom = 8.dp)) {
        HorizontalDivider(color = hair)
        Text("${(i + 1).toString().padStart(2, '0')} · ${f.label.uppercase()}", style = caps(ink.copy(alpha = .6f), 10), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(f.value, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, color = ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
