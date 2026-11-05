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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
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
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf("Review" to queue.size, "Kept" to kept.size, "Clean up" to cleanCount).forEachIndexed { i, (label, n) ->
                        Row(Modifier.clickable(onClickLabel = label) { tab = i }, verticalAlignment = Alignment.CenterVertically) {
                            val wd by androidx.compose.animation.core.animateFloatAsState(if (tab == i) 100f else 52f, spring(.9f, 300f), label = "wdth")
                            Text(label, maxLines = 1, softWrap = false, style = rflexStyle(22.sp, wght = if (tab == i) 620 else 450, wdth = wd, lh = 1f), color = if (tab == i) cs.onSurface else cs.onSurface.copy(alpha = .4f))
                            Text("$n", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
                reading?.let { (d, t) ->
                    Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(22.dp))
                        Text("READING ${d + 1} OF $t", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> ReviewPane(queue, onDecide = { s, st -> Shots.setState(ctx, s.id, st) }, onUndo = { id -> Shots.setState(ctx, id, "new") }, scanned = scanned && reading == null)
                        1 -> KeptPane(kept, infos, onDelete = { delete(listOf(it)) }, onBackToReview = { Shots.setState(ctx, it.id, "new") })
                        else -> CleanPane(queued, suggested, infos, onDelete = { delete(it) }, onKeep = { Shots.setState(ctx, it.id, "kept") })
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 110.dp))
    }
}

/** The deck: the top screenshot follows a finger, goes right to keep or left to delete; the next one waits behind it. */
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
    fun fling(dir: Int) {
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
                ShotImage(nx, 900, Modifier.fillMaxHeight().aspectRatio(nx.w.toFloat() / nx.h.coerceAtLeast(1), matchHeightConstraintsFirst = true).graphicsLayer {
                    val k = (abs(x.value) / thr).coerceIn(0f, 1f); scaleX = .93f + .07f * k; scaleY = .93f + .07f * k; alpha = .7f + .3f * k
                }.clip(RoundedCornerShape(28.dp)))
            }
            Box(
                Modifier.fillMaxHeight().aspectRatio(top.w.toFloat() / top.h.coerceAtLeast(1), matchHeightConstraintsFirst = true)
                    .graphicsLayer { translationX = x.value; rotationZ = x.value / wPx * 14f }
                    .clip(RoundedCornerShape(28.dp))
                    .pointerInput(top.id) {
                        detectDragGestures(
                            onDrag = { c, d -> c.consume(); scope.launch(start = CoroutineStart.UNDISPATCHED) { x.snapTo(x.value + d.x) } },
                            onDragEnd = { if (abs(x.value) > thr) fling(if (x.value > 0) 1 else -1) else scope.launch { x.animateTo(0f, spring(.7f, 300f)) } },
                            onDragCancel = { scope.launch { x.animateTo(0f, spring(.7f, 300f)) } },
                        )
                    },
            ) {
                ShotImage(top, 1400, Modifier.fillMaxSize())
                // what the phone made of it, over the foot of the picture
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = .55f)).padding(14.dp)) {
                    if (info != null) {
                        Text(info.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = flexStyle(20.sp, wght = 640, lh = 1.1f), color = Color.White)
                        if (info.summary.isNotEmpty()) Text(info.summary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .85f), modifier = Modifier.padding(top = 2.dp))
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(categoryLabel(info.category), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .2f)).padding(horizontal = 9.dp, vertical = 3.dp))
                            if (info.oneTime) Text("Probably only needed once", style = MaterialTheme.typography.labelSmall, color = cs.onTertiaryContainer, modifier = Modifier.clip(CircleShape).background(cs.tertiaryContainer).padding(horizontal = 9.dp, vertical = 3.dp))
                        }
                    }
                }
                // the verdict, fading in as the card is pulled
                Text("KEEP", style = flexStyle(34.sp, wght = 800, lh = 1f), color = cs.primary, modifier = Modifier.align(Alignment.TopStart).padding(22.dp).graphicsLayer { alpha = (x.value / thr).coerceIn(0f, 1f); rotationZ = -10f }.border(3.dp, cs.primary, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp))
                Text("DELETE", style = flexStyle(34.sp, wght = 800, lh = 1f), color = cs.error, modifier = Modifier.align(Alignment.TopEnd).padding(22.dp).graphicsLayer { alpha = (-x.value / thr).coerceIn(0f, 1f); rotationZ = 10f }.border(3.dp, cs.error, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 118.dp), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = { fling(-1) }, Modifier.size(64.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer)) { Icon(Icons.Rounded.Close, "Delete", Modifier.size(30.dp)) }
            FilledTonalIconButton(onClick = { if (history.isNotEmpty()) onUndo(history.removeAt(history.lastIndex)) }, enabled = history.isNotEmpty()) { Icon(Icons.Rounded.Undo, "Undo") }
            FilledIconButton(onClick = { fling(1) }, Modifier.size(64.dp)) { Icon(Icons.Rounded.Favorite, "Keep", Modifier.size(28.dp)) }
        }
    }
}

/** The ones kept: a grid that can be searched by anything written in the screenshot, its title or its tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeptPane(kept: List<Shot>, infos: Map<Long, ShotInfo>, onDelete: (Shot) -> Unit, onBackToReview: (Shot) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Shot?>(null) }
    val cats = kept.mapNotNull { infos[it.id]?.category }.distinct()
    val list = kept.filter { s ->
        val i = infos[s.id] ?: return@filter false
        (cat == null || i.category == cat) && (q.isBlank() || q.trim().lowercase().split(' ').all { w -> i.title.lowercase().contains(w) || i.summary.lowercase().contains(w) || i.text.lowercase().contains(w) || i.tags.any { it.contains(w) } })
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search what is written in them") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        if (cats.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in cats) FilterChip(cat == c, { cat = if (cat == c) null else c }, label = { Text(categoryLabel(c)) })
        }
        if (list.isEmpty()) Text(if (kept.isEmpty()) "Nothing kept yet. Swipe right on a screenshot in Review." else "Nothing matches.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        else LazyVerticalGrid(GridCells.Fixed(2), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { s ->
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
        val ctx = LocalContext.current
        ModalBottomSheet(onDismissRequest = { open = null }) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                ShotImage(s, 1400, Modifier.fillMaxWidth().aspectRatio(s.w.toFloat() / s.h.coerceAtLeast(1)).clip(RoundedCornerShape(24.dp)), ContentScale.Fit)
                Text(i?.title.orEmpty(), style = flexStyle(26.sp, wght = 700, lh = 1.1f), modifier = Modifier.padding(top = 14.dp))
                if (!i?.summary.isNullOrEmpty()) Text(i!!.summary, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { i?.tags?.forEach { Text("#$it", style = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp)) } }
                if (!i?.text.isNullOrBlank()) androidx.compose.foundation.text.selection.SelectionContainer { Text(i!!.text, style = MaterialTheme.typography.bodySmall, color = cs.onSurface, modifier = Modifier.padding(top = 14.dp)) }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(s.uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } }) { Text("Open") }
                    androidx.compose.material3.OutlinedButton(onClick = { onBackToReview(s); open = null }) { Text("Review again") }
                    TextButton(onClick = { onDelete(s); open = null }) { Text("Delete", color = cs.error) }
                }
            }
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
