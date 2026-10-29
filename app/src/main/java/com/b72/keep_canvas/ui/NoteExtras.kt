@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.b72.keep_canvas.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.Phase
import com.b72.keep_canvas.data.Trip
import com.b72.keep_canvas.data.TripLink
import com.b72.keep_canvas.data.links
import com.b72.keep_canvas.data.phase
import com.b72.keep_canvas.data.route
import com.b72.keep_canvas.data.suggestedTags
import com.b72.keep_canvas.data.summary
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val H = 3_600_000L

private fun caps(c: Color, s: Int = 11) = TextStyle(fontSize = s.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, color = c)

// ---------------------------------------------------------------- trips

@Composable
private fun rememberNow(): Long = produceState(System.currentTimeMillis()) {
    while (true) { delay(30_000); value = System.currentTimeMillis() }
}.value

fun openTripLink(ctx: Context, l: TripLink) {
    l.copy?.let {
        runCatching { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PNR", it)) }
        Toast.makeText(ctx, "PNR copied. Paste it on the page.", Toast.LENGTH_SHORT).show()
    }
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun stamp(ms: Long, withTime: Boolean): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (withTime) "EEE d MMM · HH:mm" else "EEE d MMM"))

fun tripStatus(t: Trip, now: Long): String {
    val d = t.dep - now
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    return when (t.phase(now)) {
        Phase.Running -> if (t.arr > now) "On its way · arrives ${Instant.ofEpochMilli(t.arr).atZone(ZoneId.systemDefault()).format(clock)}" else "Should be arriving about now"
        Phase.Done -> "Journey over"
        else -> when {
            !t.timeKnown && d < 24 * H -> "Leaves today"
            d < H -> "Leaves in ${(d / 60_000).coerceAtLeast(1)} min"
            d < 24 * H -> "Leaves in ${d / H} h ${(d % H) / 60_000} min"
            d < 48 * H -> "Leaves tomorrow"
            else -> "Leaves in ${d / (24 * H)} days"
        }
    }
}

private fun phaseChip(p: Phase) = when (p) {
    Phase.Early -> "UPCOMING"
    Phase.Soon -> "CHECK PNR"
    Phase.Boarding -> "LEAVING SOON"
    Phase.Running -> "ON THE WAY"
    Phase.Done -> "DONE"
}

/** A ticket: where, when, what to look at right now. The buttons change with the time left (PNR early, live tracking once under way). */
@Composable
fun TripCard(t: Trip, bg: Color, ink: Color, accent: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val now = rememberNow()
    val phase = t.phase(now)
    val hair = ink.copy(alpha = .3f)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open this trip", onClick = onClick) else Modifier)
            .padding(22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOf(t.mode.uppercase(), t.number, t.name.uppercase()).filter { it.isNotBlank() }.joinToString(" · "),
                style = caps(ink.copy(alpha = .7f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                phaseChip(phase), style = caps(bg, 10),
                modifier = Modifier.clip(CircleShape).background(accent).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 14.dp), color = hair)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(t.fromCode.ifBlank { t.from }.ifBlank { "?" }, style = TextStyle(fontFamily = Serif, fontSize = 44.sp, lineHeight = 46.sp, color = ink), maxLines = 1)
                if (t.from.isNotBlank() && t.fromCode.isNotBlank()) Text(t.from, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.padding(horizontal = 10.dp, vertical = 12.dp).size(26.dp), tint = accent)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(t.toCode.ifBlank { t.to }.ifBlank { "?" }, style = TextStyle(fontFamily = Serif, fontSize = 44.sp, lineHeight = 46.sp, color = ink), maxLines = 1)
                if (t.to.isNotBlank() && t.toCode.isNotBlank()) Text(t.to, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("DEPARTS", style = caps(ink.copy(alpha = .6f), 10))
                Text(stamp(t.dep, t.timeKnown), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink))
            }
            if (t.arr > 0) Column(Modifier.weight(1f)) {
                Text("ARRIVES", style = caps(ink.copy(alpha = .6f), 10))
                Text(stamp(t.arr, true), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink))
            }
        }
        val bits = listOf("PNR" to t.pnr, "CLASS" to t.cls, "SEAT" to t.seat).filter { it.second.isNotBlank() }
        if (bits.isNotEmpty()) FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for ((l, v) in bits) Column {
                Text(l, style = caps(ink.copy(alpha = .6f), 10))
                Text(v, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink))
            }
        }
        Text(
            tripStatus(t, now),
            style = TextStyle(fontFamily = Serif, fontSize = 20.sp, fontStyle = FontStyle.Italic, color = ink.copy(alpha = .9f)),
            modifier = Modifier.padding(top = 14.dp),
        )
        val links = t.links(phase)
        if (links.isNotEmpty()) FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEachIndexed { i, l ->
                FilledTonalButton(
                    onClick = { openTripLink(ctx, l) },
                    colors = if (i == 0) ButtonDefaults.filledTonalButtonColors(containerColor = accent, contentColor = bg)
                    else ButtonDefaults.filledTonalButtonColors(containerColor = ink.copy(alpha = .16f), contentColor = ink),
                ) { Text(l.label) }
            }
        }
    }
}

