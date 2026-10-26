@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.AutoAwesome
import com.b72.keep_canvas.data.cutPath
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedScroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.dbl
import com.b72.keep_canvas.data.seedOf
import com.b72.keep_canvas.data.str
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.offset

private val TECH = setOf("cut", "cutV", "cutBox", "cutAspect", "cutFrac", "cutOk", "cutWhy", "pop", "rating", "desc")

/**
 * The reading view. The title flexes out to full width when the page opens and shrinks into the top bar as
 * you scroll; one photo with one clear subject sits in a Material shape and the subject breaks out at the top.
 */
@Composable
fun NotePage(
    note: Note, shared: SharedCtx, onBack: () -> Unit, onEdit: () -> Unit, onTag: (String) -> Unit = {},
    onDelete: () -> Unit = {}, onToggle: (blockId: String, index: Int) -> Unit = { _, _ -> },
    onSetData: (blockId: String, key: String, value: Int) -> Unit = { _, _, _ -> },
    onOpenNote: (String) -> Unit = {}, related: List<Note> = emptyList(),
    onRemind: (Long) -> Unit = {}, allNotes: List<Note> = emptyList(),
) {
    val pal = rememberNotePalette(note)
    val density = LocalDensity.current
    var tint by remember { mutableStateOf(true) }

    // The width axis runs from condensed to the fit. The layout is measured once; only the typeface step changes.
    val flex = remember { Animatable(0f) }
    val slow = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(note.id) { flex.animateTo(1f, slow) }

    val ls = rememberLazyListState()
    // The big title scrolls away like any other content. The bar's own title takes over once the big one has slid under the
    // bar; where the big title really is gets measured, so the two are never on screen together.
    val barBottomPx = with(density) { (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp).toPx() }
    val fadePx = with(density) { 24.dp.toPx() }
    var titleBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val barP by remember {
        derivedStateOf {
            if (ls.firstVisibleItemIndex > 0) 1f
            else ((barBottomPx + fadePx - titleBottom) / fadePx).coerceIn(0f, 1f)
        }
    }

    val title = note.title
    val photos = note.photos
    val hero = if (photos.size == 1 && canPopOut(photos[0])) photos[0] else null
    val texts = note.blocks.filter { it.kind == Kind.Text && it.body.isNotEmpty() }.map { it.body }
    val list = note.blocks.filter { it.kind == Kind.Checklist }.flatMap { it.checkItems }
    val created = note.id.toLongOrNull()?.let { Instant.ofEpochMilli(it / 1000).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy")) } ?: "-"
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.bg).nestedScroll(scrollBehavior.nestedScrollConnection)) {
        val w = maxWidth - 32.dp
        val wPx = with(density) { w.toPx() }
        val lay = if (title.isNotEmpty()) rememberTitleLay(title, wPx, note.id) else null

        LazyColumn(
            state = ls,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 72.dp, bottom = bottom + 112.dp),
        ) {
            if (lay != null) item {
                FitTitle(
                    title, pal.fg, note.id,
                    modifier = Modifier.shared(shared, "title-${note.id}", text = true).onGloballyPositioned { titleBottom = it.boundsInWindow().bottom },
                    flex = { flex.value },
                )
            }
            if (photos.isNotEmpty()) item {
                if (hero != null) {
                    // The shape's frame is sized from the subject; the area is trimmed to the frame and the subject's head
                    // rides up over the title's last line.
                    val hPx = wPx * 1.2f
                    val frame = com.b72.keep_canvas.ui.popFrame(hero, wPx, hPx)
                    val s = com.b72.keep_canvas.ui.subjectIn(hero, wPx, hPx)
                    val last = lay?.heights?.lastOrNull() ?: 0f
                    val lift = if (lay == null) (s?.top ?: frame.top) * .6f else ((s?.top ?: frame.top) + last * .12f).coerceIn(0f, hPx * .5f)
                    val bottomPx = max(frame.bottom, s?.bottom ?: 0f) + with(density) { 12.dp.toPx() }
                    Box(Modifier.fillMaxWidth().height(with(density) { (bottomPx - lift).toDp() })) {
                        PopOut(
                            block = hero, fill = pal.primary,
                            tint = if (tint) pal.primary.copy(alpha = .59f) else null,
                            shapeSeed = seedOf(hero.id),
                            modifier = Modifier
                                .fillMaxWidth()
                                .requiredHeight(with(density) { hPx.toDp() })
                                .align(Alignment.TopCenter)
                                .offset { androidx.compose.ui.unit.IntOffset(0, -lift.roundToInt()) }
                                .shared(shared, "img-${note.id}")
                                .clickable(onClickLabel = "Switch the colour tint") { tint = !tint },
                        )
                    }
                } else if (photos.size == 1) {
                    val b = photos[0]
                    Spacer(Modifier.height(20.dp))
                    PhotoImage(
                        b.path!!, 1200, tint, pal.primary,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio((1 / b.aspect).toFloat().coerceIn(.7f, 1.6f))
                            .shared(shared, "img-${note.id}")
                            .clip(RoundedCornerShape(28.dp))
                            .clickable { tint = !tint },
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                    HorizontalMultiBrowseCarousel(
                        state = rememberCarouselState { photos.size },
                        preferredItemWidth = 260.dp,
                        itemSpacing = 8.dp,
                        modifier = Modifier.fillMaxWidth().height(w * .7f).shared(shared, "img-${note.id}"),
                    ) { i ->
                        PhotoImage(photos[i].path!!, 1000, tint, pal.primary, Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge).clickable { tint = !tint })
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
            if (created != "-") item {
                Text(
                    "Created $created",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = pal.fg.copy(alpha = .6f),
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }
            if (note.remind > System.currentTimeMillis()) item {
                val whenText = Instant.ofEpochMilli(note.remind).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
                androidx.compose.material3.AssistChip(
                    onClick = { onRemind(0) },
                    label = { Text("Reminder · $whenText · tap to clear") },
                    leadingIcon = { Icon(Icons.Rounded.Notifications, null, Modifier.size(18.dp)) },
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }
            if (photos.size == 1 && photos[0].cutPath() != null) item {
                val pb = photos[0]
                val on = canPopOut(pb)
                Column(Modifier.padding(bottom = 18.dp)) {
                    androidx.compose.material3.ToggleButton(checked = on, onCheckedChange = { onSetData(pb.id, "pop", if (it) 1 else 0) }) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pop out")
                    }
                    val why = popWhy(pb)
                    if (!on && why.isNotEmpty() && pb.dbl("pop") == null) Text(
                        "Left flat automatically: $why",
                        style = MaterialTheme.typography.labelSmall, color = pal.fg.copy(alpha = .6f), modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                }
            }
            for (b in note.blocks) {
                if (b.kind in com.b72.keep_canvas.data.RECORD_KINDS) item { RecSection(b, title, pal, w) }
                else if (!hasContent(b, title)) Unit
                else if (b.kind in setOf(Kind.Quote, Kind.Stat, Kind.Info, Kind.Poster)) item { TplCard(b, title, pal, Modifier.padding(bottom = 14.dp)) }
                else if (b.kind in CARD_KINDS) item { BlockCard(b, title, pal, Modifier.padding(bottom = 14.dp)) }
            }
            for (t in texts) item {
                val linked = rememberLinked(t, allNotes, pal.primary, onOpenNote)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(linked, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp), color = pal.fg, modifier = Modifier.padding(bottom = 12.dp))
                }
            }
            for (b in note.blocks) if (b.kind == Kind.Checklist) b.checkItems.forEachIndexed { i, s ->
                item(key = "${b.id}-$i") {
                    val done = s.startsWith("x ")
                    // the whole row is the target (48dp high) and reads as one checkbox to a screen reader
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle(b.id, i) })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (done) pal.primary else pal.fg)
                        Spacer(Modifier.width(12.dp))
                        ScribbleText(if (done) s.substring(2) else s, done, pal.fg, pal.primary, MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), Modifier.weight(1f))
                    }
                }
            }
            if (related.isNotEmpty()) item {
                Column(Modifier.padding(top = 28.dp)) {
                    Text("Related", style = flexStyle(26.sp, wght = 700, rond = 40f), color = pal.fg, modifier = Modifier.padding(bottom = 12.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(related, key = { it.id }) { r ->
                            Box(
                                Modifier.width(150.dp).height(180.dp).clip(RoundedCornerShape(24.dp)).background(pal.primary.copy(alpha = .16f))
                                    .clickable(onClickLabel = "Open ${r.title}") { onOpenNote(r.id) },
                            ) {
                                r.cover?.path?.let { PhotoImage(it, 500, false, pal.primary, Modifier.fillMaxSize()) }
                                val onPic = r.cover?.path != null
                                if (onPic) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(.4f to Color.Transparent, 1f to Color.Black.copy(alpha = .7f))))
                                Text(
                                    r.title.ifBlank { "Untitled" }, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    style = flexStyle(18.sp, wght = 700, rond = 40f, lh = 1.05f), color = if (onPic) Color.White else pal.fg,
                                    modifier = Modifier.align(if (onPic) Alignment.BottomStart else Alignment.TopStart).padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Material's own top app bar: transparent over the big title, the page colour once content scrolls under it
        TopAppBar(
            title = {
                Text(
                    title,
                    style = flexStyle(22.sp, wght = 700, rond = 40f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = barP; translationY = (1f - barP) * 10.dp.toPx() },
                )
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                note.tags.firstOrNull()?.let { t ->
                    Text(
                        t, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 8.dp).minimumInteractiveComponentSize().clip(RoundedCornerShape(20.dp)).background(pal.primary.copy(alpha = .2f)).clickable { onTag(t) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent, scrolledContainerColor = pal.bg,
                navigationIconContentColor = pal.fg, titleContentColor = pal.fg, actionIconContentColor = pal.fg,
            ),
            scrollBehavior = scrollBehavior,
        )

        // M3 Expressive floating toolbar: the main action is wide and labelled, the rest are icons
        val ctx = LocalContext.current
        var askDelete by remember { mutableStateOf(false) }
        var pickDate by remember { mutableStateOf(false) }
        var pickTime by remember { mutableStateOf<java.time.LocalDate?>(null) }
        val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
        val barOpen by remember { derivedStateOf { ls.firstVisibleItemIndex == 0 || !ls.lastScrolledForward } }
        HorizontalFloatingToolbar(
            expanded = barOpen,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.tertiary,
                toolbarContentColor = MaterialTheme.colorScheme.onTertiary,
            ),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            leadingContent = {
                IconButton(onClick = {
                    val text = buildString {
                        append(title)
                        texts.forEach { append("\n\n").append(it) }
                        list.forEach { append("\n• ").append(it.removePrefix("x ")) }
                    }.trim()
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, title) }
                    ctx.startActivity(Intent.createChooser(send, null))
                }) { Icon(Icons.Rounded.Share, "Share note") }
            },
            trailingContent = {
                IconButton(onClick = { pickDate = true }) { Icon(Icons.Rounded.NotificationAdd, "Remind me about this note") }
                note.tags.firstOrNull()?.let { t -> IconButton(onClick = { onTag(t) }) { Icon(Icons.Rounded.Dashboard, "Open the $t canvas") } }
                IconButton(
                    onClick = { askDelete = true },
                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Icon(Icons.Rounded.Delete, "Delete note") }
            },
            content = {
                Button(
                    onClick = onEdit,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onTertiary, contentColor = MaterialTheme.colorScheme.tertiary,
                    ),
                ) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit")
                }
            },
        )
        if (pickDate) {
            val dp = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis() + 86_400_000L)
            androidx.compose.material3.DatePickerDialog(
                onDismissRequest = { pickDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        dp.selectedDateMillis?.let { pickTime = Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }
                        pickDate = false
                    }) { Text("Next") }
                },
                dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
            ) { androidx.compose.material3.DatePicker(dp) }
        }
        pickTime?.let { day ->
            val tp = androidx.compose.material3.rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = android.text.format.DateFormat.is24HourFormat(ctx))
            AlertDialog(
                onDismissRequest = { pickTime = null },
                title = { Text("Remind me at") },
                text = { androidx.compose.material3.TimePicker(tp) },
                confirmButton = {
                    TextButton(onClick = {
                        val at = day.atTime(tp.hour, tp.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        if (android.os.Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        onRemind(at)
                        pickTime = null
                    }) { Text("Set reminder") }
                },
                dismissButton = { TextButton(onClick = { pickTime = null }) { Text("Cancel") } },
            )
        }
        if (askDelete) AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("It disappears from your collection and from every canvas. You can undo right after.") },
            confirmButton = { TextButton(onClick = { askDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun PhotoImage(path: String, width: Int, tint: Boolean, tintColor: Color, modifier: Modifier) {
    val img by produceState<ImageBitmap?>(null, path) { value = Images.load(path, width) }
    img?.let {
        Image(
            it, null, modifier,
            contentScale = ContentScale.Crop,
            colorFilter = if (tint) ColorFilter.tint(tintColor.copy(alpha = .66f), BlendMode.Color) else null,
        )
    } ?: Box(modifier.background(tintColor.copy(alpha = .15f)))
}

/** A card with nothing to show (no picture, no words other than the note's own title) is not drawn at all. */
private fun hasContent(b: Block, noteTitle: String): Boolean {
    if (b.path != null) return true
    if (b.body.isNotBlank()) return true
    if (b.title.isNotBlank() && b.title != noteTitle) return true
    return b.data.any { (k, v) -> k !in TECH && (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.isNotBlank() == true }
}

private val CARD_KINDS = setOf(Kind.Quote, Kind.Stat, Kind.Info, Kind.Poster, Kind.Bare)

/** Plain cards for the template and record blocks. Phase 3 replaces these with the full zine layouts. */
@Composable
private fun BlockCard(b: Block, noteTitle: String, pal: NotePalette, modifier: Modifier) {
    val ratio = when (b.kind) { Kind.Film, Kind.Book -> 1.5f; Kind.Track -> 1f; else -> 1.15f }
    val hasImage = b.path != null && b.kind in setOf(Kind.Film, Kind.Book, Kind.Track, Kind.Recipe, Kind.Link)
    val rating = b.dbl("rating")?.roundToInt() ?: 0
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.primary.copy(alpha = .14f)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hasImage) PhotoImage(b.path!!, 900, false, pal.primary, Modifier.fillMaxWidth().aspectRatio(1 / ratio).clip(RoundedCornerShape(20.dp)))
        if (b.kind == Kind.Quote) {
            Text("“${b.body}”", style = androidx.compose.ui.text.TextStyle(fontFamily = Serif, fontSize = 30.sp, lineHeight = 34.sp), color = pal.fg)
            if (b.title.isNotEmpty() && b.title != noteTitle) Text("— ${b.title}", style = MaterialTheme.typography.labelLarge, color = pal.fg.copy(alpha = .7f))
        } else {
            if (b.title.isNotEmpty() && b.title != noteTitle) Text(b.title, style = flexStyle(26.sp, wght = 700, rond = 30f), color = pal.fg)
            if (b.body.isNotEmpty()) Text(b.body, style = MaterialTheme.typography.bodyLarge, color = pal.fg)
        }
        b.str("desc")?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = pal.fg.copy(alpha = .85f)) }
        for ((k, v) in b.data) {
            if (k in TECH) continue
            val s = (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: continue
            Row {
                Text(k.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = pal.fg.copy(alpha = .6f), modifier = Modifier.weight(.35f))
                Text(s, style = MaterialTheme.typography.bodyMedium, color = pal.fg, modifier = Modifier.weight(.65f))
            }
        }
        if (rating > 0) Row { for (i in 1..5) Icon(if (i <= rating) Icons.Rounded.Star else Icons.Rounded.StarOutline, null, tint = pal.fg) }
    }
}
