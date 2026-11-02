@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
fun CanvasHub(loaded: Loaded?, onTag: (String) -> Unit) {
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
            val page = 330.dp
            val pad = ((maxHeight - page) / 2).coerceAtLeast(60.dp)
            if (groups.isEmpty()) Text(
                "Give a note a tag and its canvas appears here, with all the notes that share it laid out as a collage.",
                style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = .7f), modifier = Modifier.align(Alignment.Center).padding(32.dp),
            ) else androidx.compose.foundation.lazy.LazyColumn(
                state = ls,
                flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(ls, androidx.compose.foundation.gestures.snapping.SnapPosition.Center),
                // sharp in the middle, melting into blur toward the bottom edge (Android 13 and up), and faded out at both ends
                modifier = Modifier.fillMaxSize().bottomBlur(12.dp, .62f)
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, .16f to Color.Black, .86f to Color.Black, 1f to Color.Transparent),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    },
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = pad, bottom = pad),
            ) {
                itemsIndexed(groups, key = { _, g -> g.key }) { i, g ->
                    val hue = hueOf(g.key)
                    val accent = if (dark) Color.hsl(hue, .75f, .72f) else Color.hsl(hue, .8f, .38f)
                    val covers = g.value.mapNotNull { it.cover?.path }.take(3)
                    // the width of the name follows how near the middle it is: full width in the middle, condensed away from it (7 steps, so fonts are reused)
                    val step by remember(i) {
                        androidx.compose.runtime.derivedStateOf {
                            val info = ls.layoutInfo
                            val me = info.visibleItemsInfo.firstOrNull { it.index == i }
                            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val d = if (me == null) 1f else (kotlin.math.abs(me.offset + me.size / 2f - mid) / me.size).coerceIn(0f, 1f)
                            ((1f - d * 1.6f).coerceIn(0f, 1f) * 6f).let { kotlin.math.round(it).toInt() }
                        }
                    }
                    val name = remember(g.key) { g.key.replaceFirstChar { it.uppercase() } }
                    val titleStyle = remember(step) { flexStyle(56.sp, wght = 560 + step * 12, wdth = 30f + step * 11f, lh = 1f) }
                    Column(
                        Modifier.fillMaxWidth().height(page)
                            .graphicsLayer {
                                // every page eases in and out continuously with its distance from the middle, so scrolling never jumps
                                val info = ls.layoutInfo
                                val me = info.visibleItemsInfo.firstOrNull { it.index == i }
                                val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val d = if (me == null) 1f else (kotlin.math.abs(me.offset + me.size / 2f - mid) / me.size).coerceIn(0f, 1f)
                                val f = 1f - d
                                alpha = .28f + .72f * f
                                translationY = (me?.let { (it.offset + it.size / 2f - mid) } ?: 0f) * -.08f
                            }
                            .clickable(onClickLabel = "Open the ${g.key} canvas") { onTag(g.key) },
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(name, style = titleStyle, color = if (step >= 5) ink else ink.copy(alpha = .8f), maxLines = 1, softWrap = false)
                        Text(
                            "NOTES: ${g.value.size}   PHOTOS: ${g.value.sumOf { it.photos.size }}",
                            style = androidx.compose.ui.text.TextStyle(fontFamily = mono, fontSize = 11.sp, letterSpacing = .6.sp, color = ink.copy(alpha = if (i == focus) .7f else 0f)),
                            modifier = Modifier.align(Alignment.End).padding(top = 2.dp, bottom = 6.dp),
                        )
                        // only the tag in the middle shows its photos; they pop in one after another
                        TagPhotos(covers, accent, shown = i == focus)
                    }
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

/** A tag's photos, fanned out under its name. */
@Composable
private fun TagPhotos(covers: List<String>, accent: Color, shown: Boolean) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp)) {
        val w = maxWidth
        val shape = RoundedCornerShape(28.dp)
        @Composable fun pop(i: Int, path: String?, mod: Modifier, rot: Float) {
            if (path == null) return
            val p = remember(path) { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(shown) {
                if (shown) kotlinx.coroutines.delay(90L * i)
                p.animateTo(if (shown) 1f else 0f, androidx.compose.animation.core.spring(if (shown) .55f else 1f, if (shown) 280f else 600f))
            }
            if (p.value > .01f) PhotoImage(
                path, 500, false, accent,
                mod.graphicsLayer { val v = p.value; alpha = v.coerceIn(0f, 1f); scaleX = .6f + .4f * v; scaleY = .6f + .4f * v; rotationZ = rot * v + (1f - v) * 14f; translationY = (1f - v) * 40f }.clip(shape),
            )
        }
        pop(1, covers.getOrNull(1), Modifier.offset(w * .02f, 40.dp).size(w * .3f), -6f)
        pop(2, covers.getOrNull(2), Modifier.offset(w * .68f, 80.dp).size(w * .28f), 5f)
        pop(0, covers.getOrNull(0), Modifier.offset(w * .26f, 0.dp).size(w * .46f, w * .48f), 0f)
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
