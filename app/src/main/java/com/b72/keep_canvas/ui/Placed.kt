@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.data.Stuck
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

/** The hand-placed marks and stickers of one canvas: kept here while they are being moved, saved a moment after the last change. */
class PlacedState(private val repo: Repo, private val tag: String) {
    val items = mutableStateListOf<Stuck>()
    var selected by mutableStateOf<String?>(null)
    var drawing by mutableStateOf(false)
    var pen by mutableStateOf(0)
    private var seeded = false

    fun seed() { if (!seeded) { repo.loaded?.library?.let { items.addAll(it.placed[tag].orEmpty()); seeded = true } } }
    fun save() = repo.setPlaced(tag, items.toList())
    fun update(id: String, f: (Stuck) -> Stuck) { val i = items.indexOfFirst { it.id == id }; if (i >= 0) items[i] = f(items[i]) }
    fun add(s: Stuck) { items.add(s); selected = s.id }
    fun remove(id: String) { items.removeAll { it.id == id }; if (selected == id) selected = null }
    fun duplicate(id: String) { items.firstOrNull { it.id == id }?.let { add(it.copy(id = "p${System.nanoTime()}", x = it.x + .02f, y = it.y + .02f)) } }
    suspend fun autosave() { snapshotFlow { items.toList() }.drop(1).debounce(500).collect { save() } }
}

private fun markOf(k: String): Mark? = if (k.startsWith("m:")) runCatching { Mark.valueOf(k.substring(2)) }.getOrNull() else null

/** The layer above the cards. [cw] is the canvas width in px; [topInset] the room under the status bar. */
@Composable
fun PlacedLayer(st: PlacedState, cw: Float, topInset: Float, ring: Color) {
    LaunchedEffect(Unit) { st.autosave() }
    for (it in st.items) androidx.compose.runtime.key(it.id) { PlacedItem(it, st, cw, topInset, ring) }
}

@Composable
private fun PlacedItem(s: Stuck, st: PlacedState, cw: Float, topInset: Float, ring: Color) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val mark = markOf(s.k)
    val aspect = if (s.h > 0f && s.w > 0f) s.h / s.w else mark?.aspect ?: 1f
    val wPx = s.w * cw
    val on = st.selected == s.id
    val latest by androidx.compose.runtime.rememberUpdatedState(s)
    Box(
        Modifier
            .offset { IntOffset((s.x * cw).roundToInt(), (s.y * cw + topInset).roundToInt()) }
            .size(with(density) { wPx.toDp() }, with(density) { (wPx * aspect).toDp() })
            .pointerInput(s.id) { detectTapGestures { st.selected = if (st.selected == s.id) null else s.id } }
            .pointerInput(s.id) {
                detectTransformGestures { _, pan, zoom, rot ->
                    st.selected = s.id
                    st.update(s.id) { o ->
                        val nw = (o.w * zoom).coerceIn(.04f, 1f)
                        o.copy(x = o.x + pan.x / cw + (o.w - nw) / 2, y = o.y + pan.y / cw + (o.w - nw) * aspect / 2, w = nw, r = o.r + rot)
                    }
                }
            }
            .graphicsLayer { rotationZ = latest.r },
    ) {
        if (mark != null) Canvas(Modifier.fillMaxWidth().aspectRatio(1f / aspect)) { drawMark(mark, 0f, 0f, size.width, MarkColors[s.c.mod(MarkColors.size)], s.id.hashCode(), if (s.h > 0f) size.height else -1f) }
        else {
            val img by produceState<ImageBitmap?>(Images.peek(s.k.substring(2), 500), s.k) { value = Images.load(s.k.substring(2), 500) }
            img?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit) }
        }
        if (on) Box(Modifier.matchParentSize().border(2.dp, ring, RoundedCornerShape(10.dp)))
    }
}

