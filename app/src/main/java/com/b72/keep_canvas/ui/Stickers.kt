@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.Loaded
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.data.imageDir
import com.b72.keep_canvas.data.makeStickers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.b72.keep_canvas.data.Vision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


private val names = HashMap<String, String>()

/** Stamps are the artifacts a photo with no single subject becomes; everything else is a cut-out sticker. */
private fun isStamp(path: String) = path.substringAfterLast('/').startsWith("st_")

/** What the thing is, from the on-device labeller (nothing leaves the phone): its best label, or a plain word for what it is. */
private suspend fun nameOf(ctx: android.content.Context, path: String): String {
    names[path]?.let { return it }
    val fallback = if (isStamp(path)) "Postage stamp" else "Sticker"
    val label = if (isStamp(path)) null else Vision.labels(ctx, path)?.split(',')?.firstOrNull()?.substringBeforeLast(':')?.trim()?.takeIf { it.isNotEmpty() }
    return (label?.replaceFirstChar { it.uppercase() } ?: fallback).also { names[path] = it }
}

private fun ago(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when {
        m < 1 -> "just now"; m < 60 -> "$m min ago"; m < 60 * 24 -> "${m / 60} h ago"; m < 60 * 24 * 14 -> "${m / (60 * 24)} d ago"
        else -> java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))
    }
}

/** The main colours of an artifact: its outline and the empty background are left out, so what is left is the thing itself. */
private suspend fun swatches(path: String): List<Int> {
    val bmp = Images.load(path, 200) ?: return emptyList()
    return withContext(Dispatchers.Default) {
        val src = bmp.asAndroidBitmap()
        val flat = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(flat); c.drawColor(android.graphics.Color.WHITE); c.drawBitmap(src, 0f, 0f, null)
        androidx.palette.graphics.Palette.from(flat).maximumColorCount(10).addFilter { _, hsl -> hsl[2] < .93f && hsl[2] > .05f }.generate()
            .swatches.sortedByDescending { it.population }.take(5).map { it.rgb }
    }
}

private class Facts(val w: Int, val h: Int, val bytes: Long, val made: Long)

/** The artifacts: a list in the manner of a scan history (the cut-out, its name, a few facts, an arrow), and a page for each one. */
@Composable
fun StickersScreen(repo: Repo, loaded: Loaded?, onBack: () -> Unit, onCanvas: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<String?>(null) }
    var tab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val paths = remember(loaded?.library?.stickers) {
        loaded?.library?.stickers.orEmpty().mapNotNull { ((it as? JsonObject)?.get("p") as? JsonPrimitive)?.content }
    }
    // which canvases each artifact has been put on
    val onCanvases = remember(loaded?.library?.placed) {
        val m = HashMap<String, MutableList<String>>()
        for ((tag, list) in loaded?.library?.placed.orEmpty()) for (st in list) if (st.k.startsWith("s:")) m.getOrPut(st.k.removePrefix("s:")) { ArrayList() }.let { if (tag !in it) it.add(tag) }
        m
    }
    val stickers = remember(paths) { paths.filter { !isStamp(it) } }
    val stamps = remember(paths) { paths.filter { isStamp(it) } }
    val placed = remember(paths, onCanvases) { paths.filter { onCanvases[it].orEmpty().isNotEmpty() } }
    val shown = when (tab) { 1 -> stamps; 2 -> placed; else -> stickers }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            var made = 0
            var unavailable = false
            var stampsMade = 0
            for (u in uris) {
                val tmp = repo.importPhoto(u, "stk${System.nanoTime()}") ?: continue
                val r = makeStickers(tmp.path!!, imageDir(ctx))
                if (r == null) unavailable = true
                else if (r.isEmpty()) com.b72.keep_canvas.data.makeStamp(tmp.path!!, imageDir(ctx))?.let { repo.addStickers(listOf(it)); stampsMade++ }
                else { repo.addStickers(r); made += r.size }
                runCatching { File(tmp.path).delete() }
            }
            busy = false
            snack.showSnackbar(
                when {
                    made > 0 || stampsMade > 0 -> listOfNotNull(if (made > 0) "$made sticker${if (made == 1) "" else "s"}" else null, if (stampsMade > 0) "$stampsMade stamp${if (stampsMade == 1) "" else "s"}" else null).joinToString(" and ").let { "Made $it" }
                    unavailable -> "The cut-out model is not ready yet. If this is the first run it may still be downloading: try again in a minute."
                    else -> "No clear subject found."
                },
            )
        }
    }
    BackHandler(view != null) { view = null }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = cs.secondaryContainer)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            }
            Text("Artifacts", style = flexStyle(52.sp, wght = 760, wdth = 92f, lh = 1f), color = cs.onSurface, modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.padding(top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf("Stickers" to stickers.size, "Stamps" to stamps.size, "On canvases" to placed.size).forEachIndexed { i, (label, n) ->
                    Row(Modifier.clickable(onClickLabel = label) { tab = i }, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = flexStyle(22.sp, wght = if (tab == i) 600 else 420, lh = 1f), color = if (tab == i) cs.onSurface else cs.onSurface.copy(alpha = .4f))
                        Text("$n", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
            if (shown.isEmpty() && !busy) {
                Text(
                    if (paths.isEmpty()) "Pick some photos. A clear subject becomes a die-cut sticker; a photo without one becomes a postage stamp." else "Nothing here yet.",
                    style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            LazyColumn(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 190.dp)) {
                itemsIndexed(shown, key = { _, p -> p }) { _, p -> ArtifactRow(p, onCanvases[p].orEmpty()) { view = p } }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            icon = { if (busy) LoadingIndicator(Modifier.size(24.dp)) else Icon(Icons.Rounded.AutoAwesome, null) },
            text = { Text(if (busy) "Making artifacts…" else "Make artifacts") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 104.dp),
        )
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 170.dp, start = 16.dp, end = 16.dp))
        view?.let { p ->
            ArtifactPage(p, onCanvases[p].orEmpty(), onBack = { view = null }, onCanvas = { view = null; onCanvas(it) }, onDelete = { repo.removeSticker(p); view = null })
        }
    }
}

@Composable
private fun ArtifactRow(path: String, tags: List<String>, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(Images.peek(path, 400), path) { value = Images.load(path, 400) }
    val name by produceState<String?>(names[path], path) { value = nameOf(ctx, path) }
    val facts by produceState<Facts?>(null, path) { value = factsOf(path) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainer).clickable(onClickLabel = "Open", onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            img?.let { Image(it, null, Modifier.fillMaxSize().rotate(((path.hashCode() % 7) - 3) * 1.7f), contentScale = ContentScale.Fit) }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(name ?: " ", style = flexStyle(22.sp, wght = 480, lh = 1.1f), color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Fact(Icons.Rounded.Dashboard, if (tags.isEmpty()) "Unused" else "${tags.size} canvas${if (tags.size == 1) "" else "es"}")
                facts?.let { Fact(Icons.Rounded.Schedule, ago(it.made)) }
            }
        }
        Box(Modifier.size(34.dp).clip(CircleShape).background(cs.surface), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp)) }
    }
}

