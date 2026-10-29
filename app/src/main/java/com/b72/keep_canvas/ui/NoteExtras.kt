@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lightbulb
import com.b72.keep_canvas.data.Assistant
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.CUT_KEYS
import com.b72.keep_canvas.data.tripText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import androidx.compose.ui.graphics.luminance
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

/**
 * A tertiary panel with a tall, narrow title spread across its width and a row of rounded tiles that scrolls sideways.
 * The tiles alternate in height and sit staggered, the last one peeking in from the edge. (A plain LazyRow, not the carousel
 * the photos above already use.)
 */
@Composable
fun RelatedMosaic(related: List<Note>, pal: NotePalette, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    if (related.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    // tertiary container suits a page tinted from a photo; on a page in the plain system colours (and toned-down themes)
    // it is too close to the page, so the panel steps up to the solid tertiary instead
    val close = kotlin.math.abs(pal.bg.luminance() - cs.tertiaryContainer.luminance()) < .08f
    val panel = if (close) cs.tertiary else cs.tertiaryContainer
    val ink = if (close) cs.onTertiary else cs.onTertiaryContainer
    val head = flexStyle(40.sp, wght = 360, wdth = 52f, lh = 1f)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(panel).padding(vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RELATED", style = head, color = ink)
            Text("NOTES", style = head, color = ink)
        }
        androidx.compose.foundation.lazy.LazyRow(
            Modifier.padding(top = 16.dp).height(196.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(related, key = { _, n -> n.id }) { i, n ->
                val tall = i % 2 == 0
                RelatedTile(n, if (tall) 146.dp else 118.dp, if (tall) 196.dp else 150.dp, if (tall) 0.dp else 46.dp, ink, onOpen)
            }
        }
    }
}

@Composable
private fun RelatedTile(r: Note, w: Dp, h: Dp, top: Dp, ink: Color, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val path = r.cover?.path
    Box(
        Modifier.padding(top = top).width(w).height(h).clip(RoundedCornerShape(26.dp)).background(ink.copy(alpha = .16f))
            .clickable(onClickLabel = "Open note") { onOpen(r.id) },
    ) {
        path?.let { PhotoImage(it, 500, false, cs.tertiary, Modifier.fillMaxSize()) }
        if (path != null) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.45f to Color.Transparent, 1f to Color.Black.copy(alpha = .7f))))
        Text(
            r.title.ifBlank { r.snippet(50) }, maxLines = 3, overflow = TextOverflow.Ellipsis,
            style = if (r.title.isBlank()) androidx.compose.ui.text.TextStyle(fontFamily = Serif, fontSize = 14.sp, lineHeight = 18.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) else flexStyle(17.sp, wght = 700, rond = 40f, lh = 1.05f),
            color = if (path != null) Color.White else ink,
            modifier = Modifier.align(if (path != null) Alignment.BottomStart else Alignment.TopStart).padding(12.dp),
        )
    }
}

// ---------------------------------------------------------------- toolbar pieces

