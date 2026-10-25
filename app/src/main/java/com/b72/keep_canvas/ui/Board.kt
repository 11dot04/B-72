@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Loaded
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.ui.board.Card
import com.b72.keep_canvas.ui.board.CardFace
import com.b72.keep_canvas.ui.board.Lay
import com.b72.keep_canvas.ui.board.Scale
import com.b72.keep_canvas.ui.board.Tones
import com.b72.keep_canvas.ui.board.WIDE
import com.b72.keep_canvas.ui.board.arrange
import com.b72.keep_canvas.ui.board.bottomOf
import com.b72.keep_canvas.ui.board.cardWidth
import com.b72.keep_canvas.ui.board.cardsFor
import com.b72.keep_canvas.ui.board.lay
import com.b72.keep_canvas.ui.board.placeKey
import com.b72.keep_canvas.ui.board.settle
import com.b72.keep_canvas.ui.board.valid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private fun hsl(h: Float, s: Float, l: Float) = Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, l)))

private fun tonesFor(hue: Float, dark: Boolean, error: Color) =
    if (!dark) Tones(
        bg = hsl(hue, .45f, .95f), onBg = hsl(hue, .3f, .12f),
        primary = hsl(hue, .55f, .38f), onPrimary = hsl(hue, .4f, .97f),
        pc = hsl(hue, .5f, .86f), onPc = hsl(hue, .4f, .14f),
        sc = hsl(hue + 25f, .38f, .86f), onSc = hsl(hue + 25f, .4f, .14f),
        tc = hsl(hue + 60f, .5f, .84f), onTc = hsl(hue + 60f, .5f, .13f),
        error = error,
    ) else Tones(
        bg = hsl(hue, .25f, .09f), onBg = hsl(hue, .2f, .92f),
        primary = hsl(hue, .6f, .74f), onPrimary = hsl(hue, .5f, .14f),
        pc = hsl(hue, .3f, .26f), onPc = hsl(hue, .4f, .9f),
        sc = hsl(hue + 25f, .25f, .24f), onSc = hsl(hue + 25f, .35f, .9f),
        tc = hsl(hue + 60f, .3f, .28f), onTc = hsl(hue + 60f, .4f, .9f),
        error = error,
    )

/** The canvas takes its colours from the photos on it. */
@Composable
private fun rememberTones(paths: List<String>): Tones {
    val dark = isSystemInDarkTheme()
    val err = MaterialTheme.colorScheme.error
    val fallback = remember { FloatArray(3) }.also { ColorUtils.colorToHSL(MaterialTheme.colorScheme.primary.toArgb(), it) }[0]
    val hue by produceState(fallback, paths) {
        for (p in paths) {
            val bmp = Images.load(p, 160) ?: continue
            val rgb = withContext(Dispatchers.Default) {
                val pal = Palette.from(bmp.asAndroidBitmap()).generate()
                (pal.vibrantSwatch ?: pal.dominantSwatch)?.rgb
            } ?: continue
            val hh = FloatArray(3)
            ColorUtils.colorToHSL(rgb, hh)
            value = hh[0]
            return@produceState
        }
    }
    return remember(hue, dark, err) { tonesFor(hue, dark, err) }
}

