package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Note
import java.util.Calendar

/** Everything the page says, counted once from the notes. */
private class Tally(
    val notes: Int, val words: Int, val photos: Int, val tags: Int, val days: Int, val thisMonth: Int,
    val months: List<Pair<String, Int>>, val kinds: List<Pair<String, Int>>, val weekdays: List<Int>,
    val topTags: List<Pair<String, Int>>, val longest: Pair<String, Int>?,
)

private fun wordsIn(s: String) = s.split(Regex("\\s+")).count { it.isNotBlank() }

private fun tally(all: List<Note>): Tally {
    val now = Calendar.getInstance()
    val mk = { c: Calendar -> c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH) }
    val nowKey = mk(now)
    val perMonth = IntArray(12)
    val perDay = IntArray(7)
    var words = 0; var photos = 0; var thisMonth = 0
    var oldest = Long.MAX_VALUE
    var longest: Pair<String, Int>? = null
    val kinds = linkedMapOf("Text" to 0, "Photos" to 0, "Checklists" to 0, "Quotes" to 0, "Links" to 0, "Films" to 0, "Books" to 0, "Tracks" to 0, "Recipes" to 0, "Recordings" to 0)
    val tagCount = HashMap<String, Int>()
    for (n in all) {
        val millis = n.id.toLongOrNull()?.div(1000)
        if (millis != null && millis > 0) {
            oldest = minOf(oldest, millis)
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            val back = nowKey - mk(c)
            if (back in 0..11) perMonth[11 - back]++
            if (back == 0) thisMonth++
            perDay[(c.get(Calendar.DAY_OF_WEEK) + 5) % 7]++   // Monday first
        }
        var w = 0
        for (b in n.blocks) {
            w += wordsIn(b.body) + wordsIn(b.title)
            val key = when (b.kind) {
                Kind.Text -> if (b.body.isNotBlank()) "Text" else null
                Kind.Photo -> "Photos"; Kind.Checklist -> "Checklists"; Kind.Quote -> "Quotes"; Kind.Link -> "Links"
                Kind.Film -> "Films"; Kind.Book -> "Books"; Kind.Track -> "Tracks"; Kind.Recipe -> "Recipes"; Kind.Audio -> "Recordings"
                else -> null
            }
            if (key != null) kinds[key] = (kinds[key] ?: 0) + 1
            if (b.kind == Kind.Photo) photos++
        }
        words += w
        if (w > (longest?.second ?: 0)) longest = (n.title.ifBlank { n.snippet(40) }.ifBlank { "Untitled" }) to w
        for (t in n.tags) tagCount[t] = (tagCount[t] ?: 0) + 1
    }
    val names = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    val months = (0 until 12).map { i ->
        val c = Calendar.getInstance().apply { add(Calendar.MONTH, i - 11) }
        names[c.get(Calendar.MONTH)] to perMonth[i]
    }
    val days = if (oldest == Long.MAX_VALUE) 0 else ((System.currentTimeMillis() - oldest) / 86_400_000L).toInt()
    return Tally(
        all.size, words, photos, tagCount.size, days, thisMonth, months,
        kinds.filter { it.value > 0 }.toList().sortedByDescending { it.second }, perDay.toList(),
        tagCount.toList().sortedByDescending { it.second }.take(6), longest,
    )
}

/**
 * The statistics page, set the Swiss way: a hard grid, one typeface, big figures, thin rules, flat bars with square ends and a single accent
 * that marks the largest value in each chart. Nothing is decoration; every mark is a count.
 */
