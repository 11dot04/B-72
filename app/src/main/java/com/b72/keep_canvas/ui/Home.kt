@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import com.b72.keep_canvas.data.trip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import com.b72.keep_canvas.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.AutoAwesome
import com.b72.keep_canvas.data.matchesQuery
import com.b72.keep_canvas.data.searchText
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.History
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Loaded
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.Source
import kotlin.math.abs

/** Tile heights snap to a few ratios (height / width) so the two columns stay calm. */
private val Ratios = listOf(1.0, 1.25, 1.5)
private fun snap(r: Double) = Ratios.minBy { abs(it - r) }

/** Films and books are portrait posters, tracks are squares, photos follow their photo; lists and text grow with their content. */
private fun tileRatio(n: Note): Double {
    val c = n.cover
    if (c == null) {
        val list = n.blocks.filter { it.kind == Kind.Checklist }.sumOf { it.checkItems.size }
        val words = n.blocks.filter { it.kind == Kind.Text }.sumOf { it.body.length }
        return when { list > 3 || words > 160 -> 1.25; else -> 1.0 }
    }
    return when (c.kind) {
        Kind.Film, Kind.Book -> 1.5
        Kind.Track -> 1.0
        else -> snap(c.aspect.coerceIn(.8, 1.6))
    }
}

/** The kinds of note the filter row can narrow to. */
private class Filt(val name: String, val icon: ImageVector, val test: (Note) -> Boolean)

private val FILTERS = listOf(
    Filt("All", Icons.Rounded.Apps) { true },
    Filt("Starred", Icons.Rounded.Star) { it.starred },
    Filt("Photos", Icons.Rounded.Image) { it.photos.isNotEmpty() },
    Filt("Lists", Icons.Rounded.Checklist) { n -> n.blocks.any { it.kind == Kind.Checklist } },
    Filt("Quotes", Icons.Rounded.FormatQuote) { n -> n.blocks.any { it.kind == Kind.Quote } },
    Filt("Links", Icons.Rounded.Link) { n -> n.blocks.any { it.kind == Kind.Link } },
    Filt("Trips", Icons.Rounded.Train) { n -> n.blocks.any { it.trip() != null } },
)

