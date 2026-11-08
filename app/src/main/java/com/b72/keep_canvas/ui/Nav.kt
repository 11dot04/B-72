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
import androidx.compose.material.icons.rounded.Screenshot
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
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Loaded
import com.b72.keep_canvas.data.Note

/** [icon] is the resting outline, [selected] the filled one that takes its place on the open tab. */
private class Dest(val label: String, val icon: ImageVector, val selected: ImageVector)

private val DESTS = listOf(
    Dest("Notes", androidx.compose.material.icons.Icons.Outlined.StickyNote2, androidx.compose.material.icons.Icons.Filled.StickyNote2),
    Dest("Canvas", androidx.compose.material.icons.Icons.Outlined.Dashboard, androidx.compose.material.icons.Icons.Filled.Dashboard),
    Dest("Library", androidx.compose.material.icons.Icons.Outlined.CollectionsBookmark, androidx.compose.material.icons.Icons.Filled.CollectionsBookmark),
    Dest("Artifacts", androidx.compose.material.icons.Icons.Outlined.EmojiEmotions, androidx.compose.material.icons.Icons.Filled.EmojiEmotions),
    Dest("Screens", androidx.compose.material.icons.Icons.Outlined.Screenshot, androidx.compose.material.icons.Icons.Filled.Screenshot),
)