// ---------------------------------------------------------------- assistant strip

/** The assistant's one-line summary and its suggested tags (tap one to add it). */
@Composable
fun AiStrip(note: Note, fg: Color, accent: Color, onAddTag: (String) -> Unit, modifier: Modifier = Modifier) {
    val sum = note.summary()
    val tags = note.suggestedTags()
    if (sum.isEmpty() && tags.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        if (sum.isNotEmpty()) Text(sum, style = TextStyle(fontFamily = Serif, fontSize = 20.sp, lineHeight = 26.sp, fontStyle = FontStyle.Italic, color = fg.copy(alpha = .85f)), modifier = Modifier.padding(bottom = 10.dp))
        if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in tags) AssistChip(onClick = { onAddTag(t) }, label = { Text("+ $t") })
        }
    }
}

// ---------------------------------------------------------------- related, laid out like a "Top albums" panel

/** A panel with the title spread across its width and a staggered mosaic of rounded tiles (no scrolling row: the page already has a carousel). */
@Composable
fun RelatedMosaic(related: List<Note>, pal: NotePalette, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val shown = related.take(5)
    if (shown.isEmpty()) return
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(pal.fg.copy(alpha = .09f)).padding(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RELATED", style = flexStyle(22.sp, wght = 860, wdth = 130f), color = pal.fg)
            Text("NOTES", style = flexStyle(22.sp, wght = 860, wdth = 130f), color = pal.fg)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1.12f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shown.filterIndexed { i, _ -> i % 2 == 0 }.forEachIndexed { j, n -> RelatedTile(n, if (j % 2 == 0) 220.dp else 150.dp, pal, onOpen) }
            }
            Column(Modifier.weight(1f).padding(top = 34.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shown.filterIndexed { i, _ -> i % 2 == 1 }.forEachIndexed { j, n -> RelatedTile(n, if (j % 2 == 0) 150.dp else 220.dp, pal, onOpen) }
            }
        }
    }
}

@Composable
private fun RelatedTile(r: Note, h: Dp, pal: NotePalette, onOpen: (String) -> Unit) {
    val path = r.cover?.path
    Box(
        Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(28.dp)).background(pal.primary.copy(alpha = .2f))
            .clickable(onClickLabel = "Open ${r.title}") { onOpen(r.id) },
    ) {
        path?.let { PhotoImage(it, 500, false, pal.primary, Modifier.fillMaxSize()) }
        if (path != null) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.4f to Color.Transparent, 1f to Color.Black.copy(alpha = .7f))))
        Text(
            r.title.ifBlank { "Untitled" }, maxLines = 3, overflow = TextOverflow.Ellipsis,
            style = flexStyle(18.sp, wght = 700, rond = 40f, lh = 1.05f), color = if (path != null) Color.White else pal.fg,
            modifier = Modifier.align(if (path != null) Alignment.BottomStart else Alignment.TopStart).padding(14.dp),
        )
    }
}

