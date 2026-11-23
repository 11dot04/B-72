@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Assistant
import com.b72.keep_canvas.data.Shot
import com.b72.keep_canvas.data.ShotInfo
import com.b72.keep_canvas.data.Shots
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Small decoded copies of screenshots, kept for a while so a grid scrolls without re-reading files. */
private object ShotBitmaps {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(48) {}
    fun peek(uri: Uri, px: Int): ImageBitmap? = cache.get("$uri@$px")
    suspend fun load(ctx: android.content.Context, uri: Uri, px: Int): ImageBitmap? {
        peek(uri, px)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
                var sample = 1
                while (o.outHeight / (sample * 2) >= px && o.outWidth / (sample * 2) >= px / 2) sample *= 2
                val d = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, d) }?.asImageBitmap()
            }.getOrNull()?.also { cache.put("$uri@$px", it) }
        }
    }
}

@Composable
private fun ShotImage(shot: Shot, px: Int, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(ShotBitmaps.peek(shot.uri, px), shot.id) { value = ShotBitmaps.load(ctx, shot.uri, px) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = scale) }
    }
}

private fun categoryLabel(c: String) = when (c) {
    "otp" -> "One-time code"; "payment" -> "Payment"; "receipt" -> "Receipt"; "delivery" -> "Delivery"; "ride" -> "Ride"; "ticket" -> "Ticket"
    "chat" -> "Chat"; "map" -> "Map"; "article" -> "Article"; "recipe" -> "Recipe"; "product" -> "Product"; "social" -> "Social"; "code" -> "Code"
    "settings" -> "Settings"; "image" -> "Image"; else -> c.replaceFirstChar { it.uppercase() }.ifEmpty { "Other" }
}

private fun needsPermission(ctx: android.content.Context): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * Screenshots, after Pixel Screenshots: each one is read on the phone (its text, what it is, whether it only mattered once), then you
 * go through them Tinder-style, right to keep and left to delete. Kept ones can be searched by what is written in them. Anything queued
 * or suggested for deletion waits in Clean up until you confirm, and the system asks once more before a file goes.
 */
@Composable
fun ShotsScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(needsPermission(ctx)) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var shots by remember { mutableStateOf<List<Shot>>(emptyList()) }
    var reading by remember { mutableStateOf<Pair<Int, Int>?>(null) } // done of total
    var scanned by remember { mutableStateOf(false) }
    var tab by UiPrefs.shotTab

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        Shots.load(ctx)
        shots = Shots.scan(ctx); scanned = true
        val todo = shots.filter { it.id !in Shots.infos }
        if (todo.isEmpty()) return@LaunchedEffect
        val session = if (Assistant.installed) withContext(Dispatchers.Default) { Assistant.open(ctx.applicationContext) } else null
        try {
            todo.forEachIndexed { i, s ->
                reading = i to todo.size
                Shots.infos[s.id] = Shots.analyse(ctx, s, session)
                if (i % 5 == 4) Shots.save(ctx)
            }
        } finally { session?.close(); Shots.save(ctx); reading = null }
    }

    val infos = Shots.infos
    val queue = shots.filter { infos[it.id]?.state == "new" }
    val kept = shots.filter { infos[it.id]?.state == "kept" }
    val queued = shots.filter { infos[it.id]?.state == "drop" }
    val suggested = shots.filter { infos[it.id]?.let { i -> i.oneTime && i.state != "drop" && i.state != "kept" } == true }
    val cleanCount = queued.size + suggested.size

    // deleting: the system shows its own confirmation; what it deleted is dropped from our list afterwards
    var pending by remember { mutableStateOf<List<Shot>>(emptyList()) }
    val deleter = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            Shots.forget(ctx, pending.map { it.id }); shots = shots.filter { s -> pending.none { it.id == s.id } }
            scope.launch { snack.showSnackbar("Deleted ${pending.size}") }
        } else scope.launch { snack.showSnackbar("Nothing was deleted") }
    }
    fun delete(list: List<Shot>) {
        if (list.isEmpty()) return
        pending = list
        val sender = Shots.deleteRequest(ctx, list)
        if (sender != null) deleter.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
        else {
            val n = Shots.deleteDirect(ctx, list)
            Shots.forget(ctx, list.map { it.id }); shots = shots.filter { s -> list.none { it.id == s.id } }
            scope.launch { snack.showSnackbar(if (n > 0) "Deleted $n" else "Could not delete") }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp)) {
            TabTitle("Screenshots", shots.size.takeIf { it > 0 }, Modifier.padding(horizontal = 20.dp))
            if (!granted) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Go through your screenshots, keep the ones worth keeping, and let the rest go. Everything is read on this phone.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                    Button(onClick = { ask.launch(needsPermission(ctx)) }, modifier = Modifier.padding(top = 20.dp)) { Text("Allow access to screenshots") }
                }
            } else {
                FitTabs(listOf("Review", "Kept", "Clean up"), listOf(queue.size, kept.size, cleanCount).map { it.toString() }, tab, { tab = it }, Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
                reading?.let { (d, t) ->
                    Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(22.dp))
                        Text("READING ${d + 1} OF $t", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> ReviewPane(queue, onDecide = { s, st -> Shots.setState(ctx, s.id, st) }, onUndo = { id -> Shots.setState(ctx, id, "new") }, scanned = scanned && reading == null)
                        1 -> KeptPane(kept, infos, onDelete = { delete(listOf(it)) }, onBackToReview = { Shots.setState(ctx, it.id, "new") }, onCollect = { sh, n -> Shots.setCollection(ctx, sh.id, n) })
                        else -> CleanPane(queued, suggested, infos, onDelete = { delete(it) }, onKeep = { Shots.setState(ctx, it.id, "kept") })
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 110.dp))
    }
}

