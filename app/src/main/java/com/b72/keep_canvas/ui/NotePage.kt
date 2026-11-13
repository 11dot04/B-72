@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.MoreVert
import com.b72.keep_canvas.data.trip
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Redo
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
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
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
import com.b72.keep_canvas.data.ideas
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.PhotoLibrary

private fun textBlocksOf(n: Note) = n.blocks.filter { it.kind == Kind.Text }

private val TECH = com.b72.keep_canvas.data.CUT_KEYS + setOf("rating", "desc")

/**
 * The reading view. The title flexes out to full width when the page opens and shrinks into the top bar as
 * you scroll; one photo with one clear subject sits in a Material shape and the subject breaks out at the top.
 */
/** Keeps what is being typed clear of the keyboard strip and the top bar with the least scrolling; a large thing (a photo, a card) uses the usual least-scroll rule. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private class TypingSpec(val typing: () -> Boolean) : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // while a text field has the cursor the page follows the caret by its own rule (below), not by Compose's, so nothing fights over the scroll position
        if (typing()) return 0f
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f
            offset < 0f && trailing > containerSize -> 0f
            kotlin.math.abs(offset) < kotlin.math.abs(trailing - containerSize) -> offset
            else -> trailing - containerSize
        }
    }
}

@Composable
fun NotePage(
    note: Note, shared: SharedCtx, onBack: () -> Unit, onEdit: () -> Unit, onTag: (String) -> Unit = {},
    onDelete: () -> Unit = {}, onToggle: (blockId: String, index: Int) -> Unit = { _, _ -> },
    onSetData: (blockId: String, key: String, value: Int) -> Unit = { _, _, _ -> },
    onOpenNote: (String) -> Unit = {}, related: List<Note> = emptyList(),
    onRemind: (Long) -> Unit = {}, allNotes: List<Note> = emptyList(), onAddTag: (String) -> Unit = {}, onRemoveTag: (String) -> Unit = {},
    onSmart: (Boolean) -> Unit = {}, onAppend: (String) -> Unit = {},
    onStar: () -> Unit = {}, onTitle: (String) -> Unit = {}, onRich: (blockId: String, text: String, spans: String, indent: Boolean) -> Unit = { _, _, _, _ -> }, onLayout: (String) -> Unit = {}, repo: com.b72.keep_canvas.data.Repo? = null, addFirst: String? = null, onAddFirstDone: () -> Unit = {},
    onItems: (blockId: String, items: List<String>) -> Unit = { _, _ -> }, autoFocus: Boolean = false,
) {
    val pal = rememberNotePalette(note)
    val density = LocalDensity.current
    // duotone is a setting of the app (in the side menu), not something each page carries
    val tint = UiPrefs.duotone.value == 1
    var viewer by remember { mutableStateOf<Int?>(null) }
    var editTitle by remember { mutableStateOf(false) }
    var rich by remember { mutableStateOf<RichState?>(null) }
    var ready by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        // not the whole transition: the body joins while the card is still settling, so it is there by the time the eye gets to it
        if (shared.st.isTransitionActive) kotlinx.coroutines.delay(110)
        ready = true
    }
    var adding by remember { mutableStateOf(addFirst != null) }
    var tagging by remember { mutableStateOf(false) }


    val ls = rememberLazyListState()
    var vp by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var caret by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val dens = androidx.compose.ui.platform.LocalDensity.current
    // the one rule that moves the page while typing: when the caret gets into the strip above the keyboard (where the formatting bar stands) or under
    // the top bar, scroll just far enough to clear it. Inside the band nothing moves, so there is nothing to pull back.
    LaunchedEffect(caret) {
        val c = caret ?: return@LaunchedEffect
        if (vp.height <= 0f) return@LaunchedEffect
        val lowLimit = vp.bottom - with(dens) { 104.dp.toPx() }
        val highLimit = vp.top + with(dens) { 84.dp.toPx() }
        val d = when {
            c.bottom > lowLimit -> c.bottom - lowLimit
            c.top < highLimit -> c.top - highLimit
            else -> 0f
        }
        if (kotlin.math.abs(d) > 1f) ls.dispatchRawDelta(d)
    }
    // The big title scrolls away like any other content. The bar's own title takes over once the big one has slid under the
    // bar; where the big title really is gets measured, so the two are never on screen together.
    val barBottomPx = with(density) { 64.dp.toPx() }
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
    val structured = remember(note.blocks) { if (note.blocks.any { it.kind == Kind.Poster }) null else note.blocks.firstOrNull { it.kind == Kind.Text && it.body.isNotBlank() }?.let { com.b72.keep_canvas.data.looksStructured(it.body) } }
    // page layouts are gone: the note's layout field now holds only its title style
    val layoutId = ""
    val hero = if (photos.size == 1 && canPopOut(photos[0])) photos[0] else null
    // a recipe whose photo has one clear subject: the photo runs up under the status bar and the title sits behind the subject
    val recipeHero = remember(note.blocks) {
        note.blocks.singleOrNull { it.kind == Kind.Recipe }?.takeIf { it.path != null && canPopOut(it) && photos.isEmpty() }
    }
    val texts = note.blocks.filter { it.kind == Kind.Text && it.body.isNotEmpty() }.map { it.body }
    val list = note.blocks.filter { it.kind == Kind.Checklist }.flatMap { it.checkItems }
    val top = 0.dp   // the card starts below the status bar
    val bottom = 0.dp

    viewer?.let { v -> PhotoViewer(photos.mapNotNull { it.path }, v) { viewer = null } }
    // The page is a card inset from the screen: grey all round (the status bar above it, a strip below it that holds undo, redo and the tag),
    // and the page's own colours only inside. The container transform grows a tile into this card, not into the whole screen.
    val frame = MaterialTheme.colorScheme.surfaceContainerHigh
    val frameA by shared.av.transition.animateFloat(transitionSpec = { tween(260) }, label = "frame") { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.matchParentSize().graphicsLayer { alpha = frameA }.background(frame))
    Column(Modifier.fillMaxSize().imePadding().statusBarsPadding().navigationBarsPadding()) {
    Spacer(Modifier.height(6.dp))
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp).container(shared, "card-${note.id}", 28.dp, reveal = true).clip(RoundedCornerShape(28.dp)).background(pal.bg).nestedScroll(scrollBehavior.nestedScrollConnection)) {
        val w = maxWidth - 32.dp
        val wPx = with(density) { w.toPx() }
        val lay = if (title.isNotEmpty()) rememberTitleLay(title, wPx, note.id, style = note.layout) else null

        // the line being typed is kept near the middle, so neither the keyboard nor the formatting strip ever covers it
        val richNow by androidx.compose.runtime.rememberUpdatedState(rich)
        val spec = remember { TypingSpec { richNow != null } }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec) {
        LazyColumn(
            state = ls,
            modifier = Modifier.fillMaxSize().imePadding().onGloballyPositioned { vp = it.boundsInRoot() },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 72.dp, bottom = bottom + (maxHeight * .5f).coerceAtLeast(112.dp)),
        ) {
            // the title is a field when there is none yet (or after a tap); otherwise it is the big flexing title
            val explicit = note.blocks.firstOrNull { it.kind != Kind.Checklist && it.title.isNotBlank() }?.title.orEmpty()
            val hasText = note.blocks.any { it.kind == Kind.Text }
            val hasList = note.blocks.any { it.kind == Kind.Checklist }
            val fieldTitle = (explicit.isEmpty() && title.isEmpty()) || editTitle
            val headerMode = layoutId in HEADER_LAYOUTS && title.isNotEmpty() && !fieldTitle && (photos.isNotEmpty() || layoutId !in NEEDS_PHOTO)
            val embed = headerMode && layoutId == "poster"
            val coverMode = headerMode
            val besideMode = false
            val bodies: @Composable (Boolean) -> Unit = { compact ->
                val tbs = note.blocks.filter { it.kind == Kind.Text }
                // the formatting bar stands on the right edge while typing, so the words make room for it instead of running under it
                val barRoom by androidx.compose.animation.core.animateDpAsState(if (rich != null) 0.dp else 0.dp, androidx.compose.animation.core.tween(260), label = "barRoom")
                for ((ti, b) in tbs.withIndex()) {
                    val linked = rememberLinked(b.body, allNotes, pal.primary, onOpenNote)
                    Box(Modifier.padding(end = barRoom)) { InlineBody(
                        b.body, b.str("spans").orEmpty(), (b.dbl("indent") ?: 0.0) > 0 || layoutId == "script", if (ti == 0) "Note" else "", pal, linked,
                        focus = autoFocus && ti == 0 && b.body.isEmpty() && !hasList, onRich = { rich = it },
                        serif = layoutId == "script" || layoutId == "ledger", numbered = layoutId == "ledger", compact = compact, onCaret = { caret = it },
                    ) { t, sp, ind -> onRich(b.id, t, sp, ind) } }
                }
                if (tbs.isEmpty() && !hasList && note.blocks.none { it.kind == Kind.Poster }) InlineBody("", "", false, "Add a note", pal, androidx.compose.ui.text.AnnotatedString(""), focus = false, onRich = { rich = it }, serif = false, numbered = false, compact = compact) { t, sp, ind -> onRich("in_${note.id}", t, sp, ind) }
            }
            if (recipeHero != null && !fieldTitle) item(key = "recipe-head") {
                RecipeHeader(recipeHero, title, top + 72.dp, shared, note.id) { editTitle = true }
            }
            if (fieldTitle) item(key = "title-field") {
                InlineTitle(
                    explicit, if (title.isNotEmpty()) title else "Title", pal, focus = editTitle,
                    onDone = { editTitle = false }, onChange = onTitle,
                )
            } else if (lay != null && !coverMode && recipeHero == null) item(key = "title-big") {
                FitTitle(
                    title, pal.fg, note.id, style = note.layout,
                    modifier = Modifier.onGloballyPositioned { titleBottom = it.boundsInWindow().bottom }
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClickLabel = "Edit title") { editTitle = true },
                )
            }
            if (headerMode) item(key = "header") {
                val edit = { editTitle = true }
                val view: (Int) -> Unit = { viewer = it }
                when (layoutId) {
                    "cover" -> CoverBlock(title, photos[0], pal, note.id, tint, onTitle = edit, onView = { viewer = 0 })
                    "wordmark" -> WordmarkHeader(title, photos, note.tags, pal, tint, edit, view)
                    "poster" -> PosterHeader(title, photos[0], note.tags, pal, note.id, tint, edit, { viewer = 0 }, { bodies(true) })
                    else -> ColumnHeader(title, photos, note.tags, pal, tint, edit, view)
                }
            }
            if (photos.isNotEmpty() && !coverMode && !besideMode) item {
                if (hero != null) {
                    // The shape's frame is sized from the subject; the area is trimmed to the frame and the subject's head
                    // rides up over the title's last line.
                    val hPx = wPx * 1.2f
                    val frame = com.b72.keep_canvas.ui.popFrame(hero, wPx, hPx)
                    val s = com.b72.keep_canvas.ui.subjectIn(hero, wPx, hPx)
                    val last = lay?.heights?.lastOrNull() ?: 0f
                    // the highest point of anything drawn (the shape's edge can sit above the subject) may rise into the title by a sliver of its last line, no more
                    val highest = minOf(frame.top, s?.top ?: frame.top)
                    val lift = if (lay == null) highest * .6f else (highest + last * .14f).coerceIn(0f, hPx * .5f)
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
                                .clickable(onClickLabel = "View photo") { viewer = 0 },
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
                            .clickable(onClickLabel = "View photo") { viewer = 0 },
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                    HorizontalMultiBrowseCarousel(
                        state = rememberCarouselState { photos.size },
                        preferredItemWidth = 260.dp,
                        itemSpacing = 8.dp,
                        modifier = Modifier.fillMaxWidth().height(w * .7f).shared(shared, "img-${note.id}"),
                    ) { i ->
                        PhotoImage(photos[i].path!!, 1000, tint, pal.primary, Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge).clickable(onClickLabel = "View photo") { viewer = i })
                    }
                }
            }
            // the title and the photo are what the container transform needs on its first frames; the rest of the page is composed once it has landed
            if (ready) {
            item { Spacer(Modifier.height(24.dp)) }
            for (b in note.blocks) b.trip()?.let { tr ->
                item(key = "trip-${b.id}") { TripCard(tr, pal.fg, pal.bg, pal.primary, Modifier.padding(bottom = 18.dp)) }
            }
            if (structured != null) item(key = "structured") {
                androidx.compose.material3.AssistChip(
                    onClick = { onSmart(true) }, label = { Text(if (structured == "receipt") "Show as a receipt" else "Show as a details sheet") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)) }, modifier = Modifier.padding(bottom = 14.dp),
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
            if (photos.isNotEmpty() && (photos.size == 1 && photos[0].cutPath() != null || repo != null)) item(key = "photo-tools") {
                val pb = photos[0]
                val cut = photos.size == 1 && pb.cutPath() != null
                val on = cut && canPopOut(pb)
                var menu by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp), horizontalAlignment = Alignment.End) {
                    Box {
                        androidx.compose.material3.FilledTonalButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (photos.size == 1) "Photo" else "Photos")
                        }
                        GroupedMenu(menu, { menu = false }, listOf(
                            MenuGroup("Photo", listOfNotNull(
                                if (cut) MenuEntry(if (on) "Pop out: on" else "Pop out the subject", { menu = false; onSetData(pb.id, "pop", if (on) 0 else 1) }, Icons.Rounded.AutoAwesome) else null,
                            )),
                            MenuGroup(null, listOfNotNull(
                                if (repo != null) MenuEntry("Remove photo", { menu = false; repo.removeBlock(note.id, pb.id) }, Icons.Rounded.DeleteOutline, tint = MaterialTheme.colorScheme.error) else null,
                            )),
                        ))
                    }
                    val why = if (cut) popWhy(pb) else ""
                    if (cut && !on && why.isNotEmpty() && pb.dbl("pop") == null) Text(
                        "Left flat automatically: $why",
                        style = MaterialTheme.typography.labelSmall, color = pal.fg.copy(alpha = .6f), modifier = Modifier.padding(top = 6.dp, end = 4.dp),
                    )
                }
            }
            item(key = "ai") { AiStrip(note, pal.fg, pal.primary, onAddTag, Modifier.padding(bottom = 18.dp), onTitle = onTitle) }
            if (repo != null) item(key = "ideas") {
                val ideas = remember(note.blocks, note.tags) { note.ideas() }
                IdeasStrip(ideas, pal.fg, { repo.addIdea(note.id, it) }, { repo.dismissIdea(note.id, it) }, Modifier.padding(bottom = 18.dp))
            }
            for (b in note.blocks) {
                if (b.kind == Kind.Audio) item(key = "au-${b.id}") { AudioPlayer(b, pal, { repo?.removeBlock(note.id, b.id) }, Modifier.padding(bottom = 14.dp)) }
                else if (b.kind in com.b72.keep_canvas.data.RECORD_KINDS) item { RecSection(b, title, pal, w, onLyric = repo?.let { r -> { t: String -> r.setData(note.id, b.id, "lyric", kotlinx.serialization.json.JsonPrimitive(t)) } }, onData = repo?.let { r -> { k: String, v: kotlinx.serialization.json.JsonElement -> r.setData(note.id, b.id, k, v) } }, onCover = repo?.let { r -> { p: String -> r.setCover(note.id, b.id, p) } }, hero = recipeHero?.id == b.id) }
                else if (!hasContent(b, title)) Unit
                else if (b.kind in setOf(Kind.Quote, Kind.Stat, Kind.Info, Kind.Poster)) item { TplCard(b, title, pal, Modifier.padding(bottom = 14.dp)) }
                else if (b.kind in CARD_KINDS) item { BlockCard(b, title, pal, Modifier.padding(bottom = 14.dp)) }
            }
            val isDraft = autoFocus
            if (!embed) item(key = "bodies") { Column { bodies(false) } }
            for (b in note.blocks) if (b.kind == Kind.Checklist) item(key = "l-${b.id}") {
                InlineChecklist(b.checkItems, pal, focus = isDraft && b.checkItems.all { it.isBlank() }, seed = note.id.hashCode()) { onItems(b.id, it) }
            }
            item(key = "tags") {
                TagEditor(note.tags, allNotes, pal, onAddTag, onRemoveTag, onTag, onEditing = { tagging = it }, modifier = Modifier.padding(top = 10.dp))
            }
            val words = note.blocks.filter { it.kind == Kind.Text }.sumOf { it.body.trim().split(Regex("\\s+")).count { w -> w.isNotEmpty() } }
            if (words >= 20) item(key = "count") {
                Text("$words words · ${max(1, words / 200)} min read", style = MaterialTheme.typography.labelMedium, color = pal.fg.copy(alpha = .5f), modifier = Modifier.padding(top = 10.dp, start = 4.dp))
            }
            if (related.isNotEmpty()) item { RelatedMosaic(related, pal, onOpenNote, Modifier.padding(top = 28.dp)) }
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
        var parts by remember { mutableStateOf(false) }
        if (parts && repo != null) PartsSheet(note, { repo.removeBlock(note.id, it.id) }) { parts = false }
        var pickDate by remember { mutableStateOf(false) }
        var pickTime by remember { mutableStateOf<java.time.LocalDate?>(null) }
        val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
        val barOpen by remember { derivedStateOf { ls.firstVisibleItemIndex == 0 || !ls.lastScrolledForward } }
        val share = {
            val text = buildString {
                append(title)
                texts.forEach { append("\n\n").append(it) }
                list.forEach { append("\n• ").append(it.removePrefix("x ")) }
            }.trim()
            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, title) }
            ctx.startActivity(Intent.createChooser(send, null))
        }
        var details by remember { mutableStateOf(false) }
        var brainstorm by remember { mutableStateOf(false) }
        var setupAi by remember { mutableStateOf(false) }
        val smartOn = note.blocks.any { it.kind == Kind.Poster }
        // reminder and details on the sides, the primary action in the middle as a split button
        // the toolbar is the container the add card grows out of, so it steps aside (and comes back) as that card opens and closes
        androidx.compose.animation.AnimatedVisibility(
            visible = rich == null && !tagging && !adding,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            enter = androidx.compose.animation.EnterTransition.None, exit = androidx.compose.animation.ExitTransition.None,
        ) { HorizontalFloatingToolbar(
            expanded = barOpen,
            // neutral on purpose: the page takes its colours from the note, so the bar must not share them
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                toolbarContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.container(SharedCtx(shared.st, this), "add", 28.dp, full = false),
            leadingContent = {
                IconButton(onClick = onStar) { Icon(if (note.starred) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (note.starred) "Remove star" else "Star this note") }
                IconButton(onClick = { pickDate = true }) { Icon(Icons.Rounded.NotificationAdd, "Remind me about this note") }
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { details = true }) { Icon(Icons.Rounded.MoreVert, "Details and more") }
                    DetailsMenu(note, details, { details = false }, { askDelete = true }, share, onTag, onParts = if (repo != null) ({ parts = true }) else null, onArchive = if (repo != null) ({ repo.archive(note.id); onBack() }) else null)
                }
            },
            content = {
                EditSplit(
                    container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary,
                    smart = smartOn, layout = note.layout, onAdd = { adding = true }, onLayout = onLayout, onSmart = { onSmart(!smartOn) }, onBrainstorm = { brainstorm = true },
                )
            },
        ) }
        if (repo != null) androidx.compose.animation.AnimatedVisibility(
            visible = adding, enter = androidx.compose.animation.EnterTransition.None, exit = androidx.compose.animation.ExitTransition.None,
        ) { AddSheet(repo, note.id, hasList = note.blocks.any { it.kind == Kind.Checklist }, initial = addFirst, card = SharedCtx(shared.st, this)) { adding = false; onAddFirstDone() } }
        // while words are being typed: the formatting strip over the keyboard, with a tick to finish
        rich?.let { r ->
            val fm = androidx.compose.ui.platform.LocalFocusManager.current
            val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.BottomCenter) {
                FormatBar(r, { fm.clearFocus(); kb?.hide() })
            }
        }
        if (brainstorm) BrainstormSheet(note, { brainstorm = false }, onAppend, { setupAi = true })
        if (setupAi) AssistantDialog { setupAi = false }
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
    Row(
        Modifier.fillMaxWidth().height(56.dp).graphicsLayer { alpha = frameA }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val on = MaterialTheme.colorScheme.onSurfaceVariant
        if (repo != null) {
            IconButton(onClick = { repo.undo(note.id) }, enabled = repo.canUndo(note.id)) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo", tint = on.copy(alpha = if (repo.canUndo(note.id)) 1f else .38f)) }
            IconButton(onClick = { repo.redo(note.id) }, enabled = repo.canRedo(note.id)) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo", tint = on.copy(alpha = if (repo.canRedo(note.id)) 1f else .38f)) }
        }
        Spacer(Modifier.weight(1f))
        note.tags.firstOrNull()?.let { t ->
            Text(
                "#$t", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer).clickable { onTag(t) }.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
    }
    }
}

@Composable
fun PhotoImage(path: String, width: Int, tint: Boolean, tintColor: Color, modifier: Modifier, align: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center) {
    val img by produceState<ImageBitmap?>(null, path) { value = Images.load(path, width) }
    img?.let {
        Image(
            it, null, modifier,
            contentScale = ContentScale.Crop, alignment = align,
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


/**
 * The photo of a recipe with one clear subject, flush with the top of the screen. The title is drawn between the photo and the cut-out of the
 * subject, so the dish stands in front of the words. [pull] is the room the list reserves above its first item; the photo reaches back up through it.
 */
