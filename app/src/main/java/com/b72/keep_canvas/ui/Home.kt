@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
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
import com.b72.keep_canvas.data.matchesQuery
import com.b72.keep_canvas.data.searchText
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
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

/** Films and books are portrait posters, tracks are squares, everything else follows its photo. */
private fun tileRatio(n: Note): Double {
    val c = n.cover ?: return 1.0
    return when (c.kind) {
        Kind.Film, Kind.Book -> 1.5
        Kind.Track -> 1.0
        else -> snap(c.aspect.coerceIn(.8, 1.6))
    }
}

@Composable
fun HomeScreen(repo: Repo, loaded: Loaded?, grid: LazyStaggeredGridState, shared: SharedCtx, onOpen: (String) -> Unit, onAdd: (Kind) -> Unit, onTag: (String) -> Unit = {}, onStickers: () -> Unit = {}) {
  var menu by remember { mutableStateOf(false) }
  var q by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
  val texts = remember(loaded?.library) { loaded?.library?.notes.orEmpty().associate { it.id to it.searchText() } }
  val shown = remember(loaded?.library, q) { loaded?.library?.notes.orEmpty().asReversed().filter { q.isBlank() || matchesQuery(texts[it.id].orEmpty(), q) } }
  val revisit = remember(loaded?.library) { pickRevisit(loaded?.library?.notes.orEmpty()) }
  BackHandler(menu) { menu = false }
  Box(Modifier.fillMaxSize()) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { inner ->
        LazyVerticalStaggeredGrid(
            state = grid,
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 112.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (loaded == null) androidx.compose.material3.LoadingIndicator(Modifier.padding(end = 12.dp).size(40.dp))
                        Text("Collection", style = flexStyle(44.sp, wght = 700, wdth = 112f, rond = 40f), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        BackupMenu(repo, onStickers)
                    }
                    val line = when {
                        loaded == null -> "Loading…"
                        loaded.error != null -> loaded.error
                        loaded.source == Source.ImportedFromFlutter -> "Brought over ${loaded.library.notes.size} notes from the previous version."
                        else -> "${loaded.library.notes.size} notes"
                    }
                    Text(line ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    if (repo.prepTotal > 0) Column(Modifier.padding(top = 12.dp)) {
                        androidx.compose.material3.LinearWavyProgressIndicator(
                            progress = { (repo.prepDone.toFloat() / repo.prepTotal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Reading your photos ${repo.prepDone} of ${repo.prepTotal}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    androidx.compose.material3.TextField(
                        q, { q = it }, Modifier.fillMaxWidth().padding(top = 16.dp), singleLine = true,
                        placeholder = { Text("Search notes, tags and text in photos") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (q.isNotEmpty()) androidx.compose.material3.IconButton({ q = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                        shape = RoundedCornerShape(28.dp),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                    // every tag is the door to its canvas
                    val tags = remember(loaded?.library) {
                        loaded?.library?.notes.orEmpty().flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedBy { it.key.lowercase() }
                    }
                    if (tags.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (e in tags) item(key = e.key) {
                            androidx.compose.material3.AssistChip(onClick = { onTag(e.key) }, label = { Text("${e.key} · ${e.value}") })
                        }
                    }
                }
            }
            // one older note, different each day: a second mind brings things back
            if (revisit != null && q.isBlank()) item(span = StaggeredGridItemSpan.FullLine) { Revisit(revisit.first, revisit.second) { onOpen(revisit.first.id) } }
            if (loaded != null) {
                items(shown, key = { it.id }) { n -> Tile(n, shared) { onOpen(n.id) } }
                if (shown.isEmpty() && q.isNotBlank()) item(span = StaggeredGridItemSpan.FullLine) {
                    Text("Nothing matches “$q”", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }

    if (menu) Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menu = false },
    )
    FloatingActionButtonMenu(
        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
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
        if (days >= 7 && n.title.isNotBlank()) n to days else null
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
            Text(n.title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = flexStyle(22.sp, wght = 680, rond = 40f), color = cs.onTertiaryContainer)
        }
        Icon(Icons.Rounded.History, null, tint = cs.onTertiaryContainer)
    }
}

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
    Box(
        Modifier
            .fillMaxWidth()
            .scale(press)
            .aspectRatio((1 / ratio).toFloat())
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClickLabel = "Open note", onClick = onClick),
    ) {
        img?.let { Image(it, null, Modifier.fillMaxSize().shared(shared, "img-${n.id}"), contentScale = ContentScale.Crop) }
        val t = n.title
        if (t.isNotEmpty()) {
            val onPhoto = coverPath != null
            Box(
                Modifier
                    .align(if (onPhoto) Alignment.BottomStart else Alignment.TopStart)
                    .fillMaxWidth()
                    .then(if (onPhoto) Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))) else Modifier)
                    .padding(14.dp),
            ) {
                // every title fills its tile's width in its own look; the same title flexes on the note page
                FitTitle(t, if (onPhoto) Color.White else fg, n.id, Modifier.shared(shared, "title-${n.id}", text = true), maxLines = 3)
            }
        }
        if (img == null) {
            val sum = remember(n) {
                n.blocks.firstOrNull { it.kind in setOf(Kind.Quote, Kind.Stat, Kind.Info, Kind.Poster, Kind.Link) }
                    ?.let { com.b72.keep_canvas.data.tplSummary(it.kind, it.title, it.body) }.orEmpty()
            }
            if (sum.isNotEmpty()) Text(
                sum, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = .8f),
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            )
        }
    }
}

private fun seedMod(id: String, n: Int) = com.b72.keep_canvas.data.seedOf(id) % n

/** Export and restore the whole collection as one zip, through the system file picker. */
@Composable
private fun BackupMenu(repo: Repo, onStickers: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
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
        androidx.compose.material3.IconButton({ open = true }) { Icon(androidx.compose.material.icons.Icons.Rounded.MoreVert, "More") }
        androidx.compose.material3.DropdownMenu(open, { open = false }) {
            androidx.compose.material3.DropdownMenuItem({ Text("Stickers") }, { open = false; onStickers() })
            androidx.compose.material3.DropdownMenuItem({ Text("Export backup") }, { open = false; save.launch("b72-backup.zip") })
            androidx.compose.material3.DropdownMenuItem({ Text("Restore backup") }, { open = false; load.launch(arrayOf("application/zip", "application/octet-stream")) })
        }
    }
}