// ---------------------------------------------------------------- toolbar pieces

/** "Edit" with a drop-down of what else you can do next: the primary action stays one tap, the rest is one more. */
@Composable
fun EditSplit(container: Color, content: Color, tags: List<String>, onEdit: () -> Unit, onTag: (String) -> Unit, onShare: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec<Float>(), label = "chevron")
    Box {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(onClick = onEdit, colors = colors) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(SplitButtonDefaults.LeadingIconSize))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit")
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(checked = open, onCheckedChange = { open = it }, colors = colors) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "More things to do with this note", Modifier.size(SplitButtonDefaults.TrailingIconSize).graphicsLayer { rotationZ = turn })
                }
            },
        )
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(28.dp)) {
            DropdownMenuItem({ Text("Share as text") }, { open = false; onShare() }, leadingIcon = { Icon(Icons.Rounded.Share, null) })
            for (t in tags) DropdownMenuItem({ Text("Open the $t canvas") }, { open = false; onTag(t) }, leadingIcon = { Icon(Icons.Rounded.Dashboard, null) })
        }
    }
}

/** The three-dot menu: when the note was made and last changed, what is in it, and (kept apart, in red) delete. */
@Composable
fun DetailsMenu(note: Note, expanded: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm")
    fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fmt)
    fun ago(ms: Long) = DateUtils.getRelativeTimeSpanString(ms, now, DateUtils.MINUTE_IN_MILLIS).toString()
    val born = note.id.toLongOrNull()?.div(1000)
    val words = note.blocks.sumOf { b -> (b.title + " " + b.body).split(Regex("""\s+""")).count { it.isNotBlank() } }
    val items = note.blocks.filter { it.kind == Kind.Checklist }.flatMap { it.checkItems }
    val facts = buildList {
        if (words > 0) add("$words words")
        note.photos.size.takeIf { it > 0 }?.let { add("$it photo${if (it == 1) "" else "s"}") }
        if (items.isNotEmpty()) add("${items.count { it.startsWith("x ") }} of ${items.size} done")
    }
    val rows = buildList {
        if (born != null) add(Triple(Icons.Rounded.Event, "Created", at(born) to ago(born)))
        add(Triple(Icons.Rounded.EditCalendar, "Last edited", if (note.edited > 0) at(note.edited) to ago(note.edited) else "Not changed since it was made" to ""))
        if (facts.isNotEmpty()) add(Triple(Icons.Rounded.Notes, "Inside", facts.joinToString(" · ") to ""))
    }
    DropdownMenu(expanded, onDismiss, shape = RoundedCornerShape(32.dp), containerColor = cs.surfaceContainerHigh, shadowElevation = 8.dp) {
        Column(Modifier.widthIn(min = 280.dp).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ABOUT THIS NOTE", style = caps(cs.onSurfaceVariant, 11), modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp))
            rows.forEachIndexed { i, (icon, label, value) ->
                val big = 22.dp; val small = 6.dp
                val shape = RoundedCornerShape(
                    topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
                    bottomStart = if (i == rows.lastIndex) big else small, bottomEnd = if (i == rows.lastIndex) big else small,
                )
                Surface(shape = shape, color = cs.surfaceContainerHighest) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(20.dp), tint = cs.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Text(value.first, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                            if (value.second.isNotEmpty()) Text(value.second, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Surface(shape = RoundedCornerShape(22.dp), color = cs.errorContainer, onClick = { onDismiss(); onDelete() }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = cs.onErrorContainer)
                    Spacer(Modifier.width(12.dp))
                    Text("Delete note", style = MaterialTheme.typography.titleSmall, color = cs.onErrorContainer)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