@Composable
fun HomeScreen(repo: Repo, loaded: Loaded?, grid: LazyStaggeredGridState, shared: SharedCtx, onOpen: (String) -> Unit, onAdd: (Kind) -> Unit, onTag: (String) -> Unit = {}, onStickers: () -> Unit = {}, tab: Int = 0, onTab: (Int) -> Unit = {}, onMatch: (title: String, tag: String) -> Unit = { _, _ -> }) {
  val appCtx = androidx.compose.ui.platform.LocalContext.current
  androidx.compose.runtime.LaunchedEffect(Unit) { com.b72.keep_canvas.data.Follow.load(appCtx) }
  val match by produceState<com.b72.keep_canvas.data.Match?>(null, com.b72.keep_canvas.data.Follow.teams.size) { value = com.b72.keep_canvas.data.Follow.recent(appCtx).firstOrNull() }
  var dismissed by remember { mutableStateOf<String?>(null) }
  var menu by remember { mutableStateOf(false) }
  var q by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
  var searching by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
  val topInset = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this).toDp() }
  val texts = remember(loaded?.library) { loaded?.library?.notes.orEmpty().associate { it.id to it.searchText() } }
  var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("All") }
  val test: (Note) -> Boolean = if (filter.startsWith("#")) { n -> filter.substring(1) in n.tags } else (FILTERS.firstOrNull { it.name == filter }?.test ?: { _: Note -> true })
  var cols by UiPrefs.cols
  var sort by UiPrefs.sort
  // notes that mean what the search says, found by the on-device model after a short pause in typing
  val meaning by produceState<List<Note>>(emptyList(), q, loaded?.library) {
    value = emptyList()
    if (q.trim().length >= 3) { kotlinx.coroutines.delay(350); value = repo.similar(q) }
  }
  val shown = remember(loaded?.library, q, filter, sort, meaning) {
    // notes that are only a song, book, film, recipe or link live in the Library tab (a search still finds them)
    val l = loaded?.library?.notes.orEmpty().filter { test(it) && (q.isBlank() && !it.isLibrary && !(filter == "All" && it.starred) || q.isNotBlank() && matchesQuery(texts[it.id].orEmpty(), q)) }
    val extra = if (q.isBlank()) emptyList() else meaning.filter { m -> test(m) && l.none { it.id == m.id } }
    when (sort) {
      1 -> l + extra
      2 -> l.sortedByDescending { it.edited } + extra
      3 -> l.sortedBy { it.title.ifBlank { it.snippet() }.lowercase() } + extra
      else -> l.asReversed() + extra
    }
  }
  val starred = remember(loaded?.library) { loaded?.library?.notes.orEmpty().filter { it.starred && !it.isLibrary }.asReversed() }
  val revisit = remember(loaded?.library) { pickRevisit(loaded?.library?.notes.orEmpty()) }
  val trip = remember(loaded?.library) { com.b72.keep_canvas.data.Trips.next(loaded?.library?.notes.orEmpty()) }
  var asking by remember { mutableStateOf<String?>(null) }
  asking?.let { AskSheet(repo, it, onOpen) { asking = null } }
  BackHandler(menu) { menu = false }
  BackHandler(searching) { searching = false }
  // A page lays its big title out with a variable font, which is slow the first time. While the grid is idle the page-sized layouts of the
  // notes on screen are made ahead, one per frame, so opening a note does not stall the container transform on its first frame.
  run {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val screenW = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toFloat()
    val ahead = remember(shown) { (loaded?.library?.notes.orEmpty().filter { it.starred } + shown).distinctBy { it.id }.filter { it.title.isNotEmpty() }.take(16) }
    androidx.compose.runtime.LaunchedEffect(ahead, screenW) {
      val wPx = screenW - with(dens) { 32.dp.toPx() }
      for (n in ahead) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { titleLay(n.title, wPx, n.id, 3, if (dark) -25f else 0f, measurer, dens, n.layout) }
      }
    }
  }
  var railOpen by remember { mutableStateOf(false) }
  var settings by remember { mutableStateOf(false) }
  BackHandler(railOpen && !settings) { railOpen = false }
  BackHandler(settings) { settings = false }
  RailLayout(
    open = railOpen, tab = tab, onTab = { railOpen = false; if (it != tab) onTab(it) }, onSettings = { railOpen = false; settings = true },
    settings = settings, onCloseSettings = { settings = false }, settingsPage = { AppDrawer(repo) { settings = false } },
  ) {
  Box(Modifier.fillMaxSize()) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { inner ->
        LazyVerticalStaggeredGrid(
            state = grid,
            columns = StaggeredGridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize().bottomBlur(5.dp, .78f),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 80.dp,
                bottom = inner.calculateBottomPadding() + 150.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                val cs = MaterialTheme.colorScheme
                Column(Modifier.padding(bottom = 4.dp)) {
                    TabTitle(if (q.isBlank()) "Notes" else "Found", shown.size, Modifier.padding(bottom = 14.dp))
                    loaded?.error?.let { err -> Text(err, style = MaterialTheme.typography.bodyMedium, color = cs.error, modifier = Modifier.padding(bottom = 8.dp)) }
                    if (repo.prepTotal > 0) Column(Modifier.padding(bottom = 10.dp)) {
                        androidx.compose.material3.LinearWavyProgressIndicator(
                            progress = { (repo.prepDone.toFloat() / repo.prepTotal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Reading your notes ${repo.prepDone} of ${repo.prepTotal}",
                            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    // one quiet row: the kinds of note first (icon only until chosen), then the busiest tags, scrolling sideways
                    val have = remember(loaded?.library) { FILTERS.filter { f -> f.name == "All" || loaded?.library?.notes.orEmpty().any(f.test) } }
                    val topTags = remember(loaded?.library) {
                        loaded?.library?.notes.orEmpty().flatMap { it.tags }.groupingBy { it }.eachCount().filter { it.value >= 2 }.entries.sortedByDescending { it.value }.take(5).map { it.key }
                    }
                    if (have.size > 1 || topTags.isNotEmpty()) Row(
                        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (have.size > 1) for (f in have) {
                            val on = filter == f.name
                            FilterChip(
                                selected = on, onClick = { filter = f.name },
                                label = { if (on || f.name == "All") Text(f.name) else Icon(f.icon, f.name, Modifier.size(18.dp)) },
                                leadingIcon = if (on && f.name != "All") ({ Icon(f.icon, null, Modifier.size(18.dp)) }) else null,
                                shape = RoundedCornerShape(50),
                                border = null,
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                        }
                        for (t in topTags) FilterChip(
                            selected = filter == "#$t", onClick = { filter = if (filter == "#$t") "All" else "#$t" }, label = { Text("#$t") },
                            shape = RoundedCornerShape(50), border = null,
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        )
                    }
                }
            }
            // with the assistant installed, a search can also be a question
            if (q.trim().length >= 3 && com.b72.keep_canvas.data.Assistant.installed) item(span = StaggeredGridItemSpan.FullLine, key = "ask") {
                val cs = MaterialTheme.colorScheme
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer)
                        .clickable(onClickLabel = "Ask your notes") { asking = q.trim() }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = cs.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text("Ask your notes: \u201c${q.trim()}\u201d", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            // starred notes first, as a row you can slide through
            if (starred.isNotEmpty() && q.isBlank() && filter == "All") item(span = StaggeredGridItemSpan.FullLine, key = "starred") {
                val cs = MaterialTheme.colorScheme
                Column(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                        Icon(Icons.Rounded.Star, null, Modifier.size(16.dp), tint = cs.tertiary)
                        Spacer(Modifier.width(6.dp))
                        Text("STARRED", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .8.sp), color = cs.onSurfaceVariant)
                    }
                    // a multi-browse carousel: one big card, smaller ones trailing off the edge
                    androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel(
                        state = androidx.compose.material3.carousel.rememberCarouselState { starred.size },
                        preferredItemWidth = 190.dp, itemSpacing = 8.dp,
                        modifier = Modifier.fillMaxWidth().height(148.dp),
                    ) { i ->
                        val n = starred[i]
                        val cover = n.cover?.path
                        val bg = if (i % 2 == 0) cs.tertiaryContainer else cs.secondaryContainer
                        val fg = if (i % 2 == 0) cs.onTertiaryContainer else cs.onSecondaryContainer
                        Box(Modifier.fillMaxSize().opener(n.id, shrink = .1f).maskClip(RoundedCornerShape(28.dp)).noteContainer(n.id, 28.dp).background(bg).clickable(onClickLabel = "Open") { Opening.press(n.id); onOpen(n.id) }) {
                            if (cover != null) {
                                PhotoImage(cover, 400, false, cs.primary, Modifier.fillMaxSize())
                                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .62f)))))
                            }
                            Text(
                                n.title.ifBlank { n.snippet(60) }.ifBlank { "Untitled" }, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium, color = if (cover != null) Color.White else fg,
                                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                            )
                        }
                    }
                }
            }
            // the next journey, with the right status page for how close it is
            if (trip != null && q.isBlank()) item(span = StaggeredGridItemSpan.FullLine) {
                val cs = MaterialTheme.colorScheme
                TripCard(trip.second, cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary, Modifier.padding(bottom = 4.dp)) { onOpen(trip.first.id) }
            }
            // one older note, different each day: a second mind brings things back
            val mt = match?.takeIf { it.eventId != dismissed }
            if (mt != null && q.isBlank()) item(span = StaggeredGridItemSpan.FullLine) {
                MatchPrompt(mt, onWrite = {
                    com.b72.keep_canvas.data.Follow.markHandled(appCtx, mt.eventId); dismissed = mt.eventId
                    onMatch("${mt.title}  ${mt.score}", mt.team.sport.ifBlank { mt.team.name }.lowercase())
                }, onSkip = { com.b72.keep_canvas.data.Follow.markHandled(appCtx, mt.eventId); dismissed = mt.eventId })
            } else if (revisit != null && q.isBlank()) item(span = StaggeredGridItemSpan.FullLine) { Revisit(revisit.first, revisit.second) { onOpen(revisit.first.id) } }
            if (loaded != null) {
                items(shown, key = { it.id }) { n -> Box(Modifier.animateItem().opener(n.id)) { if (cols == 1) FeatureTile(n, shared, grow = !searching) { Opening.press(n.id); onOpen(n.id) } else Tile(n, shared, grow = !searching) { Opening.press(n.id); onOpen(n.id) } } }
                if (shown.isEmpty() && q.isNotBlank()) item(span = StaggeredGridItemSpan.FullLine) {
                    Text("Nothing matches “$q”", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }

    // the pill is the container the search page grows out of; while the page is open the pill steps aside
    androidx.compose.animation.AnimatedVisibility(
        visible = !searching, modifier = Modifier.align(Alignment.TopCenter),
        enter = androidx.compose.animation.EnterTransition.None, exit = androidx.compose.animation.ExitTransition.None,
    ) {
        KeepBar(
            q, { q = it }, cols == 1, { cols = if (cols == 1) 2 else 1 }, sort, { sort = it },
            count = loaded?.library?.notes?.size ?: 0, loading = loaded == null,
            menu = { androidx.compose.material3.IconButton({ railOpen = !railOpen }) { Icon(Icons.Rounded.Menu, "Menu") } },
            onSearch = { searching = true }, container = Modifier.container(SharedCtx(shared.st, this), "search", 28.dp),
        )
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = searching, enter = androidx.compose.animation.EnterTransition.None, exit = androidx.compose.animation.ExitTransition.None,
    ) {
        SearchPage(
            q, { q = it }, if (q.isBlank() && filter == "All") emptyList() else shown, loaded?.library?.notes?.count { !it.isLibrary }?.let { it } ?: 0,
            FILTERS.map { it.name to it.icon }, filter, { filter = it },
            { searching = false }, { id -> UiPrefs.pushRecent(q); Opening.press(id); onOpen(id) }, Modifier.container(SharedCtx(shared.st, this), "search", 28.dp, reveal = true, revealShift = topInset + 8.dp))
    }
    if (menu) Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menu = false },
    )
    FloatingActionButtonMenu(
        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        expanded = menu,
        button = {
            ToggleFloatingActionButton(checked = menu, onCheckedChange = { menu = it }) {
                val icon by remember { derivedStateOf { if (checkedProgress > .5f) Icons.Rounded.Close else Icons.Rounded.Add } }
                Icon(icon, contentDescription = "Add", modifier = Modifier.animateIcon({ checkedProgress }))
            }
        },
    ) {
        for ((kind, label, icon) in ADD_ITEMS) {
            FloatingActionButtonMenuItem(onClick = { menu = false; onAdd(kind) }, icon = { Icon(icon, null) }, text = { Text(label) })
        }
    }
  }
  }
}