/** "Edit" with a drop-down of the smart things you can do with the note: its layout and extraction, and an AI brainstorm. */
@Composable
fun EditSplit(container: Color, content: Color, smart: Boolean, onEdit: () -> Unit, onSmart: () -> Unit, onBrainstorm: () -> Unit) {
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
                    Icon(Icons.Rounded.KeyboardArrowDown, "Smart tools for this note", Modifier.size(SplitButtonDefaults.TrailingIconSize).graphicsLayer { rotationZ = turn })
                }
            },
        )
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(28.dp)) {
            DropdownMenuItem(
                { Column { Text(if (smart) "Plain layout" else "Smart layout"); Text(if (smart) "Show the text as written" else "Pull out the facts and the one that matters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                { open = false; onSmart() }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
            )
            DropdownMenuItem(
                { Column { Text("Brainstorm with AI"); Text("Next steps, questions, connections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                { open = false; onBrainstorm() }, leadingIcon = { Icon(Icons.Rounded.Lightbulb, null) },
            )
        }
    }
}

/** The three-dot menu: when the note was made and last changed, what is in it, and (kept apart, in red) delete. */
@Composable
fun DetailsMenu(note: Note, expanded: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit, onTag: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm")
    fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fmt)
    fun ago(ms: Long) = DateUtils.getRelativeTimeSpanString(ms, now, DateUtils.MINUTE_IN_MILLIS).toString()
    val born = note.id.toLongOrNull()?.div(1000)
    val words = note.blocks.sumOf { it.wordCount() }
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
            val acts = buildList {
                add(Triple(Icons.Rounded.Share, "Share as text", { onDismiss(); onShare() }))
                for (t in note.tags) add(Triple(Icons.Rounded.Dashboard, "Open the $t canvas", { onDismiss(); onTag(t) }))
            }
            acts.forEachIndexed { i, (icon, label, go) ->
                val big = 22.dp; val small = 6.dp
                Surface(
                    shape = RoundedCornerShape(topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small, bottomStart = if (i == acts.lastIndex) big else small, bottomEnd = if (i == acts.lastIndex) big else small),
                    color = cs.secondaryContainer, onClick = go,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = cs.onSecondaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.titleSmall, color = cs.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.height(3.dp))
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

/** Everything written in a block, including the fields a recipe, film or book was filled in with from the web. */
private fun Block.wordCount(): Int {
    fun n(t: String) = t.split(Regex("""\s+""")).count { it.isNotBlank() }
    fun JsonElement.w(): Int = when (this) {
        is JsonPrimitive -> if (isString) n(content) else 0
        is JsonArray -> sumOf { it.w() }
        is JsonObject -> values.sumOf { it.w() }
        else -> 0
    }
    var c = n(title) + n(body)
    if (body.isBlank()) c += items.sumOf { n(it) }
    for ((k, v) in data) if (k !in CUT_KEYS && k != "ocr") c += v.w()
    return c
}

// ---------------------------------------------------------------- brainstorm

private val ASKS = listOf("Next steps", "Questions to explore", "Connect the dots", "Say it shorter")

/** A conversation with the on-device assistant about one note. What it writes can be added to the note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainstormSheet(note: Note, onDismiss: () -> Unit, onAppend: (String) -> Unit, onSetup: () -> Unit) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var ask by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun run(q: String) {
        if (busy || q.isBlank()) return
        busy = true; answer = ""
        val text = note.blocks.joinToString("\n") { it.tripText() }.take(3000)
        scope.launch {
            val prompt = "You are a thoughtful thinking partner. Here is a personal note:\n\"\"\"\n$text\n\"\"\"\nTask: $q\nReply in plain text, under 120 words, no preamble."
            answer = Assistant.generate(ctx, prompt) ?: "The assistant could not answer. Try again."
            busy = false
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Brainstorm", style = flexStyle(32.sp, wght = 720, wdth = 108f, rond = 40f), color = cs.onSurface)
            Text(note.title.ifBlank { "This note" }, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!Assistant.installed) {
                Text("This runs on the on-device assistant, which is not installed yet. Nothing leaves your phone.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { onDismiss(); onSetup() }) { Text("Set up the assistant") }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (a in ASKS) AssistChip(onClick = { run(a) }, label = { Text(a) }, enabled = !busy)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(ask, { ask = it }, Modifier.weight(1f), placeholder = { Text("Or ask anything about it") }, singleLine = true, shape = RoundedCornerShape(24.dp))
                    IconButton(onClick = { run(ask) }, enabled = !busy && ask.isNotBlank()) { Icon(Icons.AutoMirrored.Rounded.Send, "Ask") }
                }
                if (busy) LoadingIndicator(Modifier.size(48.dp).align(Alignment.CenterHorizontally))
                if (answer.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(28.dp), color = cs.tertiaryContainer) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(answer, style = TextStyle(fontFamily = Serif, fontSize = 18.sp, lineHeight = 25.sp, color = cs.onTertiaryContainer), modifier = Modifier.padding(20.dp))
                        }
                    }
                    FilledTonalButton(onClick = { onAppend(answer); onDismiss() }) { Text("Add to this note") }
                }
            }
        }
    }
}