/** The deck: the top screenshot follows a finger, goes right to keep or left to delete; the next one waits behind it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewPane(queue: List<Shot>, onDecide: (Shot, String) -> Unit, onUndo: (Long) -> Unit, scanned: Boolean) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val wPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val x = remember { Animatable(0f) }
    var gone by remember { mutableStateOf<Long?>(null) }
    val history = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    val shown = queue.filter { it.id != gone }
    val top = shown.firstOrNull()
    if (top == null) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (scanned) "All caught up" else "Looking for screenshots…", style = flexStyle(32.sp, wght = 650, lh = 1.1f), color = cs.onSurface)
            Text(if (scanned) "New screenshots will turn up here once they have been read." else "", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            if (history.isNotEmpty()) TextButton(onClick = { onUndo(history.removeAt(history.lastIndex)) }) { Text("Undo the last one") }
        }
        return
    }
    val info = Shots.infos[top.id]
    val thr = wPx * .3f
    val ticks = rememberTicks()
    // a tick as the card passes the point where letting go will decide it
    LaunchedEffect(top.id) { androidx.compose.runtime.snapshotFlow { abs(x.value) > thr }.distinctUntilChanged().collect { if (it) ticks.threshold() } }
    fun fling(dir: Int) {
        if (dir > 0) ticks.confirm() else ticks.reject()
        scope.launch {
            x.animateTo(dir * wPx * 1.4f, tween(220))
            gone = top.id; history.add(top.id)
            onDecide(top, if (dir > 0) "kept" else "drop")
            x.snapTo(0f)
        }
    }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            // the next card, waiting
            shown.getOrNull(1)?.let { nx ->
                ShotImage(nx, 900, Modifier.fillMaxSize().graphicsLayer {
                    val k = (abs(x.value) / thr).coerceIn(0f, 1f); scaleX = .93f + .07f * k; scaleY = .93f + .07f * k; alpha = .7f + .3f * k
                }.clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHighest), ContentScale.Fit)
            }
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { translationX = x.value; rotationZ = x.value / wPx * 14f }
                    .clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHighest)
                    .pointerInput(top.id) {
                        detectDragGestures(
                            onDrag = { c, d -> c.consume(); scope.launch(start = CoroutineStart.UNDISPATCHED) { x.snapTo(x.value + d.x) } },
                            onDragEnd = { if (abs(x.value) > thr) fling(if (x.value > 0) 1 else -1) else scope.launch { x.animateTo(0f, spring(.7f, 300f)) } },
                            onDragCancel = { scope.launch { x.animateTo(0f, spring(.7f, 300f)) } },
                        )
                    },
            ) {
                Column(Modifier.fillMaxSize()) {
                    // the whole screenshot, letterboxed when its shape is not the card's
                    Box(Modifier.weight(1f).fillMaxWidth().background(cs.surfaceContainerHighest)) { ShotImage(top, 1400, Modifier.fillMaxSize(), ContentScale.Fit) }
                    // what the phone made of it, in a panel of its own under the picture (tap the sentence to read all of it)
                    var more by remember(top.id) { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = 18.dp, vertical = 14.dp)) {
                        if (info == null) Text("Reading\u2026", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        else {
                            Text(info.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = flexStyle(22.sp, wght = 660, lh = 1.1f), color = cs.onSurface)
                            if (info.summary.isNotEmpty()) Text(
                                info.summary, maxLines = if (more) 8 else 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp).clickable(onClickLabel = if (more) "Show less" else "Show all") { more = !more },
                            )
                            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(categoryLabel(info.category), style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer, modifier = Modifier.clip(CircleShape).background(cs.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp))
                                if (info.oneTime) Text("Probably only needed once", style = MaterialTheme.typography.labelMedium, color = cs.onTertiaryContainer, modifier = Modifier.clip(CircleShape).background(cs.tertiaryContainer).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
                // the verdict, fading in as the card is pulled
                Text("KEEP", maxLines = 1, softWrap = false, style = flexStyle(30.sp, wght = 800, lh = 1f), color = cs.primary, modifier = Modifier.align(Alignment.TopStart).padding(22.dp).graphicsLayer { alpha = (x.value / thr).coerceIn(0f, 1f); rotationZ = -10f }.border(3.dp, cs.primary, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp))
                Text("DELETE", maxLines = 1, softWrap = false, style = flexStyle(30.sp, wght = 800, lh = 1f), color = cs.error, modifier = Modifier.align(Alignment.TopEnd).padding(22.dp).graphicsLayer { alpha = (-x.value / thr).coerceIn(0f, 1f); rotationZ = 10f }.border(3.dp, cs.error, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 118.dp), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = { fling(-1) }, Modifier.size(64.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer)) { Icon(Icons.Rounded.Close, "Delete", Modifier.size(30.dp)) }
            FilledTonalIconButton(onClick = { if (history.isNotEmpty()) onUndo(history.removeAt(history.lastIndex)) }, enabled = history.isNotEmpty()) { Icon(Icons.Rounded.Undo, "Undo") }
            FilledIconButton(onClick = { fling(1) }, Modifier.size(64.dp)) { Icon(Icons.Rounded.Favorite, "Keep", Modifier.size(28.dp)) }
        }
    }
}

/** A screenshot's card keeps phone-like proportions however odd the picture is: a very wide or very tall one is shown whole inside it, not stretched across the screen. */
private fun cardAspect(s: Shot) = (s.w.toFloat() / s.h.coerceAtLeast(1)).coerceIn(.46f, .75f)
private fun fitsCard(s: Shot): Boolean { val a = s.w.toFloat() / s.h.coerceAtLeast(1); return a in .46f..0.75f }