private val ADD_ITEMS = listOf(
    Triple(Kind.Text, "Note", Icons.Rounded.Edit),
    Triple(Kind.Photo, "Photos", Icons.Rounded.AddPhotoAlternate),
    Triple(Kind.Checklist, "Checklist", Icons.Rounded.Checklist),
    Triple(Kind.Quote, "Quote", Icons.Rounded.FormatQuote),
)

/** A note that is at least a week old, picked by the day so it changes daily; returns it with its age in days. */
private fun pickRevisit(notes: List<Note>): Pair<Note, Int>? {
    val now = System.currentTimeMillis()
    val old = notes.mapNotNull { n ->
        val born = n.id.toLongOrNull()?.div(1000) ?: return@mapNotNull null
        val days = ((now - born) / 86_400_000L).toInt()
        if (days >= 7 && (n.title.isNotBlank() || n.snippet().isNotBlank())) n to days else null
    }
    if (old.isEmpty()) return null
    val day = now / 86_400_000L
    return old[((day * 31 + old.size) % old.size).toInt()]
}

@Composable
private fun Revisit(n: Note, days: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val path = n.cover?.path
    val img by produceState<ImageBitmap?>(path?.let { Images.peek(it, 600) }, n.id) { value = path?.let { Images.load(it, 600) } }
    val ago = when { days >= 365 -> "${days / 365} year${if (days >= 730) "s" else ""} ago"; days >= 60 -> "${days / 30} months ago"; else -> "$days days ago" }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(28.dp)).background(cs.tertiaryContainer)
            .clickable(onClickLabel = "Open this older note", onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        img?.let { Image(it, null, Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text("FROM $ago".uppercase(), style = MaterialTheme.typography.labelSmall, color = cs.onTertiaryContainer.copy(alpha = .7f))
            Text(n.title.ifBlank { n.snippet(60) }, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = if (n.title.isBlank()) TextStyle(fontFamily = Serif, fontSize = 17.sp, lineHeight = 22.sp, fontStyle = FontStyle.Italic) else flexStyle(22.sp, wght = 680, rond = 40f), color = cs.onTertiaryContainer)
        }
        Icon(Icons.Rounded.History, null, tint = cs.onTertiaryContainer)
    }
}