/** What to do with the selected item: its colour (marks), a bit smaller or bigger, a copy, remove, or done. */
@Composable
fun PlacedBar(st: PlacedState, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val sel = st.items.firstOrNull { it.id == st.selected } ?: return
    val isMark = sel.k.startsWith("m:")
    Row(
        modifier.clip(CircleShape).background(cs.tertiary).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (isMark) for ((i, c) in MarkColors.withIndex()) Box(
            Modifier.padding(horizontal = 3.dp).size(28.dp).clip(CircleShape).background(c)
                .border(if (sel.c == i) 3.dp else 1.dp, if (sel.c == i) cs.onTertiary else cs.onTertiary.copy(alpha = .4f), CircleShape)
                .clickable(onClickLabel = "Colour") { st.update(sel.id) { it.copy(c = i) } },
        )
        IconButton({ st.update(sel.id) { it.copy(w = (it.w / 1.2f).coerceAtLeast(.04f)) } }) { Icon(Icons.Rounded.Remove, "Smaller", tint = cs.onTertiary) }
        IconButton({ st.update(sel.id) { it.copy(w = (it.w * 1.2f).coerceAtMost(1f)) } }) { Icon(Icons.Rounded.Add, "Bigger", tint = cs.onTertiary) }
        IconButton({ st.duplicate(sel.id) }) { Icon(Icons.Rounded.ContentCopy, "Duplicate", tint = cs.onTertiary) }
        IconButton({ st.remove(sel.id) }) { Icon(Icons.Rounded.Delete, "Remove", tint = cs.onTertiary) }
        IconButton({ st.selected = null }) { Icon(Icons.Rounded.Check, "Done", tint = cs.onTertiary) }
    }
}

/** The tray: every mark in the pen colour you pick, and your stickers. A tap puts the item in the middle of what you are looking at. */
@Composable
fun PlacedTray(stickers: List<String>, onMark: (Mark, Int) -> Unit, onSticker: (String) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var color by remember { mutableStateOf(0) }
    ModalBottomSheet(onClose) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("Mark up this canvas", style = flexStyle(androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp), wght = 720, rond = 40f))
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((i, c) in MarkColors.withIndex()) Box(
                    Modifier.size(34.dp).clip(CircleShape).background(c).border(if (color == i) 3.dp else 1.dp, if (color == i) cs.onSurface else cs.outlineVariant, CircleShape)
                        .clickable(onClickLabel = "Pen colour") { color = i },
                )
            }
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.height(172.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Mark.entries.toList()) { m ->
                    Box(
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh).clickable(onClickLabel = m.label) { onMark(m, color) }.padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.fillMaxWidth().aspectRatio(1f / m.aspect)) { drawMark(m, 0f, 0f, size.width, MarkColors[color], m.ordinal * 7) }
                    }
                }
            }
            if (stickers.isNotEmpty()) {
                Text("STICKERS", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(stickers.size) { i ->
                        val p = stickers[i]
                        val img by produceState<ImageBitmap?>(Images.peek(p, 300), p) { value = Images.load(p, 300) }
                        Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).clickable(onClickLabel = "Add sticker") { onSticker(p) }.padding(6.dp)) {
                            img?.let { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
                        }
                    }
                }
            }
        }
    }
}


/** Draw mode: a finger-drawn line, circle, box or triangle is corrected into the clean shape as soon as the finger lifts. */
@Composable
fun DrawLayer(st: PlacedState, hs: Int, vs: Int, cw: Float, topInset: Float, modifier: Modifier = Modifier) {
    val pts = remember { mutableStateListOf<androidx.compose.ui.geometry.Offset>() }
    val hsNow by androidx.compose.runtime.rememberUpdatedState(hs)
    val vsNow by androidx.compose.runtime.rememberUpdatedState(vs)
    Canvas(
        modifier.pointerInput(cw) {
            detectDragGestures(
                onDragStart = { pts.clear(); pts.add(it) },
                onDrag = { c, _ -> pts.add(c.position) },
                onDragEnd = {
                    val canvasPts = pts.map { androidx.compose.ui.geometry.Offset((it.x + hsNow) / cw, (it.y + vsNow - topInset) / cw) }
                    com.b72.keep_canvas.ui.Shapes.recognise(canvasPts, "p${System.nanoTime()}", st.pen)?.let { s -> st.items.add(s) }
                    pts.clear()
                },
                onDragCancel = { pts.clear() },
            )
        },
    ) {
        if (pts.size > 1) drawPath(
            androidx.compose.ui.graphics.Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) },
            MarkColors[st.pen.mod(MarkColors.size)].copy(alpha = .8f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}

/** The pill shown while drawing: pick the pen, or finish. */
@Composable
fun DrawBar(st: PlacedState, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.clip(CircleShape).background(cs.tertiary).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Draw a shape", color = cs.onTertiary, style = MaterialTheme.typography.labelLarge)
        for ((i, c) in MarkColors.withIndex()) Box(
            Modifier.size(28.dp).clip(CircleShape).background(c).border(if (st.pen == i) 3.dp else 1.dp, cs.onTertiary.copy(alpha = if (st.pen == i) 1f else .4f), CircleShape).clickable(onClickLabel = "Pen colour") { st.pen = i },
        )
        IconButton({ st.drawing = false }) { Icon(Icons.Rounded.Check, "Done", tint = cs.onTertiary) }
    }
}