/** The three top-level places as one centred tertiary pill. Depth comes from a surface-coloured halo and tone, not a shadow. */
@Composable
fun FloatingNav(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.clip(CircleShape).background(cs.tertiaryContainer).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        DESTS.forEachIndexed { i, d ->
            val on = tab == i
            val bg by animateColorAsState(if (on) cs.tertiary else Color.Transparent, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navbg")
            val fg = if (on) cs.onTertiary else cs.onTertiaryContainer
            Row(
                Modifier.clip(CircleShape).background(bg)
                    .clickable(role = Role.Tab, onClickLabel = d.label) { onTab(i) }
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                    .padding(horizontal = if (on) 16.dp else 11.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (on) d.selected else d.icon, if (on) null else d.label, Modifier.size(24.dp), tint = fg)
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
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    var hubIdx by UiPrefs.hub
    val ls = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = hubIdx)
    val focus by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = ls.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: 0
        }
    }
    // where the list was left is kept once it comes to rest
    androidx.compose.runtime.LaunchedEffect(Unit) { androidx.compose.runtime.snapshotFlow { ls.isScrollInProgress }.collect { moving -> if (!moving && ls.layoutInfo.visibleItemsInfo.isNotEmpty()) hubIdx = focus } }
    // the page is Material You's own surface, nothing tinted: the colour comes from the theme and the photos
    val ink = cs.onSurface
    Box(Modifier.fillMaxSize().background(cs.surface)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
            val pad = ((maxHeight - 96.dp) / 2).coerceAtLeast(60.dp)
            val hubScope = androidx.compose.runtime.rememberCoroutineScope()
            if (groups.isEmpty()) Text(
                "Give a note a tag and its canvas appears here, with all the notes that share it laid out as a collage.",
                style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = .7f), modifier = Modifier.align(Alignment.Center).padding(32.dp),
            ) else {
                val cur = groups[focus.coerceIn(0, groups.lastIndex)]
                val dens = androidx.compose.ui.platform.LocalDensity.current
                val tmeas = androidx.compose.ui.text.rememberTextMeasurer()
                val arrowDp = 44f
                val availDp = maxWidth.value - 40f - arrowDp
                // every name is measured once, at normal width: the biggest size at which its longest word still fits on a line (so nothing breaks
                // mid-word), and how tall that makes its row. Rows keep that height whether or not they are the chosen one, so scrolling never
                // changes a row's size, which is what made the list judder before.
                class Metric(val sp: Float, val h: Float, val w: Float)
                val metrics = remember(groups.map { it.key }, availDp) {
                    val availPx = (availDp * dens.density).toInt()
                    groups.map { g ->
                        val name = g.key.replaceFirstChar { it.uppercase() }
                        val words = name.split(' ').filter { it.isNotEmpty() }
                        var size = 46f
                        var lay = tmeas.measure(name, rflexStyle(size.sp, 640, 100f, 1.05f), constraints = androidx.compose.ui.unit.Constraints(maxWidth = availPx))
                        while (size > 24f) {
                            val st = rflexStyle(size.sp, 640, 100f, 1.05f)
                            lay = tmeas.measure(name, st, constraints = androidx.compose.ui.unit.Constraints(maxWidth = availPx))
                            val longest = words.maxOfOrNull { w -> tmeas.measure(w, st, softWrap = false, maxLines = 1).size.width } ?: 0
                            if (longest <= availPx && lay.lineCount <= 3) break
                            size -= 3f
                        }
                        Metric(size, lay.size.height / dens.density + 14f, lay.size.width / dens.density)
                    }
                }
                val sel = metrics.getOrNull(focus.coerceIn(0, groups.lastIndex))
                val cx = maxWidth.value; val cy = maxHeight.value / 2
                val avoid = remember(sel, cx, cy) {
                    val rowH = sel?.h ?: 70f
                    listOf(
                        androidx.compose.ui.geometry.Rect(0f, cy - rowH / 2 - 6, 20f + arrowDp + (sel?.w ?: 200f) + 14f, cy + rowH / 2 + 6), // the chosen name
                        androidx.compose.ui.geometry.Rect(cx - 150f, (maxHeight.value + 140f) / 2 - 44f, cx, (maxHeight.value + 140f) / 2 + 44f), // the counts
                        androidx.compose.ui.geometry.Rect(cx - 130f, 0f, cx, 64f), // 01 / 09
                        androidx.compose.ui.geometry.Rect(0f, 0f, 270f, 76f), // the page title
                        androidx.compose.ui.geometry.Rect(cx - 30f, cy - 120f, cx, cy + 120f), // the ticks
                        androidx.compose.ui.geometry.Rect(0f, maxHeight.value - 96f, cx, maxHeight.value), // under the floating bar
                    )
                }
                // the photos of the tag in the middle, placed round its name and never over it; a tag with none leaves the page clear and no gap in the list
                androidx.compose.animation.Crossfade(cur.key, Modifier.fillMaxSize(), animationSpec = androidx.compose.animation.core.tween(320), label = "scatter") { k ->
                    val g = groups.firstOrNull { it.key == k }
                    if (g != null) ScatterPhotos(k, remember(k, g.value) { g.value.flatMap { n -> n.photos.mapNotNull { it.path } }.distinct().take(7) }, avoid, androidx.compose.ui.geometry.Offset(20f + arrowDp + (sel?.w ?: 200f) / 2, cy), cs.secondaryContainer, Modifier.fillMaxSize())
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
                        val m = metrics.getOrNull(i) ?: return@itemsIndexed
                        val here = focus == i
                        // Material 3 Expressive's width axis: the chosen name is set at normal width, the rest are pulled in
                        val st = remember(m.sp, here) { rflexStyle(m.sp.sp, if (here) 640 else 470, if (here) 100f else 52f, 1.05f) }
                        val shift by androidx.compose.animation.core.animateFloatAsState(if (here) arrowDp else 0f, androidx.compose.animation.core.spring(.8f, 380f), label = "shift")
                        val fade by androidx.compose.animation.core.animateFloatAsState(if (here) 1f else .3f, androidx.compose.animation.core.spring(1f, 400f), label = "fade")
                        Box(
                            Modifier.fillMaxWidth().height(m.h.dp)
                                .then(if (here) Modifier.keyContainer("board-${g.key}", 28.dp) else Modifier)
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
                        ) {
                            Text("\u2192", style = rflexStyle(36.sp, wght = 400, lh = 1f), color = cs.primary, maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.CenterStart).graphicsLayer { alpha = (shift / arrowDp).coerceIn(0f, 1f) })
                            Text(name, style = st, color = ink, modifier = Modifier.align(Alignment.CenterStart).width(availDp.dp).graphicsLayer { translationX = shift * density; alpha = fade })
                        }
                    }
                }
                // what the tag in the middle holds, as tonal chips
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 22.dp, top = 150.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    fun chip(t: String, bgc: Color, fgc: Color) = @Composable { Text(t, style = MaterialTheme.typography.labelLarge, color = fgc, modifier = Modifier.clip(CircleShape).background(bgc).padding(horizontal = 12.dp, vertical = 5.dp)) }
                    val notesN = cur.value.size
                    chip("$notesN note${if (notesN == 1) "" else "s"}", cs.primaryContainer, cs.onPrimaryContainer)()
                    val ph = cur.value.sumOf { it.photos.size }
                    if (ph > 0) chip("$ph photo${if (ph == 1) "" else "s"}", cs.secondaryContainer, cs.onSecondaryContainer)()
                    val stars = cur.value.count { it.starred }
                    if (stars > 0) chip("$stars starred", cs.tertiaryContainer, cs.onTertiaryContainer)()
                    val pl = cur.value.flatMap { it.places.keys }.distinct().size
                    if (pl > 0) chip("$pl place${if (pl == 1) "" else "s"}", cs.surfaceContainerHighest, cs.onSurface)()
                }
            }
            // where you are in the list: a tick per tag, the current one long
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.End) {
                val lo = (focus - 6).coerceAtLeast(0); val hi = (focus + 6).coerceAtMost(groups.lastIndex)
                for (k in lo..hi) {
                    val wTick by androidx.compose.animation.core.animateDpAsState(if (k == focus) 18.dp else 7.dp, androidx.compose.animation.core.spring(.6f, 400f), label = "tick")
                    Box(Modifier.width(wTick).height(3.dp).clip(CircleShape).background(if (k == focus) cs.primary else cs.outlineVariant))
                }
            }
            if (groups.isNotEmpty()) Text(
                "%02d / %02d".format(focus + 1, groups.size),
                style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 24.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            TabTitle("Canvases", null, Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 16.dp).statusBarsPadding(), ink)
        }
    }
}