@Composable
fun StatsPage(notes: List<Note>) {
    val cs = MaterialTheme.colorScheme
    val t = remember(notes) { tally(notes) }
    val ink = cs.onSurface
    val rule = Modifier.fillMaxWidth().height(1.dp).background(ink)
    val label = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
    val margin = 24.dp

    @Composable fun Heading(no: String, title: String) {
        Column(Modifier.padding(top = 44.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(ink))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title.uppercase(), style = label, color = ink)
                Text(no, style = label, color = cs.onSurfaceVariant)
            }
        }
    }

    @Composable fun Figure(value: String, name: String, modifier: Modifier = Modifier) {
        Column(modifier) {
            Text(value, style = flexStyle(44.sp, wght = 700, wdth = 90f, lh = 1f), color = ink)
            Text(name.uppercase(), style = label, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }

    // a flat column chart: the tallest bar takes the accent, the rest the ink at low weight
    @Composable fun Columns(values: List<Pair<String, Int>>, height: Dp) {
        val top = (values.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        val peak = values.indexOfFirst { it.second == top }
        Row(Modifier.fillMaxWidth().height(height + 22.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            values.forEachIndexed { i, (name, v) ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (v > 0) Text("$v", style = label, color = if (i == peak) cs.primary else cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                    Box(Modifier.fillMaxWidth().height((height.value * v / top).dp.coerceAtLeast(2.dp)).background(if (i == peak && v > 0) cs.primary else ink.copy(alpha = .8f)))
                    Text(name, style = label, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
                }
            }
        }
    }

    @Composable fun Ruled(rows: List<Pair<String, Int>>) {
        val top = (rows.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        Column(Modifier.padding(top = 12.dp)) {
            rows.forEachIndexed { i, (name, v) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text("%02d".format(i + 1), style = label, color = cs.onSurfaceVariant, modifier = Modifier.width(36.dp))
                        Text(name, style = MaterialTheme.typography.titleMedium, color = ink, modifier = Modifier.weight(1f))
                        Text("$v", style = flexStyle(22.sp, wght = 650, wdth = 90f, lh = 1f), color = if (i == 0) cs.primary else ink)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 36.dp)) {
                        Box(Modifier.weight(v.toFloat().coerceAtLeast(.01f) / top).height(3.dp).background(if (i == 0) cs.primary else ink.copy(alpha = .8f)))
                        val rest = 1f - v.toFloat() / top
                        if (rest > .001f) Box(Modifier.weight(rest).height(1.dp).align(Alignment.CenterVertically).background(ink.copy(alpha = .25f)))
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(cs.surface)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = margin).padding(top = 64.dp, bottom = 48.dp)) {
            Text("B-72", style = label, color = cs.onSurfaceVariant)
            Text("Statistics", style = flexStyle(56.sp, wght = 800, wdth = 85f, lh = .95f), color = ink, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(36.dp))
            Box(rule)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1.35f)) {
                    Text("%,d".format(t.notes), style = flexStyle(112.sp, wght = 800, wdth = 82f, lh = .9f), color = cs.primary)
                    Text("NOTES AND LIBRARY ITEMS", style = label, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Figure("%,d".format(t.words), "Words")
                    Figure("%,d".format(t.photos), "Photos")
                }
            }

            Heading("01", "Over time")
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Figure("${t.thisMonth}", "This month", Modifier.weight(1f))
                Figure("%,d".format(t.days), "Days since the first", Modifier.weight(1f))
                Figure("${t.tags}", "Tags", Modifier.weight(1f))
            }
            Text("NOTES MADE, LAST TWELVE MONTHS", style = label, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 30.dp))
            Columns(t.months, 120.dp)

            Heading("02", "What is in them")
            Ruled(t.kinds)

            Heading("03", "Which days")
            val names = listOf("M", "T", "W", "T", "F", "S", "S")
            val full = listOf("Mondays", "Tuesdays", "Wednesdays", "Thursdays", "Fridays", "Saturdays", "Sundays")
            val busiest = t.weekdays.indices.maxByOrNull { t.weekdays[it] }
            if (busiest != null && t.weekdays[busiest] > 0) Text(full[busiest], style = flexStyle(40.sp, wght = 700, wdth = 90f, lh = 1f), color = ink, modifier = Modifier.padding(top = 16.dp))
            Text("THE DAY MOST NOTES BEGIN ON", style = label, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Columns(t.weekdays.mapIndexed { i, v -> names[i] to v }, 84.dp)

            if (t.topTags.isNotEmpty()) {
                Heading("04", "Tags")
                Ruled(t.topTags.map { "#${it.first}" to it.second })
            }
            t.longest?.let { (name, w) ->
                Heading(if (t.topTags.isNotEmpty()) "05" else "04", "The longest")
                Text(name, style = flexStyle(30.sp, wght = 700, wdth = 92f, lh = 1.05f), color = ink, modifier = Modifier.padding(top = 16.dp), maxLines = 3)
                Text("%,d WORDS".format(w), style = label, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            Box(Modifier.padding(top = 56.dp).fillMaxWidth().height(1.dp).background(ink))
            Text("Counted on this phone. Nothing here leaves it.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
