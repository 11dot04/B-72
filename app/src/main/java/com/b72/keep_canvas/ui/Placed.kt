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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight
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
    private val past = ArrayList<List<Stuck>>()
    private val future = ArrayList<List<Stuck>>()
    private var lastEdit = 0L
    val canUndo get() = historySize > 0
    val canRedo get() = futureSize > 0
    private var historySize by mutableStateOf(0)
    private var futureSize by mutableStateOf(0)

    /** The state before a change is kept (up to fifty steps), so undo can bring it back. */
    fun checkpoint() {
        past.add(items.toList()); if (past.size > 50) past.removeAt(0)
        future.clear(); historySize = past.size; futureSize = 0
    }
    fun undo() {
        if (past.isEmpty()) return
        future.add(items.toList()); val back = past.removeAt(past.lastIndex)
        items.clear(); items.addAll(back); selected = null; historySize = past.size; futureSize = future.size
    }
    fun redo() {
        if (future.isEmpty()) return
        past.add(items.toList()); val fwd = future.removeAt(future.lastIndex)
        items.clear(); items.addAll(fwd); selected = null; historySize = past.size; futureSize = future.size
    }
    /** Pens are not chosen: each new mark takes the next colour of the canvas's own palette (never the ink, which is for text). */
    fun nextColour() = intArrayOf(0, 1, 2, 4)[items.size % 4]

    fun seed() { if (!seeded) { repo.loaded?.library?.let { items.addAll(it.placed[tag].orEmpty()); seeded = true } } }
    fun save() = repo.setPlaced(tag, items.toList())
    fun update(id: String, f: (Stuck) -> Stuck) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        // a drag or a pinch is many updates; one pause in them is one undo step
        val now = System.currentTimeMillis()
        if (now - lastEdit > 700) checkpoint()
        lastEdit = now
        items[i] = f(items[i])
    }
    fun add(s: Stuck) { checkpoint(); items.add(s); selected = s.id }
    fun remove(id: String) { checkpoint(); items.removeAll { it.id == id }; if (selected == id) selected = null }
    fun duplicate(id: String) { items.firstOrNull { it.id == id }?.let { add(it.copy(id = "p${System.nanoTime()}", x = it.x + .02f, y = it.y + .02f)) } }
    suspend fun autosave() { snapshotFlow { items.toList() }.drop(1).debounce(500).collect { save() } }
}

private fun markOf(k: String): Mark? = if (k.startsWith("m:")) runCatching { Mark.valueOf(k.substring(2)) }.getOrNull() else null

/** The layer above the cards. [cw] is the canvas width in px; [topInset] the room under the status bar. */
@Composable
fun PlacedLayer(st: PlacedState, cw: Float, topInset: Float, ring: Color, ink: Color, origin: () -> androidx.compose.ui.geometry.Offset, onEditText: (String) -> Unit) {
    LaunchedEffect(Unit) { st.autosave() }
    for (it in st.items) androidx.compose.runtime.key(it.id) { PlacedItem(it, st, cw, topInset, ring, ink, origin, onEditText) }
}

