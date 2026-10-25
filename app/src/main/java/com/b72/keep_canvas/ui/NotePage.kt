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
import kotlin.math.roundToInt

private val TECH = setOf("cut", "cutV", "cutBox", "cutAspect", "cutFrac", "rating", "desc")

/**
 * The reading view. The title flexes out to full width when the page opens and shrinks into the top bar as
 * you scroll; one photo with one clear subject sits in a Material shape and the subject breaks out at the top.
 */
@Composable
fun NotePage(
    note: Note, shared: SharedCtx, onBack: () -> Unit, onEdit: () -> Unit, onTag: (String) -> Unit = {},
    onDelete: () -> Unit = {}, onToggle: (blockId: String, index: Int) -> Unit = { _, _ -> },
) {
    val pal = rememberNotePalette(note)
    val density = LocalDensity.current
    var tint by remember { mutableStateOf(true) }

    // The width axis runs from condensed to the fit. The layout is measured once; only the typeface step changes.
    val flex = remember { Animatable(0f) }
    val slow = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(note.id) { flex.animateTo(1f, slow) }

    val ls = rememberLazyListState()
    val shrinkPx = with(density) { 110.dp.toPx() }
    val p by remember { derivedStateOf { if (ls.firstVisibleItemIndex > 0) 1f else (ls.firstVisibleItemScrollOffset / shrinkPx).coerceIn(0f, 1f) } }
    val barP by remember { derivedStateOf { if (ls.firstVisibleItemIndex > 0) 1f else ((ls.firstVisibleItemScrollOffset - with(density) { 40.dp.toPx() }) / with(density) { 70.dp.toPx() }).coerceIn(0f, 1f) } }

    val title = note.title
    val photos = note.photos
    val hero = if (photos.size == 1 && canPopOut(photos[0])) photos[0] else null
    val texts = note.blocks.filter { it.kind == Kind.Text && it.body.isNotEmpty() }.map { it.body }
    val list = note.blocks.filter { it.kind == Kind.Checklist }.flatMap { it.checkItems }
    val created = note.id.toLongOrNull()?.let { Instant.ofEpochMilli(it / 1000).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy")) } ?: "-"
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.bg)) {
        val w = maxWidth - 32.dp
        val wPx = with(density) { w.toPx() }
        val lay = if (title.isNotEmpty()) rememberTitleLay(title, wPx, note.id) else null

        LazyColumn(
            state = ls,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 64.dp, bottom = bottom + 112.dp),
        ) {
            if (lay != null) item {
                FitTitle(
                    title, pal.fg, note.id,
                    modifier = Modifier
                        .shared(shared, "title-${note.id}", text = true)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = 1f - .6f * p
                            scaleY = 1f - .6f * p
                            alpha = 1f - p
                        },
                    flex = { flex.value },
                )
            }
            if (photos.isNotEmpty()) item {
                if (hero != null) {
                    // the subject's top edge rides over the bottom of the title's last line, only slightly
                    val hPx = wPx * 1.2f
                    val s = com.b72.keep_canvas.ui.subjectIn(hero, wPx, hPx)
                    val last = lay?.heights?.lastOrNull() ?: 0f
                    val lift = if (lay == null) (s?.top ?: 0f) * .6f else ((s?.top ?: 0f) + last * .12f).coerceIn(0f, hPx * .5f)
                    val hDp = with(density) { hPx.toDp() }
                    val liftDp = with(density) { lift.toDp() }
                    Box(Modifier.fillMaxWidth().height(hDp - liftDp)) {
                        PopOut(
                            block = hero, fill = pal.primary,
                            tint = if (tint) pal.primary.copy(alpha = .59f) else null,
                            shapeSeed = seedOf(hero.id),
                            modifier = Modifier
                                .fillMaxWidth()
                                .requiredHeight(hDp)
                                .align(Alignment.BottomCenter)
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
            item {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Info("Tags", if (note.tags.isEmpty()) "None" else note.tags.joinToString(", "), pal.fg, Modifier.weight(1f))
                        Info("Created", created, pal.fg, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Info("Photos", "${photos.size}", pal.fg, Modifier.weight(1f))
                        Info("Checklist", if (list.isEmpty()) "-" else "${list.count { it.startsWith("x ") }} of ${list.size} done", pal.fg, Modifier.weight(1f))
                    }
                }
            }
            for (b in note.blocks) {
                if (b.kind in com.b72.keep_canvas.data.RECORD_KINDS) item { RecSection(b, title, pal, w) }
                else if (b.kind in setOf(Kind.Quote, Kind.Stat, Kind.Info, Kind.Poster)) item { TplCard(b, title, pal, Modifier.padding(bottom = 14.dp)) }
                else if (b.kind in CARD_KINDS) item { BlockCard(b, title, pal, Modifier.padding(bottom = 14.dp)) }
            }
            for (t in texts) item { Text(t, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp), color = pal.fg, modifier = Modifier.padding(bottom = 12.dp)) }
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
                        Icon(if (done) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, null, tint = pal.fg)
                        Spacer(Modifier.width(12.dp))
                        ScribbleText(if (done) s.substring(2) else s, done, pal.fg, MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), Modifier.weight(1f))
                    }
                }
            }
        }

        // top bar: the small title fades in as the big one shrinks away
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(pal.bg.copy(alpha = barP)) }
                .padding(top = top)
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = pal.fg) }
            Text(
                title,
                style = flexStyle(22.sp, wght = 700, rond = 40f),
                color = pal.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = barP; translationY = (1f - barP) * 16.dp.toPx() },
            )
            note.tags.firstOrNull()?.let { t ->
                Text(
                    t, style = MaterialTheme.typography.labelLarge, color = pal.fg,
                    modifier = Modifier.padding(end = 8.dp).minimumInteractiveComponentSize().clip(RoundedCornerShape(20.dp)).background(pal.primary.copy(alpha = .2f)).clickable { onTag(t) }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // M3 Expressive floating toolbar: the main action is wide and labelled, the rest are icons
        val ctx = LocalContext.current
        var askDelete by remember { mutableStateOf(false) }
        val barOpen by remember { derivedStateOf { ls.firstVisibleItemIndex == 0 || !ls.lastScrolledForward } }
        HorizontalFloatingToolbar(
            expanded = barOpen,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
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
                note.tags.firstOrNull()?.let { t -> IconButton(onClick = { onTag(t) }) { Icon(Icons.Rounded.Dashboard, "Open the $t canvas") } }
                IconButton(onClick = { askDelete = true }) { Icon(Icons.Rounded.Delete, "Delete note", tint = MaterialTheme.colorScheme.error) }
            },
            content = {
                Button(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit")
                }
            },
        )
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

@Composable
private fun Info(k: String, v: String, fg: Color, modifier: Modifier) {
    Column(modifier.padding(bottom = 16.dp)) {
        Text(k, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 15.sp), color = fg.copy(alpha = .7f))
        Text(v, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = fg)
    }
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