/**
 * The one-column tile: a big rounded card. With a photo, the picture runs full bleed and melts into progressive blur toward the
 * bottom, where the title sits large; without one, a colour card with the title big and a few lines beneath.
 */
@Composable
private fun FeatureTile(n: Note, shared: SharedCtx, grow: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tones = remember(cs) {
        listOf(cs.surfaceContainerHigh to cs.onSurface, cs.surfaceContainerHighest to cs.onSurface, cs.surfaceContainer to cs.onSurface)
    }
    val (bg, fg) = tones[seedMod(n.id, tones.size)]
    val path = n.cover?.path
    val img by produceState<ImageBitmap?>(path?.let { Images.peek(it, 900) }, n.id) { value = path?.let { Images.load(it, 900) } }
    val shape = RoundedCornerShape(36.dp)
    val t = n.title
    Box(Modifier.fillMaxWidth().then(if (grow) Modifier.container(shared, "card-${n.id}", 36.dp) else Modifier).clip(shape).background(bg).clickable(onClickLabel = "Open note", onClick = onClick)) {
        if (img != null) {
            Box(Modifier.fillMaxWidth().aspectRatio(.92f)) {
                Image(
                    img!!, null,
                    Modifier.fillMaxSize().shared(shared, "img-${n.id}").blur(
                        blurRadiusSpec = BlurRadiusSpec.verticalGradient(stops = listOf(BlurStop(0f, 0.dp), BlurStop(.5f, 0.dp), BlurStop(1f, 22.dp))),
                        edgeTreatment = BlurredEdgeTreatment.Rectangle,
                    ),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.45f to Color.Transparent, 1f to Color(0x99000000))))
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 18.dp, top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Meta(n, Color.White.copy(alpha = .85f))
                    Icon(Icons.Rounded.ArrowOutward, null, Modifier.size(20.dp), tint = Color.White)
                }
                if (t.isNotEmpty()) Box(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    FitTitle(t, Color.White, n.id, Modifier, maxLines = 3, style = n.layout)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Meta(n, fg.copy(alpha = .65f))
                    Icon(Icons.Rounded.ArrowOutward, null, Modifier.size(20.dp), tint = fg)
                }
                Column(Modifier.padding(top = 28.dp)) {
                    if (t.isNotEmpty()) FitTitle(t, fg, n.id, Modifier, maxLines = 3, style = n.layout)
                    Spacer(Modifier.height(12.dp))
                    Preview(n, fg)
                }
            }
        }
    }
}

