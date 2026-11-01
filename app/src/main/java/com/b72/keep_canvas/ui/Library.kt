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
import androidx.compose.ui.BiasAlignment
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
import com.b72.keep_canvas.data.dbl
import com.b72.keep_canvas.data.nums
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

private val SQUARE_SHAPES by lazy {
    listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Gem, MaterialShapes.Bun, MaterialShapes.Flower, MaterialShapes.Cookie6Sided, MaterialShapes.Pentagon)
}

/** Each saved thing gets its own frame from the expressive set; the same one every time. Tall posters and covers keep to the tall shapes. */
@Composable
private fun coverShape(k: Kind, n: Int): Shape {
    return when (k) {
        Kind.Track -> CircleShape
        Kind.Film, Kind.Book -> when (n % 4) {
            0 -> MaterialShapes.Arch.toShape()
            1 -> MaterialShapes.Oval.toShape()
            2 -> RoundedCornerShape(topStart = 110.dp, topEnd = 110.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
            else -> RoundedCornerShape(36.dp)
        }
        Kind.Link -> if (n % 2 == 0) RoundedCornerShape(32.dp) else MaterialShapes.Bun.toShape()
        else -> SQUARE_SHAPES[n % SQUARE_SHAPES.size].toShape()
    }
}

/** Where to look in a photo so its subject sits in the middle of a square frame (the subject box is read from the cut-out). */
private fun subjectAlign(b: Block): Alignment {
    val box = b.nums("cutBox")?.takeIf { it.size >= 4 } ?: return Alignment.Center
    val ar = b.dbl("cutAspect")?.toFloat()?.takeIf { it > 0f } ?: return Alignment.Center
    val cx = ((box[0] + box[2]) / 2).toFloat(); val cy = ((box[1] + box[3]) / 2).toFloat()
    fun f(c: Float, ratio: Float) = if (ratio == 1f) .5f else ((c * ratio - .5f) / (ratio - 1f)).coerceIn(0f, 1f)
    return if (ar >= 1f) BiasAlignment(2 * f(cx, ar) - 1f, 0f) else BiasAlignment(0f, 2 * f(cy, 1f / ar) - 1f)
}

/** A record: dark grooves, the cover as the label, a spindle hole. */
@Composable
private fun Vinyl(b: Block, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val c = center; val r = size.minDimension / 2
            drawCircle(tint, r, c)
            var rr = r * .96f
            while (rr > r * .46f) { drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = .06f), rr, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())); rr -= 5.dp.toPx() }
            // a glint across the grooves
            drawArc(androidx.compose.ui.graphics.Color.White.copy(alpha = .12f), -50f, 28f, true, androidx.compose.ui.geometry.Offset(c.x - r * .94f, c.y - r * .94f), androidx.compose.ui.geometry.Size(r * 1.88f, r * 1.88f))
            drawArc(androidx.compose.ui.graphics.Color.White.copy(alpha = .12f), 130f, 28f, true, androidx.compose.ui.geometry.Offset(c.x - r * .94f, c.y - r * .94f), androidx.compose.ui.geometry.Size(r * 1.88f, r * 1.88f))
        }
        Box(Modifier.fillMaxSize(.4f).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            if (b.path != null) PhotoImage(b.path, 400, false, tint, Modifier.fillMaxSize())
        }
        Box(Modifier.size(12.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF0E0D11)))
    }
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
    Column(Modifier.fillMaxSize().bottomBlur(4.dp, .8f).statusBarsPadding().padding(top = 16.dp)) {
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
                        val slot = items.size - 1 - i // stable as newer items are added in front, and neighbours always differ
                        val look = if (b.kind == Kind.Track) slot % 3 else -1 // 0 CD, 1 black vinyl, 2 maroon vinyl
                        if (look != 0 && look != -1) Vinyl(
                            b, if (look == 1) androidx.compose.ui.graphics.Color(0xFF15141A) else androidx.compose.ui.graphics.Color(0xFF3A1F2B),
                            Modifier.width(220.dp).height(h).scale(sc).clickable(onClickLabel = "Open") { onOpen(p.first.id) },
                        ) else Box(
                            Modifier.width(220.dp).height(h).scale(sc).clip(coverShape(b.kind, slot)).background(cs.primaryContainer)
                                .clickable(onClickLabel = "Open") { onOpen(p.first.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (b.path != null) PhotoImage(b.path, 600, false, cs.primary, Modifier.fillMaxSize(), if (b.kind == Kind.Recipe) subjectAlign(b) else Alignment.Center)
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