@Composable
private fun PlacedItem(s: Stuck, st: PlacedState, cw: Float, topInset: Float, ring: Color, ink: Color, origin: () -> androidx.compose.ui.geometry.Offset, onEditText: (String) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val mark = markOf(s.k)
    val stamp = stampOf(s.k)
    val ding = dingOf(s.k)
    val isText = s.k.startsWith("t:")
    val pal = LocalMarkColors.current
    val aspect = if (s.h > 0f && s.w > 0f) s.h / s.w else mark?.aspect ?: stamp?.aspect ?: 1f
    val wPx = s.w * cw
    val on = st.selected == s.id
    val latest by androidx.compose.runtime.rememberUpdatedState(s)
    Box(
        Modifier
            .offset { IntOffset((s.x * cw + origin().x).roundToInt(), (s.y * cw + topInset + origin().y).roundToInt()) }
            .then(if (isText) Modifier.width(with(density) { wPx.toDp() }) else Modifier.size(with(density) { wPx.toDp() }, with(density) { (wPx * aspect).toDp() }))
            .pointerInput(s.id) { detectTapGestures(onDoubleTap = { if (isText) onEditText(s.id) }) { st.selected = if (st.selected == s.id) null else s.id } }
            .pointerInput(s.id) {
                detectTransformGestures { _, pan, zoom, rot ->
                    st.selected = s.id
                    st.update(s.id) { o ->
                        val nw = (o.w * zoom).coerceIn(.04f, 1f)
                        o.copy(x = o.x + pan.x / cw + (o.w - nw) / 2, y = o.y + pan.y / cw + (o.w - nw) * aspect / 2, w = nw, r = o.r + rot, h = if (o.h > 0f) o.h * nw / o.w else 0f)
                    }
                }
            }
            .graphicsLayer { rotationZ = latest.r },
    ) {
        if (isText) androidx.compose.foundation.text.BasicText(
            s.k.substring(2),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = Serif, fontSize = with(density) { (wPx / 8.5f).toSp() }, lineHeight = with(density) { (wPx / 6.2f).toSp() },
                color = if (s.c == 3) ink else pal[s.c.mod(pal.size)],
            ),
        )
        else if (s.k.startsWith("f:")) {
            val line = remember(s.k) { freehandPoints(s.k) }
            val col = pal[s.c.mod(pal.size)]
            Canvas(Modifier.fillMaxSize()) {
                if (line.size > 1) drawPath(
                    Path().apply { moveTo(line[0].x * size.width, line[0].y * size.height); for (i in 1 until line.size) lineTo(line[i].x * size.width, line[i].y * size.height) },
                    col, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                )
            }
        }
        else if (ding != null) DingView(ding.first, ding.second, s.c, Modifier.fillMaxWidth())
        else if (stamp != null) StampView(stamp, s.c, s.id.hashCode(), Modifier.fillMaxWidth())
        else if (mark != null) Canvas(Modifier.fillMaxWidth().aspectRatio(1f / aspect)) { drawMark(mark, 0f, 0f, size.width, pal[s.c.mod(pal.size)], s.id.hashCode(), if (s.h > 0f) size.height else -1f) }
        else {
            val img by produceState<ImageBitmap?>(Images.peek(s.k.substring(2), 500), s.k) { value = Images.load(s.k.substring(2), 500) }
            img?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit) }
        }
        if (on) Box(Modifier.matchParentSize().border(2.dp, ring, RoundedCornerShape(10.dp)))
    }
}

/** What to do with the selected item: its colour (marks), a bit smaller or bigger, a copy, remove, or done. */
@Composable
fun PlacedBar(st: PlacedState, modifier: Modifier = Modifier, onEdit: (String) -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val sel = st.items.firstOrNull { it.id == st.selected } ?: return
    val isMark = sel.k.startsWith("m:") || sel.k.startsWith("x:") || sel.k.startsWith("d:")
    Row(
        modifier.clip(CircleShape).background(cs.tertiary).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (sel.k.startsWith("t:")) IconButton({ onEdit(sel.id) }) { Icon(Icons.Rounded.Edit, "Edit the text", tint = cs.onTertiary) }
        IconButton({ st.update(sel.id) { it.copy(w = (it.w / 1.2f).coerceAtLeast(.04f)) } }) { Icon(Icons.Rounded.Remove, "Smaller", tint = cs.onTertiary) }
        IconButton({ st.update(sel.id) { it.copy(w = (it.w * 1.2f).coerceAtMost(1f)) } }) { Icon(Icons.Rounded.Add, "Bigger", tint = cs.onTertiary) }
        IconButton({ st.duplicate(sel.id) }) { Icon(Icons.Rounded.ContentCopy, "Duplicate", tint = cs.onTertiary) }
        val del = LocalMarkColors.current[1]
        IconButton({ st.remove(sel.id) }, colors = IconButtonDefaults.iconButtonColors(containerColor = del, contentColor = if (del.luminance() > .5f) Color(0xFF1B1B1F) else Color.White)) { Icon(Icons.Rounded.Delete, "Remove") }
        IconButton({ st.selected = null }) { Icon(Icons.Rounded.Check, "Done", tint = cs.onTertiary) }
    }
}