@Composable
private fun Fact(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
    }
}

private suspend fun factsOf(path: String): Facts? = withContext(Dispatchers.IO) {
    val f = File(path)
    if (!f.exists()) return@withContext null
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, o)
    Facts(o.outWidth, o.outHeight, f.length(), f.lastModified())
}

/** One artifact on a page of its own: large, named, with its colours, where it has been used, and its plain facts. */
@Composable
private fun ArtifactPage(path: String, tags: List<String>, onBack: () -> Unit, onCanvas: (String) -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(Images.peek(path, 900), path) { value = Images.load(path, 900) }
    val name by produceState<String?>(names[path], path) { value = nameOf(ctx, path) }
    val facts by produceState<Facts?>(null, path) { value = factsOf(path) }
    val colours by produceState<List<Int>>(emptyList(), path) { value = swatches(path) }
    var confirm by remember { mutableStateOf(false) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("Delete this artifact?") },
        text = { Text("It is also taken off every canvas it is on.") },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = cs.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } },
    )
    Column(Modifier.fillMaxSize().background(cs.surfaceContainerLow).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 120.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = cs.surface)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(
                if (isStamp(path)) "Postage stamp" else "Sticker", style = MaterialTheme.typography.titleMedium, color = cs.onSurface,
                modifier = Modifier.clip(CircleShape).background(cs.surface).padding(horizontal = 16.dp, vertical = 8.dp),
            )
            IconButton(onClick = { confirm = true }, colors = IconButtonDefaults.iconButtonColors(containerColor = cs.surface)) { Icon(Icons.Rounded.Delete, "Delete") }
        }
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            img?.let { Image(it, null, Modifier.fillMaxSize().padding(12.dp).rotate(((path.hashCode() % 7) - 3) * 1.5f), contentScale = ContentScale.Fit) }
        }
        Text(name ?: " ", style = flexStyle(40.sp, wght = 480, wdth = 96f, lh = 1.08f), color = cs.onSurface, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        facts?.let { Text("Made ${ago(it.made)}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }

        InfoCard("Colours", "from the artifact", Modifier.padding(top = 22.dp)) {
            if (colours.isEmpty()) Text("Reading its colours…", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in colours) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color(c)).border(1.dp, cs.outlineVariant, CircleShape))
                    Text("#%06X".format(c and 0xFFFFFF), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        InfoCard("On canvases", if (tags.isEmpty()) "not yet" else "${tags.size}", Modifier.padding(top = 12.dp)) {
            if (tags.isEmpty()) Text("It has not been put on a canvas. Open a canvas and add it from the artifacts button.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            else tags.forEachIndexed { i, t ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant.copy(alpha = .5f)))
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Open the $t canvas") { onCanvas(t) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Dashboard, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                    Text(t.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
                }
            }
        }
        InfoCard("Details", "", Modifier.padding(top = 12.dp)) {
            val f = facts
            val rows = listOfNotNull(
                "Kind" to (if (isStamp(path)) "Postage stamp: a photo with no single subject" else "Die-cut sticker with an outline"),
                f?.let { "Size" to "${it.w} \u00d7 ${it.h} px" },
                f?.let { "File" to (if (it.bytes >= 1_048_576) "%.1f MB".format(it.bytes / 1_048_576.0) else "${it.bytes / 1024} KB") },
                f?.let { "Made" to java.time.Instant.ofEpochMilli(it.made).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", java.util.Locale.ENGLISH)) },
            )
            rows.forEachIndexed { i, (k, v) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant.copy(alpha = .5f)))
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    Text(k, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(72.dp))
                    Text(v, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, note: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surface).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = flexStyle(24.sp, wght = 480, lh = 1f), color = cs.onSurface)
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        content()
    }
}