/** A tag's canvas: every block of every note with the tag, laid out like a collage. Long-press a card to move it. */
@Composable
fun BoardScreen(repo: Repo, loaded: Loaded?, tag: String, onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val notes = loaded?.library?.notes.orEmpty()
    val photos = remember(notes, tag) {
        notes.filter { tag in it.tags }.flatMap { n -> n.blocks.mapNotNull { b -> b.path?.takeIf { b.kind == Kind.Photo || b.kind in com.b72.keep_canvas.data.RECORD_KINDS } } }.take(4)
    }
    val t = rememberTones(photos)
    val density = LocalDensity.current
    val tm = rememberTextMeasurer()
    var retidy by remember { mutableIntStateOf(0) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var liftTick by remember { mutableIntStateOf(0) }
    val force = remember { booleanArrayOf(false) } // set by the re-tidy button, consumed by the next layout
    val vs = rememberScrollState()
    val hs = rememberScrollState()

    BoxWithConstraints(Modifier.fillMaxSize().background(t.bg)) {
        val swPx = with(density) { maxWidth.toPx() }
        val shPx = with(density) { maxHeight.toPx() }
        val cw = swPx * WIDE
        val s = remember(cw, density, tm) { Scale(cw, density, tm) }
        val topInset = with(density) { WindowInsets.statusBars.getTop(density).toFloat() } + with(density) { 72.dp.toPx() }

        LaunchedEffect(notes, tag, s, retidy) {
            val fresh = cardsFor(notes, tag) { b, big -> val w = cardWidth(b, big); w to lay(b, w, s).h / s.cw }
            val all = force[0]
            force[0] = false
            val changed = withContext(Dispatchers.Default) { arrange(fresh, all) }
            cards = fresh
            if (changed.isNotEmpty()) persist(repo, tag, changed)
        }
        LaunchedEffect(Unit) {
            snapshotFlow { hs.maxValue }.first { it > 0 }
            hs.scrollTo(hs.maxValue / 2)
        }

        val ordered = liftTick.let { cards }.sortedWith(compareBy<Card>({ if (it.low) 0 else 1 }, { it.lift }, { it.order }))
        val bottom by remember(cards) { androidx.compose.runtime.derivedStateOf { (bottomOf(cards) * 20f).roundToInt() / 20f } }
        val contentH = max(shPx, (bottom + .35f) * cw + topInset)
        Box(Modifier.fillMaxSize().verticalScroll(vs).horizontalScroll(hs)) {
            Box(Modifier.size(with(density) { cw.toDp() }, with(density) { contentH.toDp() })) {
                // hand-drawn arrows between the cards of one note, under everything else
                Canvas(Modifier.matchParentSize()) {
                    cards.forEach { it.rev }
                    doodles(cards, s.cw, topInset, t.primary.copy(alpha = .7f))
                }
                for (c in ordered) key(c.id) {
                    BoardCard(
                        c, s, t, topInset,
                        dragging = dragId == c.id,
                        all = cards,
                        onDragStart = {
                            dragId = c.id
                            c.lift = (cards.maxOfOrNull { it.lift } ?: 0) + 1
                            liftTick++
                        },
                        onMove = { dx, dy -> c.x += dx / s.cw; c.y += dy / s.cw; c.moved() },
                        onDrop = {
                            settle(c, cards)
                            c.moved()
                            dragId = null
                            persist(repo, tag, listOf(c))
                        },
                        onOpen = { onOpen(c.noteId) },
                        onNudge = { dx, dy ->
                            c.x = (c.x + dx).coerceAtLeast(0f); c.y = (c.y + dy).coerceAtLeast(0f)
                            settle(c, cards); c.moved(); persist(repo, tag, listOf(c))
                        },
                    )
                }
            }
        }
        if (cards.isEmpty() && loaded != null) Text(
            "Nothing on this canvas yet", style = flexStyle(22.sp, wght = 600, rond = 40f), color = t.onBg.copy(alpha = .6f),
            modifier = Modifier.align(Alignment.Center),
        )

        Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val btn = IconButtonDefaults.iconButtonColors(containerColor = t.pc, contentColor = t.onPc)
            IconButton(onClick = onBack, colors = btn) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(
                tag, style = flexStyle(26.sp, wght = 720, wdth = 108f, rond = 40f), color = t.onBg, maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
        }
        // the same floating toolbar as the note page: one wide action, one icon
        androidx.compose.material3.HorizontalFloatingToolbar(
            expanded = true,
            colors = androidx.compose.material3.FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            leadingContent = { IconButton(onClick = { force[0] = true; retidy++ }) { Icon(Icons.Rounded.AutoFixHigh, "Tidy the whole canvas") } },
            content = {
                androidx.compose.material3.Button(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
                    Text("New note")
                }
            },
        )
    }
}

private fun persist(repo: Repo, tag: String, changed: List<Card>) {
    val latest = repo.loaded?.library?.notes ?: return
    val out = mutableListOf<Note>()
    for ((id, cs) in changed.groupBy { it.noteId }) {
        val n = latest.firstOrNull { it.id == id } ?: continue
        out.add(n.copy(places = n.places + cs.associate { placeKey(tag, it.b) to listOf(it.x.toDouble(), it.y.toDouble()) }))
    }
    if (out.isNotEmpty()) repo.upsertAll(out)
}

@Composable
private fun BoardCard(
    c: Card, s: Scale, t: Tones, topInset: Float,
    dragging: Boolean, all: List<Card>,
    onDragStart: () -> Unit, onMove: (Float, Float) -> Unit, onDrop: () -> Unit, onOpen: () -> Unit, onNudge: (Float, Float) -> Unit,
) {
    val u = s.u
    val l: Lay = remember(c.b, c.w, s) { lay(c.b, c.w, s) }
    val ring = u * .012f
    val bare = c.bare
    val rev = c.rev // the card's position is a plain field; this read makes the card follow it
    val bad = rev >= 0 && dragging && !valid(c, all)
    val start by androidx.compose.runtime.rememberUpdatedState(onDragStart)
    val move by androidx.compose.runtime.rememberUpdatedState(onMove)
    val drop by androidx.compose.runtime.rememberUpdatedState(onDrop)
    val radius = if (bare) 0f else u * .07f
    val ringColor = if (bare) (if (bad) t.error.copy(alpha = .28f) else Color.Transparent) else if (bad) t.error else t.bg
    val pos = animateOffsetAsState(
        Offset(c.x * s.cw, c.y * s.cw + topInset),
        if (dragging) snap<Offset>() else MaterialTheme.motionScheme.defaultSpatialSpec<Offset>(),
        label = "pos",
    )
    val lift by animateFloatAsState(if (dragging) 1.04f else 1f, MaterialTheme.motionScheme.fastSpatialSpec<Float>(), label = "lift")
    Box(
        Modifier
            .offset { IntOffset(pos.value.x.roundToInt(), pos.value.y.roundToInt()) }
            .size(s.dp(c.w * s.cw), s.dp(c.h * s.cw))
            .scale(lift)
            .rotate(Math.toDegrees(c.angle.toDouble()).toFloat())
            .background(ringColor, RoundedCornerShape(s.dp(radius + ring)))
            .padding(s.dp(ring))
            .clip(RoundedCornerShape(s.dp(radius)))
            .semantics {
                contentDescription = c.label
                // dragging is not possible with a screen reader, so moving is offered as actions
                customActions = listOf(
                    CustomAccessibilityAction("Move up") { onNudge(0f, -.06f); true },
                    CustomAccessibilityAction("Move down") { onNudge(0f, .06f); true },
                    CustomAccessibilityAction("Move left") { onNudge(-.06f, 0f); true },
                    CustomAccessibilityAction("Move right") { onNudge(.06f, 0f); true },
                )
            }
            .clickable(onClickLabel = "Open note", onClick = onOpen)
            .pointerInput(c.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start() },
                    onDrag = { ch, amt -> ch.consume(); move(amt.x, amt.y) },
                    onDragEnd = { drop() },
                    onDragCancel = { drop() },
                )
            },
    ) { CardFace(c.b, l, s, t) }
}

