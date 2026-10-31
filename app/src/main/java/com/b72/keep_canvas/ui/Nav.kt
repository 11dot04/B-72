@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

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
@Composable
fun CanvasHub(loaded: Loaded?, onTag: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val groups = remember(loaded?.library) {
        loaded?.library?.notes.orEmpty().flatMap { n -> n.tags.map { it to n } }
            .groupBy({ it.first }, { it.second }).entries.sortedByDescending { it.value.size }
    }
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    val ls = androidx.compose.foundation.lazy.rememberLazyListState()
    // the tag nearest the middle of the screen is the focused one; measured at its first line, so opening its photos cannot flip it
    val anchor = with(androidx.compose.ui.platform.LocalDensity.current) { 44.dp.toPx() }
    val focus by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = ls.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + anchor - mid) }?.index ?: 0
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val pad = (maxHeight / 2 - 48.dp).coerceAtLeast(80.dp)
        if (groups.isEmpty()) Text(
            "Give a note a tag and its canvas appears here, with all the notes that share it laid out as a collage.",
            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).padding(32.dp),
        ) else androidx.compose.foundation.lazy.LazyColumn(
            state = ls,
            // sharp at the top, melting into blur toward the bottom edge (Android 13 and up; older phones just show it sharp)
            modifier = Modifier.fillMaxSize().blur(
                blurRadiusSpec = BlurRadiusSpec.verticalGradient(stops = listOf(BlurStop(0f, 0.dp), BlurStop(.6f, 0.dp), BlurStop(1f, 14.dp))),
                edgeTreatment = BlurredEdgeTreatment.Rectangle,
            ),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = pad, bottom = pad),
        ) {
            itemsIndexed(groups, key = { _, g -> g.key }) { i, g ->
                val on = i == focus
                val a by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else .24f, label = "tagfocus")
                val hue = (com.b72.keep_canvas.data.seedOf(g.key) % 360).toFloat()
                val accent = Color.hsl(hue, .78f, if (dark) .68f else .42f)
                val flip = Color.hsl(hue + 180f, .85f, if (dark) .3f else .8f)
                val sub = g.value.lastOrNull { it.title.isNotBlank() }?.title.orEmpty()
                val covers = g.value.mapNotNull { it.cover?.path }.take(3)
                Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Open the ${g.key} canvas") { onTag(g.key) }.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.width(if (on) 52.dp else 0.dp)) { if (on) Text("\u2192", style = flexStyle(44.sp, wght = 400, lh = 1f), color = cs.onSurface) }
                        Text(g.key.lowercase(), style = flexStyle(54.sp, wght = if (on) 620 else 480, wdth = 96f, lh = 1f), color = cs.onSurface.copy(alpha = a), modifier = Modifier.weight(1f))
                    }
                    Text(
                        "NOTES: ${g.value.size}   PHOTOS: ${g.value.sumOf { it.photos.size }}",
                        style = androidx.compose.ui.text.TextStyle(fontFamily = mono, fontSize = 11.sp, letterSpacing = .6.sp, color = cs.onSurface.copy(alpha = a * .7f)),
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    )
                    androidx.compose.animation.AnimatedVisibility(on && covers.isNotEmpty()) { TagPhotos(covers, sub, accent, flip) }
                }
            }
        }
        val fade = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(cs.surface, cs.surface.copy(alpha = 0f)))
        Box(Modifier.fillMaxWidth().height(150.dp).background(fade))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(130.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(cs.surface.copy(alpha = 0f), cs.surface.copy(alpha = .9f)))))
        Text("Canvases", style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 22.dp))
    }
}

/**
 * The focused tag's photos, set between it and the next tag. A line of the newest note's title runs in the tag's accent colour over
 * the first photo, and the part that crosses the photo flips to the opposite colour.
 */
@Composable
private fun TagPhotos(covers: List<String>, sub: String, accent: Color, flip: Color) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(262.dp)) {
        val w = maxWidth
        val pw = w * .46f
        val ph = pw * 1.05f
        val px = w * .40f
        val py = 30.dp
        val shape = RoundedCornerShape(28.dp)
        covers.getOrNull(1)?.let { PhotoImage(it, 400, false, accent, Modifier.offset(w * .04f, 82.dp).size(w * .32f).rotate(-6f).clip(shape)) }
        covers.getOrNull(2)?.let { PhotoImage(it, 400, false, accent, Modifier.offset(w * .66f, 140.dp).size(w * .3f).rotate(5f).clip(shape)) }
        covers.getOrNull(0)?.let { PhotoImage(it, 500, false, accent, Modifier.offset(px, py).size(pw, ph).clip(shape)) }
        if (sub.isNotEmpty()) {
            val tx = w * .12f
            val tw = w * .84f
            val st = flexStyle(40.sp, wght = 600, wdth = 96f, lh = 1f)
            Text(sub.lowercase(), style = st, color = accent, maxLines = 2, modifier = Modifier.offset(tx, 0.dp).width(tw))
            Box(Modifier.offset(px, py).size(pw, ph).clip(shape)) {
                Text(sub.lowercase(), style = st, color = flip, maxLines = 2, modifier = Modifier.offset(tx - px, -py).requiredWidth(tw))
            }
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
