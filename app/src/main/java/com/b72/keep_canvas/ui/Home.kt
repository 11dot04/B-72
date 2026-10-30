@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import com.b72.keep_canvas.data.trip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Checklist
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
import androidx.compose.material.icons.rounded.Star
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
fun HomeScreen(repo: Repo, loaded: Loaded?, grid: LazyStaggeredGridState, shared: SharedCtx, onOpen: (String) -> Unit, onAdd: (Kind) -> Unit, onTag: (String) -> Unit = {}, onStickers: () -> Unit = {}, onMatch: (title: String, tag: String) -> Unit = { _, _ -> }) {
  val appCtx = androidx.compose.ui.platform.LocalContext.current
  androidx.compose.runtime.LaunchedEffect(Unit) { com.b72.keep_canvas.data.Follow.load(appCtx) }
  val match by produceState<com.b72.keep_canvas.data.Match?>(null, com.b72.keep_canvas.data.Follow.teams.size) { value = com.b72.keep_canvas.data.Follow.recent(appCtx).firstOrNull() }
  var dismissed by remember { mutableStateOf<String?>(null) }
  var menu by remember { mutableStateOf(false) }
  var q by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
  val texts = remember(loaded?.library) { loaded?.library?.notes.orEmpty().associate { it.id to it.searchText() } }
  var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("All") }
  val test: (Note) -> Boolean = if (filter.startsWith("#")) { n -> filter.substring(1) in n.tags } else (FILTERS.firstOrNull { it.name == filter }?.test ?: { _: Note -> true })
  var cols by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(2) }
  var sort by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
  val shown = remember(loaded?.library, q, filter, sort) {
    val l = loaded?.library?.notes.orEmpty().filter { test(it) && (q.isBlank() || matchesQuery(texts[it.id].orEmpty(), q)) }
    when (sort) {
      1 -> l
      2 -> l.sortedByDescending { it.edited }
      3 -> l.sortedBy { it.title.ifBlank { it.snippet() }.lowercase() }
      else -> l.asReversed()
    }
  }
  val revisit = remember(loaded?.library) { pickRevisit(loaded?.library?.notes.orEmpty()) }
  val trip = remember(loaded?.library) { com.b72.keep_canvas.data.Trips.next(loaded?.library?.notes.orEmpty()) }
  BackHandler(menu) { menu = false }
  Box(Modifier.fillMaxSize()) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { inner ->
        LazyVerticalStaggeredGrid(
            state = grid,
            columns = StaggeredGridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize(),
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
                    // narrow by what a note contains; chips wrap onto the next line instead of being cut off
                    val have = remember(loaded?.library) { FILTERS.filter { f -> f.name == "All" || loaded?.library?.notes.orEmpty().any(f.test) } }
                    if (have.size > 1) androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (f in have) FilterChip(
                            selected = filter == f.name, onClick = { filter = f.name }, label = { Text(f.name) },
                            leadingIcon = { Icon(f.icon, null, Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(50),
                        )
                    }
                    val topTags = remember(loaded?.library) {
                        loaded?.library?.notes.orEmpty().flatMap { it.tags }.groupingBy { it }.eachCount().filter { it.value >= 2 }.entries.sortedByDescending { it.value }.take(8).map { it.key }
                    }
                    if (topTags.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (t in topTags) FilterChip(selected = filter == "#$t", onClick = { filter = if (filter == "#$t") "All" else "#$t" }, label = { Text("#$t") }, shape = RoundedCornerShape(50))
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
                items(shown, key = { it.id }) { n -> Tile(n, shared) { onOpen(n.id) } }
                if (shown.isEmpty() && q.isNotBlank()) item(span = StaggeredGridItemSpan.FullLine) {
                    Text("Nothing matches “$q”", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }

    KeepBar(
        q, { q = it }, cols == 1, { cols = if (cols == 1) 2 else 1 }, sort, { sort = it },
        count = loaded?.library?.notes?.size ?: 0, loading = loaded == null,
        menu = { BackupMenu(repo, onStickers) },
        modifier = Modifier.align(Alignment.TopCenter),
    )
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

private val ADD_ITEMS = listOf(
    Triple(Kind.Text, "Note", Icons.Rounded.Edit),
    Triple(Kind.Photo, "Photos", Icons.Rounded.AddPhotoAlternate),
    Triple(Kind.Checklist, "Checklist", Icons.Rounded.Checklist),
    Triple(Kind.Quote, "Quote", Icons.Rounded.FormatQuote),
    Triple(Kind.Link, "Link", Icons.Rounded.Link),
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

private val TileShape = RoundedCornerShape(28.dp)

@Composable
private fun Tile(n: Note, shared: SharedCtx, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tones = remember(cs) {
        listOf(cs.primaryContainer to cs.onPrimaryContainer, cs.secondaryContainer to cs.onSecondaryContainer, cs.tertiaryContainer to cs.onTertiaryContainer)
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
                FitTitle(t, Color.White, n.id, Modifier.shared(shared, "title-${n.id}", text = true), maxLines = 3)
            }
            if (hasList || hasTrip) Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(cs.surface.copy(alpha = .88f)),
                contentAlignment = Alignment.Center,
            ) { Icon(if (hasTrip) Icons.Rounded.Train else Icons.Rounded.Checklist, null, Modifier.size(16.dp), tint = cs.onSurface) }
        } else {
            // a swash of the pen
            // one bold, restrained swash in the corner of some tiles
            val sw = seedMod(n.id, 8)
            if (sw < 4 && !hasList) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val wd = size.width * .46f
                drawSwash(sw, size.width - wd - 14.dp.toPx(), size.height - wd * .45f - 16.dp.toPx(), wd, fg.copy(alpha = .16f))
            }
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                if (t.isNotEmpty()) FitTitle(t, fg, n.id, Modifier.shared(shared, "title-${n.id}", text = true), maxLines = 3)
                Spacer(Modifier.height(10.dp))
                Preview(n, fg)
            }
        }
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
            if (items.isNotEmpty()) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { done.toFloat() / items.size }, modifier = Modifier.size(26.dp),
                    color = fg, trackColor = fg.copy(alpha = .18f), strokeWidth = 4.dp, gapSize = 0.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("$done of ${items.size}", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = .75f))
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

private fun seedMod(id: String, n: Int) = com.b72.keep_canvas.data.seedOf(id) % n

/** Export and restore the whole collection as one zip, through the system file picker. */
@Composable
private fun BackupMenu(repo: Repo, onStickers: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var assistant by remember { mutableStateOf(false) }
    var services by remember { mutableStateOf(false) }
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
    Box {
        androidx.compose.material3.IconButton({ open = true }) { Icon(Icons.Rounded.Menu, "Menu") }
        androidx.compose.material3.DropdownMenu(open, { open = false }) {
            androidx.compose.material3.DropdownMenuItem({ Text("On-device assistant") }, { open = false; assistant = true })
            androidx.compose.material3.DropdownMenuItem({ Text("Connected apps") }, { open = false; services = true })
            androidx.compose.material3.DropdownMenuItem({ Text("Clear unused photos") }, { open = false; scope.launch { val b = repo.cleanOrphans(); say(if (b > 0) "Freed ${"%.1f".format(b / 1_048_576.0)} MB" else "Nothing to clear") } })
            androidx.compose.material3.DropdownMenuItem({ Text("Export backup") }, { open = false; save.launch("b72-backup.zip") })
            androidx.compose.material3.DropdownMenuItem({ Text("Restore backup") }, { open = false; load.launch(arrayOf("application/zip", "application/octet-stream")) })
        }
    }
}

/** The top bar, as Keep has it: a menu, a pill to search in, and the layout and sort controls inside the pill. */
@Composable
private fun KeepBar(
    q: String, onQ: (String) -> Unit, list: Boolean, onLayout: () -> Unit, sort: Int, onSort: (Int) -> Unit,
    count: Int, loading: Boolean, menu: @Composable () -> Unit, modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var sorting by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .height(56.dp).clip(RoundedCornerShape(50)).background(cs.surfaceContainerHigh).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        menu()
        Box(Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            if (q.isEmpty()) Text(if (loading) "Loading…" else "Search $count notes", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                q, onQ, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
            )
        }
        if (q.isNotEmpty()) androidx.compose.material3.IconButton({ onQ("") }) { Icon(Icons.Rounded.Close, "Clear") }
        else {
            androidx.compose.material3.IconButton(onLayout) { Icon(if (list) Icons.Rounded.GridView else Icons.Rounded.ViewAgenda, if (list) "Grid view" else "List view") }
            Box {
                androidx.compose.material3.IconButton({ sorting = true }) { Icon(Icons.Rounded.SwapVert, "Sort") }
                androidx.compose.material3.DropdownMenu(sorting, { sorting = false }) {
                    listOf("Newest first", "Oldest first", "Recently edited", "A to Z").forEachIndexed { i, t ->
                        androidx.compose.material3.DropdownMenuItem(
                            { Text(t) }, { sorting = false; onSort(i) },
                            trailingIcon = { if (sort == i) Icon(Icons.Rounded.Check, null) },
                        )
                    }
                }
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