/**
 * The ones kept. Search by what is written in them, or ask a question in your own words and get the answer in a sentence, drawn only from
 * your screenshots. Filter by what they are, and gather them into collections. Opening one shows what the phone made of it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeptPane(kept: List<Shot>, infos: Map<Long, ShotInfo>, onDelete: (Shot) -> Unit, onBackToReview: (Shot) -> Unit, onCollect: (Shot, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var col by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Shot?>(null) }
    var answer by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<Shot>>(emptyList()) }
    var asking by remember { mutableStateOf(false) }
    val cats = kept.mapNotNull { infos[it.id]?.category }.distinct()
    val collections = kept.mapNotNull { infos[it.id]?.col?.takeIf { c -> c.isNotBlank() } }.distinct().sorted()
    val words = q.trim().lowercase().split(' ').filter { it.isNotBlank() }
    val list = kept.filter { s ->
        val i = infos[s.id] ?: return@filter false
        (col == null || i.col == col) && (cat == null || i.category == cat) &&
            (words.isEmpty() || words.all { w -> i.title.lowercase().contains(w) || i.summary.lowercase().contains(w) || i.text.lowercase().contains(w) || i.tags.any { it.contains(w) } })
    }
    val shown = if (answer != null) matches else list

    fun ask() {
        if (asking || q.isBlank()) return
        asking = true; answer = null
        val question = q.trim()
        scope.launch {
            val ws = question.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
            val ranked = kept.map { s ->
                val i = infos[s.id]
                val t = if (i == null) "" else (i.title + " " + i.summary + " " + i.text + " " + i.tags.joinToString(" ")).lowercase()
                s to ws.count { it in t }
            }.filter { it.second > 0 }.sortedByDescending { it.second }.take(8).map { it.first }
            matches = ranked
            answer = when {
                ranked.isEmpty() -> "None of your kept screenshots mention that."
                !Assistant.installed && !com.b72.keep_canvas.data.Cloud.on -> "These look closest. Switch on the assistant (side menu, Settings) to get an answer in a sentence."
                else -> {
                    val body = ranked.mapIndexed { n, s -> val i = infos[s.id]; "[${n + 1}] ${i?.title.orEmpty()}: ${i?.summary.orEmpty()} ${i?.text.orEmpty().replace('\n', ' ').take(350)}" }.joinToString("\n")
                    Assistant.generate(ctx, "Answer the question using ONLY the text of these phone screenshots. One or two plain sentences, the answer first, no preamble. If they do not contain the answer, say so.\nQuestion: $question\nScreenshots:\n$body")
                        ?: "Could not answer just now. The matching screenshots are below."
                }
            }
            asking = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            q, { q = it; answer = null }, singleLine = true, placeholder = { Text("Search, or ask in your own words") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { IconButton(onClick = { ask() }, enabled = q.isNotBlank() && !asking) { Icon(Icons.Rounded.AutoAwesome, "Ask") } },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { if (q.trim().endsWith("?")) ask() }),
            shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        if (cats.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in cats) FilterChip(cat == c, { cat = if (cat == c) null else c }, label = { Text(categoryLabel(c)) })
        }
        if (asking) LoadingIndicator(Modifier.padding(20.dp).size(40.dp).align(Alignment.CenterHorizontally))
        LazyVerticalGrid(
            GridCells.Fixed(2), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f),
        ) {
            // the answer, in the manner of Pixel Screenshots: a sentence, where it came from, and what it came from
            answer?.let { a ->
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Text("FROM YOUR SCREENSHOTS", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant)
                        Text(a, style = flexStyle(24.sp, wght = 480, lh = 1.2f), color = cs.onSurface, modifier = Modifier.padding(top = 8.dp))
                        Text("Written by AI from the text in your screenshots. It may be wrong; check the ones below.", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            // collections, as stacks of what is in them
            if (answer == null && q.isBlank() && col == null && collections.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Column {
                    Text("COLLECTIONS", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (name in collections) {
                            val inside = kept.filter { infos[it.id]?.col == name }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).clickable { col = name }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.width(96.dp).height(64.dp)) {
                                    inside.take(3).forEachIndexed { n, s ->
                                        ShotImage(s, 240, Modifier.padding(start = (n * 24).dp).width(48.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).border(2.dp, cs.surfaceContainerHigh, RoundedCornerShape(10.dp)))
                                    }
                                }
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${inside.size} screenshot${if (inside.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Text("ALL", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp))
                }
            }
            col?.let { c ->
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { col = null }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Close, "Leave the collection", Modifier.size(20.dp))
                        Text(c, style = flexStyle(26.sp, wght = 680, lh = 1.1f), modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            if (shown.isEmpty() && !asking) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Text(if (kept.isEmpty()) "Nothing kept yet. Swipe right on a screenshot in Review." else "Nothing matches.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            }
            items(shown, key = { it.id }) { s ->
                val i = infos[s.id]
                Column(Modifier.clickable(onClickLabel = "Open") { open = s }) {
                    ShotImage(s, 500, Modifier.fillMaxWidth().aspectRatio(.62f).clip(RoundedCornerShape(22.dp)))
                    Text(i?.title.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    Text(categoryLabel(i?.category.orEmpty()).uppercase(), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant)
                }
            }
        }
    }
    open?.let { s ->
        val i = infos[s.id]
        var naming by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { open = null }) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(max = 380.dp).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    ShotImage(s, 1400, Modifier.fillMaxWidth().heightIn(max = 380.dp), ContentScale.Fit)
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = {
                        runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, s.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null)) }
                    }) { Icon(Icons.Rounded.Share, "Share") }
                    FilledTonalIconButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(s.uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } }) { Icon(Icons.Rounded.OpenInNew, "Open in another app") }
                    FilledTonalIconButton(onClick = { onBackToReview(s); open = null }) { Icon(Icons.Rounded.Undo, "Review again") }
                    FilledTonalIconButton(onClick = { onDelete(s); open = null }) { Icon(Icons.Rounded.Delete, "Delete", tint = cs.error) }
                }
                Text(i?.title.orEmpty(), style = flexStyle(26.sp, wght = 700, lh = 1.1f), modifier = Modifier.padding(top = 14.dp))
                if (!i?.summary.isNullOrEmpty()) Text(i!!.summary, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (i != null) Text(categoryLabel(i.category), style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer, modifier = Modifier.clip(CircleShape).background(cs.secondaryContainer).padding(horizontal = 12.dp, vertical = 5.dp))
                    if (i?.oneTime == true) Text("Probably only needed once", style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer, modifier = Modifier.clip(CircleShape).background(cs.tertiaryContainer).padding(horizontal = 12.dp, vertical = 5.dp))
                    i?.tags?.forEach { Text("#$it", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 5.dp)) }
                }
                Text("COLLECTION", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in collections) FilterChip(i?.col == c, { onCollect(s, if (i?.col == c) "" else c) }, label = { Text(c) })
                    AssistChip(onClick = { naming = true }, label = { Text("New collection") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
                }
                // everything the phone read in it is kept for search, but it is not laid out here unless asked for
                var showText by remember(s.id) { mutableStateOf(false) }
                if (!i?.text.isNullOrBlank()) {
                    TextButton(onClick = { showText = !showText }, Modifier.padding(top = 6.dp)) { Text(if (showText) "Hide the text in it" else "Show the text in it") }
                    androidx.compose.animation.AnimatedVisibility(showText) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(i!!.text, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerLow).padding(14.dp))
                        }
                    }
                }
            }
        }
        if (naming) {
            var name by remember { mutableStateOf("") }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { naming = false }, title = { Text("New collection") },
                text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Trip to Spain") }) },
                confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCollect(s, name); naming = false }, enabled = name.isNotBlank()) { Text("Add") } },
                dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
            )
        }
    }
}

/** What is waiting to go: the ones swiped left, and the ones that look like they only mattered once. Nothing leaves until Delete is pressed and the system agrees. */
@Composable
private fun CleanPane(queued: List<Shot>, suggested: List<Shot>, infos: Map<Long, ShotInfo>, onDelete: (List<Shot>) -> Unit, onKeep: (Shot) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val chosen = remember { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }
    fun on(s: Shot, queuedOne: Boolean) = chosen[s.id] ?: queuedOne
    val picked = queued.filter { on(it, true) } + suggested.filter { on(it, false) }
    Box(Modifier.fillMaxSize()) {
        if (queued.isEmpty() && suggested.isEmpty()) Text("Nothing to clear. Screenshots swiped left, and ones that only mattered once (a payment, a code, a delivery), wait here.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        else LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 190.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (queued.isNotEmpty()) item { Text("YOU SWIPED LEFT", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp)) }
            items(queued, key = { "q${it.id}" }) { s -> CleanRow(s, infos[s.id], on(s, true), { chosen[s.id] = it }, "Keep", { onKeep(s) }) }
            if (suggested.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("PROBABLY ONLY NEEDED ONCE", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { suggested.forEach { chosen[it.id] = true } }) { Text("Select all") }
                }
            }
            items(suggested, key = { "s${it.id}" }) { s -> CleanRow(s, infos[s.id], on(s, false), { chosen[s.id] = it }, "Keep", { onKeep(s) }) }
        }
        if (picked.isNotEmpty()) ExtendedFloatingActionButton(
            text = { Text("Delete ${picked.size}") }, icon = { Icon(Icons.Rounded.Delete, null) }, onClick = { onDelete(picked) },
            containerColor = cs.errorContainer, contentColor = cs.onErrorContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 110.dp),
        )
    }
}

@Composable
private fun CleanRow(s: Shot, i: ShotInfo?, on: Boolean, onToggle: (Boolean) -> Unit, keepLabel: String, onKeep: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerHigh).clickable { onToggle(!on) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ShotImage(s, 300, Modifier.width(56.dp).height(84.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(i?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(i?.summary.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Text(categoryLabel(i?.category.orEmpty()).uppercase(), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Checkbox(on, { onToggle(it) })
            TextButton(onClick = onKeep) { Text(keepLabel) }
        }
    }
}