/**
 * A tag's photos, as plain rounded cards in a few proportions, placed round the chosen name and never on it, nor on the counts, the page
 * title or the bar. Each card is the closest free spot to the name out of a handful tried, so they gather round it the way the concept
 * has them, bleeding off the edges a little. The same tag always lands the same way.
 */
@Composable
private fun ScatterPhotos(key: String, paths: List<String>, avoid: List<androidx.compose.ui.geometry.Rect>, near: Offset, accent: Color, modifier: Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val w = maxWidth.value; val h = maxHeight.value
        class Spot(val x: Float, val y: Float, val w: Float, val h: Float, val rot: Float, val radius: Int)
        val spots = remember(key, paths.size, avoid, w, h) {
            val r = kotlin.random.Random(com.b72.keep_canvas.data.seedOf(key))
            val ratios = listOf(1f to 1.25f, 1f to 1f, 1.25f to 1f, 1f to 1.15f)
            val taken = ArrayList<androidx.compose.ui.geometry.Rect>()
            val out = ArrayList<Spot>()
            fun area(q: androidx.compose.ui.geometry.Rect) = q.width * q.height
            for (i in paths.indices) {
                val base = listOf(176f, 142f, 124f, 112f, 104f, 98f, 92f)[i]
                val (rw, rh) = ratios[r.nextInt(ratios.size)]
                val pw = base * rw; val ph = base * rh
                var best: Spot? = null; var bestD = Float.MAX_VALUE
                repeat(90) {
                    val x = -pw * .12f + r.nextFloat() * (w - pw * .76f)
                    val y = 20f + r.nextFloat() * (h - ph - 20f)
                    val c = androidx.compose.ui.geometry.Rect(x, y, x + pw, y + ph)
                    if (avoid.any { it.inflate(8f).overlaps(c) }) return@repeat
                    if (taken.any { t -> val ov = t.intersect(c); ov.width > 0 && ov.height > 0 && area(ov) > .14f * minOf(area(t), area(c)) }) return@repeat
                    val d = kotlin.math.hypot(c.center.x - near.x, c.center.y - near.y) + r.nextFloat() * 60f
                    if (d < bestD) { bestD = d; best = Spot(x, y, pw, ph, (r.nextFloat() - .5f) * 10f, 22 + r.nextInt(10)) }
                }
                best?.let { taken.add(androidx.compose.ui.geometry.Rect(it.x, it.y, it.x + it.w, it.y + it.h)); out.add(it) }
            }
            out
        }
        spots.forEachIndexed { i, sp ->
            val pop = remember(paths[i]) { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(paths[i]) { kotlinx.coroutines.delay(70L * i); pop.animateTo(1f, androidx.compose.animation.core.spring(.7f, 300f)) }
            PhotoImage(
                paths[i], 500, false, accent,
                Modifier.offset(sp.x.dp, sp.y.dp).width(sp.w.dp).height(sp.h.dp)
                    .graphicsLayer { val v = pop.value; alpha = v.coerceIn(0f, 1f); scaleX = .7f + .3f * v; scaleY = .7f + .3f * v; rotationZ = sp.rot * v }
                    .clip(RoundedCornerShape(sp.radius.dp)),
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
