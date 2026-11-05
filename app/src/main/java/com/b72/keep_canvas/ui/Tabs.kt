package com.b72.keep_canvas.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one tab row every screen uses. The chosen tab is set at normal width and the rest are pulled in on the width axis;
 * how far they are pulled is worked out from the room there is, so every tab is always on screen and nothing scrolls.
 */
@Composable
fun FitTabs(labels: List<String>, counts: List<String?>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val m = rememberTextMeasurer()
    val d = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val avail = constraints.maxWidth.toFloat()
        val gap = with(d) { 14.dp.toPx() }
        // (type size, width of the tabs that are not chosen) that makes the row fit
        val plan = remember(labels, counts, selected, avail, d.density) {
            val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            fun total(size: Float, other: Float): Float {
                var t = gap * (labels.size - 1)
                labels.forEachIndexed { i, l ->
                    val st = if (i == selected) rflexStyle(size.sp, wght = 650, wdth = 100f, lh = 1f) else rflexStyle(size.sp, wght = 450, wdth = other, lh = 1f)
                    t += m.measure(l, st, softWrap = false, maxLines = 1).size.width
                    counts.getOrNull(i)?.let { c -> t += m.measure(c, mono, softWrap = false, maxLines = 1).size.width + with(d) { 2.dp.toPx() } }
                }
                return t
            }
            var size = 22f
            var other = 52f
            var found = false
            while (!found && size >= 13f) {
                var o = 52f
                while (o >= 25f) { if (total(size, o) <= avail) { other = o; found = true; break }; o -= 3f }
                if (!found) size -= 1f
            }
            if (!found) { size = 13f; other = 25f }
            size to other
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            labels.forEachIndexed { i, l ->
                Row(Modifier.clickable(onClickLabel = l) { onSelect(i) }, verticalAlignment = Alignment.Top) {
                    val wd by animateFloatAsState(if (i == selected) 100f else plan.second, spring(.9f, 300f), label = "wdth")
                    Text(l, maxLines = 1, softWrap = false, style = rflexStyle(plan.first.sp, wght = if (i == selected) 650 else 450, wdth = wd, lh = 1f), color = if (i == selected) cs.onSurface else cs.onSurface.copy(alpha = .4f))
                    counts.getOrNull(i)?.let { c ->
                        Text(c, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = if (i == selected) cs.primary else cs.onSurface.copy(alpha = .35f), modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
        }
    }
}