/** Hand-drawn connectors, as in the old app: a wavy line with an arrowhead from each card of a note to the next one. */
private fun DrawScope.doodles(cards: List<Card>, cw: Float, topInset: Float, color: androidx.compose.ui.graphics.Color) {
    val u = cw / WIDE
    val stroke = Stroke(width = u * .007f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun px(c: Card) = Rect(c.x * cw, c.y * cw + topInset, (c.x + c.w) * cw, (c.y + c.h) * cw + topInset)
    fun edge(r: Rect, t: Offset): Offset {
        val d = t - r.center
        val k = min(r.width / 2 / (abs(d.x) + 1e-6f), r.height / 2 / (abs(d.y) + 1e-6f))
        return r.center + d * k
    }
    for ((_, list) in cards.groupBy { it.noteId }) {
        if (list.size < 2) continue
        val s = list.sortedBy { it.order }
        for (i in 0 until s.size - 1) {
            val a = px(s[i])
            val b = px(s[i + 1])
            if (a.overlaps(b)) continue
            val p0 = edge(a, b.center)
            val p1 = edge(b, a.center)
            val v = p1 - p0
            val len = v.getDistance()
            if (len < u * .06f) continue
            val dir = v / len
            val nrm = Offset(-dir.y, dir.x)
            val waves = kotlin.math.max(2, (len / (u * .14f)).toInt())
            fun at(t: Float): Offset = p0 + v * t + nrm * (sin(PI.toFloat() * t) * len * .1f + sin(t * 2f * PI.toFloat() * waves) * u * .012f * sin(PI.toFloat() * t))
            val path = Path().apply { moveTo(p0.x, p0.y); for (k in 1..48) at(k / 48f).let { lineTo(it.x, it.y) } }
            drawPath(path, color, style = stroke)
            val from = at(.95f)
            val h = atan2(p1.y - from.y, p1.x - from.x)
            for (da in listOf(2.6f, -2.6f)) drawLine(color, p1, p1 + Offset(cos(h + da), sin(h + da)) * (u * .03f), stroke.width, StrokeCap.Round)
        }
    }
}