private val TileShape = RoundedCornerShape(18.dp)

@Composable
private fun Tile(n: Note, shared: SharedCtx, grow: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tones = remember(cs) {
        listOf(cs.surfaceContainerHigh to cs.onSurface, cs.surfaceContainerHighest to cs.onSurface, cs.surfaceContainer to cs.onSurface)
    }
    val (bg, fg) = tones[seedMod(n.id, tones.size)]
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // spatial spring: the tile changes size, so it may overshoot a little
    val press by animateFloatAsState(if (pressed) .96f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    val ratio = tileRatio(n)
    val coverPath = n.cover?.path
    val img by produceState<ImageBitmap?>(coverPath?.let { Images.peek(it, 600) }, n.id) { value = coverPath?.let { Images.load(it, 600) } }
    val hasList = n.blocks.any { it.kind == Kind.Checklist }
    val hasTrip = n.blocks.any { it.trip() != null }
    val starred = n.starred
    Box(
        Modifier
            .fillMaxWidth()
            .scale(press)
            .graphicsLayer { rotationZ = if (n.starred) (seedMod(n.id, 5) - 2) * 1.1f + 1.4f else 0f }
            .aspectRatio((1 / ratio).toFloat())
            .then(if (grow) Modifier.container(shared, "card-${n.id}", 18.dp) else Modifier)
            .clip(TileShape)
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClickLabel = "Open note", onClick = onClick),
    ) {
        img?.let { Image(it, null, Modifier.fillMaxSize().shared(shared, "img-${n.id}"), contentScale = ContentScale.Crop) }
        if (starred) Icon(Icons.Rounded.Star, "Starred", Modifier.align(Alignment.TopEnd).padding(10.dp).size(20.dp), tint = if (img != null) Color(0xFFFFC928) else fg)
        val t = n.title
        if (img != null) {
            if (t.isNotEmpty()) Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
                    .padding(14.dp),
            ) {
                // every title fills its tile's width in its own look; the same title flexes on the note page
                Column {
                    FitTitle(t, Color.White, n.id, Modifier, maxLines = 3, style = n.layout)
                }
            }
            if (hasList || hasTrip) Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(cs.surface.copy(alpha = .88f)),
                contentAlignment = Alignment.Center,
            ) { Icon(if (hasTrip) Icons.Rounded.Train else Icons.Rounded.Checklist, null, Modifier.size(16.dp), tint = cs.onSurface) }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                if (t.isNotEmpty()) FitTitle(t, fg, n.id, Modifier, maxLines = 3, style = n.layout)
                Spacer(Modifier.height(10.dp))
                Preview(n, fg)
            }
        }
    }
}

/** Progress through a list as Material 3 Expressive draws it: a wavy ring that fills, with the number done in the middle. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChecklistRing(done: Int, total: Int, color: Color, size: androidx.compose.ui.unit.Dp) {
    val target = if (total == 0) 0f else done.toFloat() / total
    val p by androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.spring(.8f, 200f), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxSize(), color = color, trackColor = color.copy(alpha = .2f))
        Text("$done", style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** What a note with no picture shows in its tile: the first items of its list with a progress bar, else a few lines of its words. */
@Composable
private fun Preview(n: Note, fg: Color) {
    val list = n.blocks.firstOrNull { it.kind == Kind.Checklist }
    if (list != null) {
        val items = list.checkItems
        val done = items.count { it.startsWith("x ") }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (it in items.take(4)) {
                val d = it.startsWith("x ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (d) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(16.dp), tint = fg.copy(alpha = if (d) .9f else .6f))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (d) it.substring(2) else it, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = if (d) TextDecoration.LineThrough else null),
                        color = fg.copy(alpha = if (d) .55f else .9f),
                    )
                }
            }
            if (items.size > 4) Text("+ ${items.size - 4} more", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = .65f))
            if (items.isNotEmpty()) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ChecklistRing(done, items.size, fg, 44.dp)
                Spacer(Modifier.width(10.dp))
                Text("of ${items.size}", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = .75f))
            }
        }
        return
    }
    val sum = remember(n) {
        n.blocks.firstOrNull { it.kind in setOf(Kind.Quote, Kind.Stat, Kind.Info, Kind.Poster, Kind.Link) }
            ?.let { com.b72.keep_canvas.data.tplSummary(it.kind, it.title, it.body) }
            ?: n.blocks.firstOrNull { it.kind == Kind.Text && it.body.isNotBlank() }?.body
            ?: ""
    }
    if (sum.isNotEmpty()) Text(
        sum, maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = Serif, fontSize = 15.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic), color = fg.copy(alpha = .85f),
    )
}

