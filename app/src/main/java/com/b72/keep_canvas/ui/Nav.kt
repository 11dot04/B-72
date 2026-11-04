@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import com.b72.keep_canvas.data.Images
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Loaded
import com.b72.keep_canvas.data.Note

private class Dest(val label: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest("Notes", Icons.Rounded.StickyNote2),
    Dest("Canvas", Icons.Rounded.Dashboard),
    Dest("Library", Icons.Rounded.CollectionsBookmark),
    Dest("Artifacts", Icons.Rounded.EmojiEmotions),
)

/** The three top-level places as one centred tertiary pill. Depth comes from a surface-coloured halo and tone, not a shadow. */
@Composable
fun FloatingNav(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.clip(CircleShape).background(cs.tertiaryContainer).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        DESTS.forEachIndexed { i, d ->
            val on = tab == i
            val bg by animateColorAsState(if (on) cs.tertiary else Color.Transparent, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navbg")
            val fg = if (on) cs.onTertiary else cs.onTertiaryContainer
            Row(
                Modifier.clip(CircleShape).background(bg)
                    .clickable(role = Role.Tab, onClickLabel = d.label) { onTab(i) }
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                    .padding(horizontal = if (on) 18.dp else 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(d.icon, if (on) null else d.label, Modifier.size(24.dp), tint = fg)
                AnimatedVisibility(on) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        Text(d.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Every tag is a canvas; this is the page that lists them, each as a card with a little spread of its photos. */
/** The soft fade at the bottom edge shared by every tab: sharp above, melting into blur toward the bottom (Android 13+; older phones show it sharp). */
fun Modifier.bottomBlur(radius: androidx.compose.ui.unit.Dp = 5.dp, from: Float = .72f): Modifier = this.blur(
    blurRadiusSpec = BlurRadiusSpec.verticalGradient(stops = listOf(BlurStop(0f, 0.dp), BlurStop(from, 0.dp), BlurStop(1f, radius))),
    edgeTreatment = BlurredEdgeTreatment.Rectangle,
)

@Composable
fun CanvasHub(loaded: Loaded?, onRename: (String, String) -> Unit = { _, _ -> }, onTag: (String) -> Unit) {
    var renaming by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var newName by remember { androidx.compose.runtime.mutableStateOf("") }
    renaming?.let { old ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename \"$old\"") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("New name") })
                    Text("Type the name of another tag to merge the two into one.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { androidx.compose.material3.TextButton({ onRename(old, newName); renaming = null }, enabled = newName.isNotBlank()) { Text("Save") } },
            dismissButton = { androidx.compose.material3.TextButton({ renaming = null }) { Text("Cancel") } },
        )
    }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    LightStatusBar(!dark)
    val groups = remember(loaded?.library) {
        loaded?.library?.notes.orEmpty().flatMap { n -> n.tags.map { it to n } }
            .groupBy({ it.first }, { it.second }).entries.sortedByDescending { it.value.size }
    }
    // each tag's colour is the main hue of its first photo (the tag's own name picks one until that is read)
    val hues by androidx.compose.runtime.produceState<Map<String, Float>>(emptyMap(), groups.map { it.key }) {
        val out = HashMap<String, Float>()
        for (g in groups) {
            val p = g.value.firstNotNullOfOrNull { it.cover?.path } ?: continue
            hueOfPhoto(p)?.let { out[g.key] = it; value = HashMap(out) }
        }
    }
    fun hueOf(key: String) = hues[key] ?: (com.b72.keep_canvas.data.seedOf(key) % 360).toFloat()
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    val ls = androidx.compose.foundation.lazy.rememberLazyListState()
    val focus by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = ls.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: 0
        }
    }
    val focusHue = groups.getOrNull(focus)?.let { hueOf(it.key) } ?: 220f
    fun bgOf(h: Float) = if (dark) Color.hsl(h, .5f, .17f) else Color.hsl(h, .55f, .86f)
    val ink = if (dark) Color.hsl(focusHue, .35f, .93f) else Color(0xFF1B1A1F)
    // the focused tag's colour drains in as a soft gradient edge with a grain along it, rising from the bottom when moving on, falling from the top when going back
    val target = bgOf(focusHue)
    var from by remember { androidx.compose.runtime.mutableStateOf(target) }
    var to by remember { androidx.compose.runtime.mutableStateOf(target) }
    var rising by remember { androidx.compose.runtime.mutableStateOf(true) }
    var lastFocus by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val prog = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(target) {
        if (target != to) {
            from = if (prog.value >= 1f) to else androidx.compose.ui.graphics.lerp(from, to, prog.value)
            to = target
            rising = focus >= lastFocus
            prog.snapTo(0f)
            prog.animateTo(1f, androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.CubicBezierEasing(.2f, 0f, 0f, 1f)))
        }
        lastFocus = focus
    }
    val dots = remember { val r = kotlin.random.Random(72); List(1500) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawRect(from)
            val p = prog.value
            fun glow() = drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf((if (dark) Color.hsl(focusHue, .65f, .3f) else Color.hsl(focusHue, .8f, .93f)).copy(alpha = .75f), Color.Transparent),
                    center = Offset(size.width * .25f, size.height * .5f), radius = size.width * 1.1f,
                ),
            )
            if (p >= 1f) { drawRect(to); glow(); return@Canvas }
            val band = size.height * .55f
            // the edge's position: the distance the wash has travelled
            val edge = if (rising) size.height - p * (size.height + band) else -band + p * (size.height + band)
            val top = if (rising) edge else edge
            val brush = if (rising) androidx.compose.ui.graphics.Brush.verticalGradient(0f to to.copy(alpha = 0f), 1f to to, startY = top, endY = top + band)
            else androidx.compose.ui.graphics.Brush.verticalGradient(0f to to, 1f to to.copy(alpha = 0f), startY = top, endY = top + band)
            drawRect(brush)
            glow()
            // grain: specks of the new colour ahead of the edge and of the old one behind it
            val px = 2.2.dp.toPx()
            for ((rx, ry, rr) in dots) {
                val y = top - band * .25f + ry * band * 1.5f
                val t = ((y - top) / band).coerceIn(0f, 1f)
                val coverage = if (rising) t else 1f - t
                val smooth = coverage * coverage * (3 - 2 * coverage)
                drawRect(if (rr < smooth) to else from, Offset(rx * size.width, y), androidx.compose.ui.geometry.Size(px, px), alpha = (1f - kotlin.math.abs(smooth - .5f) * 1.6f).coerceIn(0f, .9f))
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
            val pad = ((maxHeight - 96.dp) / 2).coerceAtLeast(60.dp)
            val hubScope = androidx.compose.runtime.rememberCoroutineScope()
            if (groups.isEmpty()) Text(
                "Give a note a tag and its canvas appears here, with all the notes that share it laid out as a collage.",
                style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = .7f), modifier = Modifier.align(Alignment.Center).padding(32.dp),
            ) else {
                val cur = groups[focus.coerceIn(0, groups.lastIndex)]
                // the photos of the tag in the middle, thrown across the page; a tag with none leaves the page clear and no gap in the list
                androidx.compose.animation.Crossfade(cur.key, Modifier.fillMaxSize(), animationSpec = androidx.compose.animation.core.tween(320), label = "scatter") { k ->
                    val g = groups.firstOrNull { it.key == k }
                    if (g != null) ScatterPhotos(k, remember(k, g.value) { g.value.flatMap { n -> n.photos.mapNotNull { it.path } }.distinct().take(7) }, if (dark) Color.hsl(focusHue, .6f, .3f) else Color.hsl(focusHue, .7f, .8f), Modifier.fillMaxSize())
                }
                androidx.compose.foundation.lazy.LazyColumn(
                    state = ls,
                    flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(ls, androidx.compose.foundation.gestures.snapping.SnapPosition.Center),
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, .14f to Color.Black, .86f to Color.Black, 1f to Color.Transparent),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        },
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = pad, bottom = pad),
                ) {
                    itemsIndexed(groups, key = { _, g -> g.key }) { i, g ->
                        val name = remember(g.key) { g.key.replaceFirstChar { it.uppercase() } }
                        // how near the middle this name is, in 7 steps so fonts are reused
                        val step by remember(i) {
                            androidx.compose.runtime.derivedStateOf {
                                val info = ls.layoutInfo
                                val me = info.visibleItemsInfo.firstOrNull { it.index == i }
                                val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val d = if (me == null) 1f else (kotlin.math.abs(me.offset + me.size / 2f - mid) / 140f).coerceIn(0f, 1f)
                                kotlin.math.round((1f - d) * 6f).toInt()
                            }
                        }
                        // a long name takes a smaller size and a second or third line; it is never cut off
                        val big = when { name.length <= 10 -> 50f; name.length <= 18 -> 40f; else -> 32f }
                        val titleStyle = remember(step, big) { flexStyle((26f + (big - 26f) * step / 6f).sp, wght = 480 + step * 38, wdth = 100f, lh = 1.08f) }
                        val here = step >= 5
                        val arrow by androidx.compose.animation.core.animateDpAsState(if (here) 44.dp else 0.dp, androidx.compose.animation.core.spring(.7f, 400f), label = "arrow")
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp)
                                .graphicsLayer { alpha = .26f + .74f * step / 6f }
                                .combinedClickable(
                                    onClickLabel = if (here) "Open the ${g.key} canvas" else "Go to ${g.key}", onLongClickLabel = "Rename or merge",
                                    onLongClick = { newName = g.key; renaming = g.key },
                                ) {
                                    if (here) onTag(g.key) else hubScope.launch {
                                        val info = ls.layoutInfo
                                        val me = info.visibleItemsInfo.firstOrNull { it.index == i }
                                        if (me != null) ls.animateScrollBy((me.offset + me.size / 2f) - (info.viewportStartOffset + info.viewportEndOffset) / 2f) else ls.animateScrollToItem(i)
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(arrow).clipToBounds()) { Text("\u2192", style = flexStyle(36.sp, wght = 400, lh = 1f), color = ink, maxLines = 1, softWrap = false) }
                            Text(name, style = titleStyle, color = ink, maxLines = 3, modifier = Modifier.fillMaxWidth(.8f))
                        }
                    }
                }
                // what the tag in the middle holds
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 22.dp, top = 140.dp), horizontalAlignment = Alignment.End) {
                    val st = androidx.compose.ui.text.TextStyle(fontFamily = mono, fontSize = 11.sp, letterSpacing = .6.sp, color = ink.copy(alpha = .75f))
                    Text("NOTES: ${cur.value.size}", style = st)
                    Text("PHOTOS: ${cur.value.sumOf { it.photos.size }}", style = st)
                    val stars = cur.value.count { it.starred }
                    if (stars > 0) Text("STARRED: $stars", style = st)
                    val pl = cur.value.flatMap { it.places.keys }.distinct().size
                    if (pl > 0) Text("PLACES: $pl", style = st)
                }
            }
            // where you are in the list: a tick per tag, the current one long
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.End) {
                val lo = (focus - 6).coerceAtLeast(0); val hi = (focus + 6).coerceAtMost(groups.lastIndex)
                for (k in lo..hi) {
                    val wTick by androidx.compose.animation.core.animateDpAsState(if (k == focus) 18.dp else 7.dp, androidx.compose.animation.core.spring(.6f, 400f), label = "tick")
                    Box(Modifier.width(wTick).height(2.dp).clip(CircleShape).background(ink.copy(alpha = if (k == focus) .9f else .35f)))
                }
            }
            if (groups.isNotEmpty()) Text(
                "%02d / %02d".format(focus + 1, groups.size),
                style = androidx.compose.ui.text.TextStyle(fontFamily = mono, fontSize = 12.sp, letterSpacing = .8.sp, color = ink.copy(alpha = .7f)),
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 28.dp),
            )
            TabTitle("Canvases", null, Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 16.dp), ink)
        }
    }
}