/** The tray: either the stamps, in the pen colour you pick, or your artifacts. A tap puts the item in the middle of what you are looking at. */
@Composable
fun PlacedTray(marks: Boolean, stickers: List<String>, onStamp: (String, Int) -> Unit, onSticker: (String) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val color = 0
    ModalBottomSheet(onClose) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(if (marks) "Stamps" else "Artifacts", style = flexStyle(androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp), wght = 720, rond = 40f))
            if (marks) {
                Spacer(Modifier.height(12.dp))
                val dings = remember { availableDings() }
                LazyVerticalGrid(GridCells.Fixed(4), Modifier.height(420.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (g in listOf("Ornament", "Marks", "Nature", "Play", "Type")) {
                        val of = dings.filter { it.group == g }
                        if (of.isEmpty()) continue
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(g.uppercase(), style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                        items(of.size) { i ->
                            val m = of[i]
                            val shape = (m.cp + i).mod(4)
                            Box(
                                Modifier.aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh).clickable(onClickLabel = m.name) { onStamp("d:${Integer.toHexString(m.cp)}:$shape", color) }.padding(10.dp),
                                contentAlignment = Alignment.Center,
                            ) { DingView(m.cp, shape, color, Modifier.fillMaxWidth()) }
                        }
                    }
                }
            } else if (stickers.isEmpty()) {
                Text("No artifacts yet. Cut a subject out of a photo and it will wait here.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyVerticalGrid(GridCells.Fixed(4), Modifier.padding(top = 12.dp).height(300.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(stickers.size) { i ->
                        val p = stickers[i]
                        val img by produceState<ImageBitmap?>(Images.peek(p, 300), p) { value = Images.load(p, 300) }
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).clickable(onClickLabel = "Add artifact") { onSticker(p) }.padding(8.dp)) {
                            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
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
    val pal = LocalMarkColors.current
    val hsNow by androidx.compose.runtime.rememberUpdatedState(hs)
    val vsNow by androidx.compose.runtime.rememberUpdatedState(vs)
    Canvas(
        modifier.pointerInput(cw) {
            detectDragGestures(
                onDragStart = { pts.clear(); pts.add(it) },
                onDrag = { c, _ -> pts.add(c.position) },
                onDragEnd = {
                    val canvasPts = pts.map { androidx.compose.ui.geometry.Offset((it.x + hsNow) / cw, (it.y + vsNow - topInset) / cw) }
                    val col = st.nextColour()
                    val shape = com.b72.keep_canvas.ui.Shapes.recognise(canvasPts, "p${System.nanoTime()}", col) ?: freehand(canvasPts, "p${System.nanoTime()}", col)
                    shape?.let { s -> st.add(s); st.selected = null }
                    pts.clear()
                },
                onDragCancel = { pts.clear() },
            )
        },
    ) {
        if (pts.size > 1) drawPath(
            androidx.compose.ui.graphics.Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) },
            pal[st.nextColour().mod(pal.size)].copy(alpha = .85f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}

/** The pill shown while drawing: pick the pen, or finish. */
@Composable
fun DrawBar(st: PlacedState, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.clip(CircleShape).background(cs.tertiary).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Draw", color = cs.onTertiary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        IconButton({ st.undo() }, enabled = st.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo", tint = cs.onTertiary.copy(alpha = if (st.canUndo) 1f else .35f)) }
        IconButton({ st.drawing = false }) { Icon(Icons.Rounded.Check, "Done", tint = cs.onTertiary) }
    }
}


/** A free line becomes an item of its own: its points are kept inside its own box (0..1), so it scales and turns like any mark. */
private fun freehand(pts: List<androidx.compose.ui.geometry.Offset>, id: String, color: Int): Stuck? {
    if (pts.size < 3) return null
    val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }; val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
    val w = (maxX - minX).coerceAtLeast(.02f); val h = (maxY - minY).coerceAtLeast(.02f)
    if (w + h < .03f) return null
    val step = (pts.size / 70).coerceAtLeast(1)
    val kept = pts.filterIndexed { i, _ -> i % step == 0 || i == pts.lastIndex }
    val text = kept.joinToString(";") { "%.3f,%.3f".format(java.util.Locale.US, (it.x - minX) / w, (it.y - minY) / h) }
    return Stuck(id, "f:$text", minX, minY, w, 0f, color, h)
}

private fun freehandPoints(k: String): List<androidx.compose.ui.geometry.Offset> =
    k.substring(2).split(';').mapNotNull { p -> val v = p.split(','); val x = v.getOrNull(0)?.toFloatOrNull(); val y = v.getOrNull(1)?.toFloatOrNull(); if (x != null && y != null) androidx.compose.ui.geometry.Offset(x, y) else null }
