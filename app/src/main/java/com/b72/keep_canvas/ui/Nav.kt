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
    Dest("Stickers", Icons.Rounded.EmojiEmotions),
)

/** The three top-level places as one floating pill: the chosen one opens up to show its name. */
@Composable
fun FloatingNav(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        modifier = modifier,
        content = {
            DESTS.forEachIndexed { i, d ->
                val on = tab == i
                val bg by animateColorAsState(if (on) cs.primary else Color.Transparent, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navbg")
                Row(
                    Modifier.clip(CircleShape).background(bg)
                        .clickable(role = Role.Tab, onClickLabel = d.label) { onTab(i) }
                        .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                        .padding(horizontal = if (on) 18.dp else 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(d.icon, if (on) null else d.label, Modifier.size(24.dp), tint = if (on) cs.onPrimary else LocalContentColor.current)
                    AnimatedVisibility(on) {
                        Row {
                            Spacer(Modifier.width(8.dp))
                            Text(d.label, style = MaterialTheme.typography.labelLarge, color = cs.onPrimary, maxLines = 1)
                        }
                    }
                }
            }
        },
    )
}

/** Every tag is a canvas; this is the page that lists them, each as a card with a little spread of its photos. */
@Composable
fun CanvasHub(loaded: Loaded?, onTag: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val groups = remember(loaded?.library) {
        loaded?.library?.notes.orEmpty().flatMap { n -> n.tags.map { it to n } }
            .groupBy({ it.first }, { it.second }).entries.sortedByDescending { it.value.size }
    }
    val tones = listOf(cs.primaryContainer to cs.onPrimaryContainer, cs.secondaryContainer to cs.onSecondaryContainer, cs.tertiaryContainer to cs.onTertiaryContainer)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp)) {
                Box(Modifier.align(Alignment.TopEnd).size(120.dp).rotate(14f).clip(MaterialShapes.Clover4Leaf.toShape()).background(cs.tertiaryContainer.copy(alpha = .8f)))
                Column {
                    Text("Canvas", style = flexStyle(48.sp, wght = 700, wdth = 112f, rond = 40f), color = cs.onSurface)
                    Text(
                        if (groups.isEmpty()) "Nothing here yet" else "${groups.size} canvas${if (groups.size == 1) "" else "es"}, one for every tag",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (groups.isEmpty()) item(span = { GridItemSpan(2) }) {
            Text(
                "Give a note a tag and its canvas appears here, with all the notes that share it laid out as a collage.",
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        items(groups, key = { it.key }) { g ->
            val (bg, fg) = tones[(com.b72.keep_canvas.data.seedOf(g.key) % tones.size)]
            CanvasCard(g.key, g.value, bg, fg) { onTag(g.key) }
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
            Text(tag, style = flexStyle(26.sp, wght = 740, rond = 40f, lh = 1f), color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${notes.size} note${if (notes.size == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = .75f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}