/** A tag's photos thrown across the page at random, each in its own shape and tilt; the same tag always lands the same way. */
@Composable
private fun ScatterPhotos(key: String, paths: List<String>, accent: Color, modifier: Modifier) {
    val shapes = remember {
        listOf<androidx.compose.ui.graphics.Shape>(
            RoundedCornerShape(30.dp), MaterialShapes.Cookie6Sided.toShape(), MaterialShapes.Clover4Leaf.toShape(), MaterialShapes.Oval.toShape(),
            RoundedCornerShape(topStart = 90.dp, topEnd = 18.dp, bottomEnd = 90.dp, bottomStart = 18.dp), MaterialShapes.Arch.toShape(), RoundedCornerShape(50),
        )
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val w = maxWidth; val h = maxHeight
        // the page is cut into eight cells and the photos are dealt into them in a shuffled order, so they spread out instead of piling up
        class Spot(val x: Float, val y: Float, val size: Int, val rot: Float, val shape: Int)
        val spots = remember(key, paths.size) {
            val r = kotlin.random.Random(com.b72.keep_canvas.data.seedOf(key))
            val cells = (0 until 8).shuffled(r)
            List(paths.size) { i ->
                val cell = cells[i % 8]
                Spot(
                    x = (if (cell % 2 == 0) .34f else .66f) + (r.nextFloat() - .5f) * .2f,
                    y = ((cell / 2) + .2f + r.nextFloat() * .6f) / 4f,
                    size = 84 + r.nextInt(100), rot = (r.nextFloat() - .5f) * 26f, shape = r.nextInt(shapes.size),
                )
            }
        }
        paths.forEachIndexed { i, p ->
            val sp = spots[i]
            val pop = remember(p) { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(p) { kotlinx.coroutines.delay(70L * i); pop.animateTo(1f, androidx.compose.animation.core.spring(.55f, 280f)) }
            PhotoImage(
                p, 500, false, accent,
                Modifier.offset(w * sp.x - sp.size.dp / 2, h * sp.y - sp.size.dp / 2).size(sp.size.dp)
                    .graphicsLayer { val v = pop.value; alpha = v.coerceIn(0f, 1f); scaleX = .6f + .4f * v; scaleY = .6f + .4f * v; rotationZ = sp.rot * v + (1f - v) * 14f }
                    .clip(shapes[sp.shape]),
            )
        }
    }
}

@Composable
private fun CanvasCard(tag: String, notes: List<Note>, bg: Color, fg: Color, onClick: () -> Unit) {
    val covers = notes.mapNotNull { it.cover?.path }.take(3)
    val big = RoundedCornerShape(34.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(.88f).clip(big).background(bg).clickable(onClickLabel = "Open the $tag canvas", onClick = onClick),
    ) {
        covers.forEachIndexed { i, p ->
            PhotoImage(
                p, 400, false, fg,
                Modifier.align(Alignment.TopStart).padding(start = 14.dp + 38.dp * i, top = 14.dp + 6.dp * (i % 2)).size(72.dp).rotate(listOf(-6f, 5f, -3f)[i])
                    .clip(RoundedCornerShape(18.dp)),
            )
        }
        if (covers.isEmpty()) Box(
            Modifier.align(Alignment.TopEnd).padding(14.dp).size(64.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(fg.copy(alpha = .14f)),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            FitLine(tag, flexStyle(28.sp, wght = 740, rond = 40f, lh = 1f), fg)
            Text("${notes.size} note${if (notes.size == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = .75f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}