/** A line of small monospaced capitals over a tile's title: its first tag and the day it was made. */
@Composable
private fun Meta(n: Note, color: Color, modifier: Modifier = Modifier) {
    val day = n.id.toLongOrNull()?.div(1000)?.takeIf { it > 1_000_000_000_000L }?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH)).uppercase()
    }
    val line = listOfNotNull(n.tags.firstOrNull()?.uppercase()?.take(14), day).joinToString(" \u00b7 ")
    if (line.isNotEmpty()) Text(line, maxLines = 1, modifier = modifier, style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp, letterSpacing = .6.sp, color = color))
}

private fun seedMod(id: String, n: Int) = com.b72.keep_canvas.data.seedOf(id) % n

/** The side menu: the on-device assistant, connected apps, security, and everything about getting data in and out. */
@Composable
private fun AppDrawer(repo: Repo, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var assistant by remember { mutableStateOf(false) }
    var services by remember { mutableStateOf(false) }
    var drive by remember { mutableStateOf(false) }
    var trash by remember { mutableStateOf(false) }
    if (trash) TrashSheet(repo) { trash = false }
    if (drive) DriveSheet(repo) { drive = false }
    if (assistant) AssistantDialog { assistant = false }
    if (services) ServicesSheet(repo) { services = false }
    fun say(t: String) = android.widget.Toast.makeText(ctx, t, android.widget.Toast.LENGTH_LONG).show()
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val lib = repo.loaded?.library
        if (uri != null && lib != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { com.b72.keep_canvas.data.Backup.export(lib, it) } }.isSuccess }
            say(if (ok) "Backup saved" else "Could not save the backup")
        }
    }
    val load = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val lib = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { com.b72.keep_canvas.data.Backup.read(ctx, it) } }.getOrNull() }
            say(if (lib == null) "That is not a B-72 backup" else "Restored ${repo.merge(lib)} new notes")
        }
    }
    val keep = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            say("Reading your Keep export…")
            val lib = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { com.b72.keep_canvas.data.KeepImport.read(ctx, it) } }.getOrNull() }
            say(if (lib == null) "No Keep notes found in that file" else "Imported ${repo.merge(lib)} notes from Keep")
        }
    }
    var digestOn by remember { mutableStateOf(com.b72.keep_canvas.data.Digest.enabled(ctx)) }
    var lockOn by remember { mutableStateOf(com.b72.keep_canvas.data.AppLock.enabled(ctx)) }
    val cs = MaterialTheme.colorScheme
    // one entry: what it is, its icon, and (for a setting that is on or off) its state, shown as a switch
    class Entry(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val on: Boolean? = null, val keepOpen: Boolean = false, val go: () -> Unit)
    // a group is a run of entries in one container: rounded hard at its two ends, only slightly between neighbours, as Material 3 Expressive lists are
    @Composable
    fun Group(title: String, entries: List<Entry>) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp))
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            entries.forEachIndexed { i, e ->
                val big = 24.dp; val small = 6.dp
                val shape = RoundedCornerShape(topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small, bottomStart = if (i == entries.lastIndex) big else small, bottomEnd = if (i == entries.lastIndex) big else small)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(cs.surfaceContainerHigh).clickable(onClickLabel = e.label) { if (!e.keepOpen) onClose(); e.go() }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(if (e.on == true) cs.primaryContainer else cs.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(e.icon, null, Modifier.size(22.dp), tint = if (e.on == true) cs.onPrimaryContainer else cs.onSecondaryContainer)
                    }
                    Text(e.label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
                    if (e.on != null) androidx.compose.material3.Switch(e.on, null)
                }
            }
        }
    }
    // one setting, laid out as pills with their icons: the chosen one is wider and takes the colour
    @Composable
    fun Choice(title: String, options: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>, selected: Int, onSelect: (Int) -> Unit) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp))
        Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, (label, icon) ->
                val on = i == selected
                val w by androidx.compose.animation.core.animateFloatAsState(if (on) 1.7f else 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "choice")
                val bg by androidx.compose.animation.animateColorAsState(if (on) cs.primary else cs.secondaryContainer, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "choicebg")
                Row(
                    Modifier.weight(w).clip(CircleShape).background(bg).clickable(onClickLabel = label) { onSelect(i) }.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(20.dp), tint = if (on) cs.onPrimary else cs.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) cs.onPrimary else cs.onSecondaryContainer, maxLines = 1)
                }
            }
        }
    }
    androidx.compose.material3.Surface(color = cs.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer).statusBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text("B-72", style = flexStyle(40.sp, wght = 760, rond = 40f, lh = 1f), color = cs.onPrimaryContainer)
                Text("Settings and your data. All of it stays on this phone unless you back it up.", style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer.copy(alpha = .8f), modifier = Modifier.padding(top = 6.dp))
            }
            Group("On this phone", listOf(
                Entry("On-device assistant", Icons.Rounded.AutoAwesome) { assistant = true },
                Entry("Connected apps", Icons.Rounded.Extension) { services = true },
                Entry("Weekly digest", Icons.Rounded.Notifications, digestOn, keepOpen = true) { digestOn = !digestOn; com.b72.keep_canvas.data.Digest.setEnabled(ctx, digestOn); say(if (digestOn) "Sunday digest on" else "Sunday digest off") },
                Entry("Clear unused photos", Icons.Rounded.CleaningServices) { scope.launch { val b = repo.cleanOrphans(); say(if (b > 0) "Freed ${"%.1f".format(b / 1_048_576.0)} MB" else "Nothing to clear") } },
            ))
            var saveShots by UiPrefs.saveShots
            Group("Look and feel", listOf(
                Entry("Keep artifact photos in the gallery", Icons.Rounded.PhotoLibrary, saveShots == 1, keepOpen = true) { saveShots = 1 - saveShots },
            ))
            var duo by UiPrefs.duotone
            Choice("Photos on a note page", listOf("As taken" to Icons.Rounded.Image, "Duotone" to Icons.Rounded.Contrast), duo) { duo = it }
            Group("Privacy", listOf(
                Entry("Lock the app", Icons.Rounded.Lock, lockOn, keepOpen = true) {
                    val on = !lockOn
                    if (com.b72.keep_canvas.data.AppLock.set(ctx, on)) { lockOn = on; say(if (on) "The app will ask for your PIN, pattern or fingerprint" else "App lock off") } else say("Set a screen lock on the phone first")
                },
                Entry("Archive & trash", Icons.Rounded.Archive) { trash = true },
            ))
            Group("Backup", listOf(
                Entry("Back up to Google Drive", Icons.Rounded.CloudUpload) { drive = true },
                Entry("Export backup", Icons.Rounded.Save) { save.launch("b72-backup.zip") },
                Entry("Restore backup", Icons.Rounded.Restore) { load.launch(arrayOf("application/zip", "application/octet-stream")) },
            ))
            Group("Bring notes in", listOf(
                Entry("Import from Google Keep", Icons.Rounded.Notes) { keep.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
            ))
        }
    }
}