@Composable
private fun RecipeHeader(b: Block, title: String, pull: androidx.compose.ui.unit.Dp, shared: SharedCtx, noteId: String, onEditTitle: () -> Unit) {
    val photo by produceState<ImageBitmap?>(b.path?.let { Images.peek(it, 1100) }, b.path) { value = b.path?.let { Images.load(it, 1100) } }
    val cut by produceState<ImageBitmap?>(b.cutPath()?.let { Images.peek(it, 1100) }, b.id) { value = b.cutPath()?.let { Images.load(it, 1100) } }
    val full = 430.dp
    // the photo runs the whole width of the card, past the list's side padding, with round lower corners
    val edge = 16.dp
    Box(
        Modifier.layout { m, c ->
            val e = edge.roundToPx()
            val p = m.measure(c.copy(minWidth = c.maxWidth + 2 * e, maxWidth = c.maxWidth + 2 * e))
            layout(c.maxWidth, p.height) { p.place(-e, 0) }
        }.fillMaxWidth().height(full - pull).wrapContentHeight(Alignment.Top, unbounded = true),
    ) {
        Box(Modifier.fillMaxWidth().height(full).offset(y = -pull).shared(shared, "img-$noteId").clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))) {
            photo?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = .32f), Color.Transparent, Color.Black.copy(alpha = .2f)))))
            Text(
                title.ifBlank { "Recipe" }, maxLines = 3,
                style = flexStyle(64.sp, wght = 800, wdth = 92f, lh = .95f), color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, end = 20.dp, top = pull + 8.dp)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClickLabel = "Edit title") { onEditTitle() },
            )
            cut?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            // the dish stands in front of the words, but they stay readable through it: a faint second copy of the title is drawn over the dish
            if (cut != null) Text(
                title.ifBlank { "Recipe" }, maxLines = 3,
                style = flexStyle(64.sp, wght = 800, wdth = 92f, lh = .95f), color = Color.White.copy(alpha = .3f),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, end = 20.dp, top = pull + 8.dp),
            )
        }
    }
}
