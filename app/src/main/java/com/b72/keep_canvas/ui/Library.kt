@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Loaded
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.recSub
import com.b72.keep_canvas.data.seedOf

private class Shelf(val name: String, val kind: Kind, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val SHELVES = listOf(
    Shelf("Music", Kind.Track, Icons.Rounded.MusicNote),
    Shelf("Books", Kind.Book, Icons.Rounded.MenuBook),
    Shelf("Films", Kind.Film, Icons.Rounded.Movie),
    Shelf("Recipes", Kind.Recipe, Icons.Rounded.Restaurant),
    Shelf("Links", Kind.Link, Icons.Rounded.Link),
)

@Composable
private fun coverShape(k: Kind, id: String): Shape = when (k) {
    Kind.Track -> CircleShape
    Kind.Recipe -> if (seedOf(id) % 2 == 0) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Flower.toShape()
    Kind.Link -> RoundedCornerShape(32.dp)
    else -> RoundedCornerShape(28.dp)
}

/** Everything that holds structured data (songs, books, films, recipes, links) lives here, one shelf per kind. */
@Composable
fun LibraryScreen(loaded: Loaded?, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val notes = loaded?.library?.notes.orEmpty()
    var shelf by rememberSaveable { mutableIntStateOf(0) }
    val counts = remember(notes) { SHELVES.map { s -> notes.count { n -> n.blocks.any { it.kind == s.kind } } } }
    val items = remember(notes, shelf) {
        notes.asReversed().mapNotNull { n -> n.blocks.firstOrNull { it.kind == SHELVES[shelf].kind }?.let { n to it } }
    }
    val ls = rememberLazyListState()
    val focus by remember {
        derivedStateOf {
            val info = ls.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: 0
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
            Text("library", style = flexStyle(56.sp, wght = 480, wdth = 96f, lh = .95f), color = cs.onSurface)
            Spacer(Modifier.width(10.dp))
            Text("(${counts.sum()})", style = flexStyle(26.sp, wght = 200, lh = 1f), color = cs.onSurface.copy(alpha = .4f), modifier = Modifier.padding(bottom = 6.dp))
        }
        Text("Everything you saved that has a shape of its own.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SHELVES.forEachIndexed { i, s ->
                Row(Modifier.clickable(onClickLabel = s.name) { shelf = i }, verticalAlignment = Alignment.Top) {
                    Text(s.name, style = flexStyle(22.sp, wght = if (i == shelf) 650 else 420, lh = 1f), color = if (i == shelf) cs.onSurface else cs.onSurface.copy(alpha = .35f))
                    Text("${counts[i]}", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = if (i == shelf) cs.primary else cs.onSurface.copy(alpha = .35f), modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                "Nothing on the ${SHELVES[shelf].name.lowercase()} shelf yet. Add one from the + menu inside a note.",
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(24.dp),
            )
        } else {
            val cur = items.getOrNull(focus.coerceIn(0, items.lastIndex))!!
            Text(
                cur.second.title.ifBlank { cur.first.title }.ifBlank { SHELVES[shelf].name },
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = flexStyle(40.sp, wght = 560, wdth = 96f, lh = 1f), color = cs.onSurface, modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(cur.second.recSub(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                val side = (maxWidth - 220.dp) / 2
                LazyRow(
                    state = ls, flingBehavior = rememberSnapFlingBehavior(ls),
                    contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(items, key = { _, p -> p.second.id }) { i, p ->
                        val sc by animateFloatAsState(if (i == focus) 1f else .84f, label = "shelf")
                        val b = p.second
                        val h = when (b.kind) { Kind.Film, Kind.Book -> 320.dp; else -> 220.dp }
                        Box(
                            Modifier.width(220.dp).height(h).scale(sc).clip(coverShape(b.kind, b.id)).background(cs.primaryContainer)
                                .clickable(onClickLabel = "Open") { onOpen(p.first.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (b.path != null) PhotoImage(b.path, 600, false, cs.primary, Modifier.fillMaxSize())
                            else Icon(SHELVES[shelf].icon, null, Modifier.size(56.dp), tint = cs.onPrimaryContainer)
                            if (b.kind == Kind.Track) Box(Modifier.size(34.dp).clip(CircleShape).background(cs.surface))
                        }
                    }
                }
            }
            // the featured panel: what you wrote about it
            val note: Note = cur.first
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainerHigh)
                    .clickable(onClickLabel = "Open the note") { onOpen(note.id) }.padding(20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        (note.tags.firstOrNull() ?: "saved").uppercase(),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant,
                    )
                    Icon(Icons.Rounded.ArrowOutward, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                }
                val said = cur.second.body.ifBlank { note.snippet(140) }
                Text(
                    said.ifBlank { "Open it to add your thoughts." }, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Serif, fontSize = 18.sp, lineHeight = 24.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = cs.onSurface,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