/** The page the search pill opens into: the field at the top, the notes that match under it. */
@Composable
private fun SearchPage(
    q: String, onQ: (String) -> Unit, results: List<Note>, count: Int, kinds: List<Pair<String, ImageVector>>, kind: String, onKind: (String) -> Unit,
    onBack: () -> Unit, onOpen: (String) -> Unit, container: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(container.fillMaxSize().background(cs.surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(56.dp).clip(RoundedCornerShape(50)).background(cs.surfaceContainerHigh).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") }
            Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                if (q.isEmpty()) Text("Search $count notes", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1)
                androidx.compose.foundation.text.BasicTextField(
                    q, onQ, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
                )
            }
            if (q.isNotEmpty()) androidx.compose.material3.IconButton({ onQ("") }) { Icon(Icons.Rounded.Close, "Clear") }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            for ((name, icon) in kinds) FilterChip(
                selected = kind == name, onClick = { onKind(name) }, label = { Text(name) },
                leadingIcon = if (name != "All") ({ Icon(icon, null, Modifier.size(18.dp)) }) else null,
                shape = RoundedCornerShape(50), border = null,
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(containerColor = cs.surfaceContainerHigh),
            )
        }
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (q.isBlank() && kind == "All" && UiPrefs.recents.isNotEmpty()) {
                item { Text("Recent", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp)) }
                items(UiPrefs.recents.size) { i ->
                    val r = UiPrefs.recents[i]
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onQ(r) }.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, null, tint = cs.onSurfaceVariant)
                        Text(r, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.padding(start = 16.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if ((q.isNotBlank() || kind != "All") && results.isEmpty()) item { Text("Nothing matches “$q”", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(20.dp)) }
            items(results, key = { it.id }) { n ->
                Column(
                    Modifier.fillMaxWidth().noteContainer(n.id, 20.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh)
                        .clickable(onClickLabel = "Open note") { onOpen(n.id) }.padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Text(n.title.ifBlank { n.snippet(60) }.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (n.title.isNotBlank()) n.snippet(110).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
        }
    }
}

/** The top bar, as Keep has it: a menu, a pill to search in, and the layout and sort controls inside the pill. */
@Composable
private fun KeepBar(
    q: String, onQ: (String) -> Unit, list: Boolean, onLayout: () -> Unit, sort: Int, onSort: (Int) -> Unit,
    count: Int, loading: Boolean, menu: @Composable () -> Unit, onSearch: () -> Unit, container: Modifier, modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var sorting by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .height(56.dp).then(container).clip(RoundedCornerShape(50)).background(cs.surfaceContainerHigh).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        menu()
        // tapping the pill opens the search page; the words typed there show here too
        Box(
            Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Search", onClick = onSearch).padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (q.isEmpty()) Text(if (loading) "Loading…" else "Search $count notes", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1)
            else Text(q, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (q.isNotEmpty()) androidx.compose.material3.IconButton({ onQ("") }) { Icon(Icons.Rounded.Close, "Clear") }
        else {
            androidx.compose.material3.IconButton(onLayout) { Icon(if (list) Icons.Rounded.GridView else Icons.Rounded.ViewAgenda, if (list) "Grid view" else "List view") }
            Box {
                androidx.compose.material3.IconButton({ sorting = true }) { Icon(Icons.Rounded.SwapVert, "Sort") }
                GroupedMenu(sorting, { sorting = false }, listOf(MenuGroup("Sort by",
                    listOf("Newest first", "Oldest first", "Recently edited", "A to Z").mapIndexed { i, t ->
                        MenuEntry(t, { sorting = false; onSort(i) }, trailing = if (sort == i) Icons.Rounded.Check else null)
                    },
                )))
            }
        }
    }
}


/** Sits where the "from 7 days ago" tile does: a followed team has played, and this asks, once and quietly, what you made of it. */
@Composable
private fun MatchPrompt(m: com.b72.keep_canvas.data.Match, onWrite: () -> Unit, onSkip: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val whenText = when (m.daysAgo) { 0 -> "today"; 1 -> "yesterday"; else -> "${m.daysAgo} days ago" }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(28.dp)).background(cs.tertiaryContainer).clickable(onClickLabel = "Write about this match", onClick = onWrite).padding(18.dp),
    ) {
        Text("${m.team.name.uppercase()} · ${whenText.uppercase()}", style = MaterialTheme.typography.labelSmall, color = cs.onTertiaryContainer.copy(alpha = .7f))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(m.score, style = flexStyle(40.sp, wght = 800, rond = 30f, lh = 1f), color = cs.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(m.title, style = MaterialTheme.typography.titleSmall, color = cs.onTertiaryContainer, modifier = Modifier.weight(1f).padding(bottom = 4.dp), maxLines = 2)
        }
        Text("What did you make of it?", style = TextStyle(fontFamily = Serif, fontSize = 17.sp, fontStyle = FontStyle.Italic), color = cs.onTertiaryContainer, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilledTonalButton(onWrite) { Text("Write it down") }
            androidx.compose.material3.TextButton(onSkip) { Text("Not now", color = cs.onTertiaryContainer) }
        }
    }
}


/**
 * The expanded navigation rail. It sits beside the content in a Row rather than over it, so opening it squeezes the tiles to the side
 * instead of dimming them. Settings opens as its own page over everything.
 */
@Composable
private fun RailLayout(
    open: Boolean, tab: Int, onTab: (Int) -> Unit, onSettings: () -> Unit,
    settings: Boolean, onCloseSettings: () -> Unit, settingsPage: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val w by androidx.compose.animation.core.animateDpAsState(
        if (open) 272.dp else 0.dp,
        androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.CubicBezierEasing(.05f, .7f, .1f, 1f)), label = "rail",
    )
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(w).fillMaxHeight().clipToBounds()) {
                Column(Modifier.requiredWidth(272.dp).fillMaxHeight().background(cs.surface).statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Text("B-72", style = flexStyle(32.sp, wght = 760, rond = 40f, lh = 1f), color = cs.onSurface, modifier = Modifier.padding(start = 16.dp, top = 56.dp, bottom = 20.dp))
                    for ((i, d) in DESTS.withIndex()) {
                        val on = i == tab
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                                .background(if (on) cs.secondaryContainer else Color.Transparent)
                                .clickable { onTab(i) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (on) d.selected else d.icon, null, tint = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(d.label, style = MaterialTheme.typography.labelLarge, color = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).clickable { onSettings() }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Settings, null, tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("Settings", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
        AnimatedVisibility(settings, enter = fadeIn(tween(220)) + slideInHorizontally(tween(380, easing = androidx.compose.animation.core.CubicBezierEasing(.05f, .7f, .1f, 1f))) { it / 6 }, exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize()) {
                settingsPage()
                androidx.compose.material3.FilledTonalIconButton(onClick = onCloseSettings, modifier = Modifier.statusBarsPadding().padding(8.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            }
        }
    }
}
