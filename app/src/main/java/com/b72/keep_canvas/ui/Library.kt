@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import com.b72.keep_canvas.data.str
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.toPath
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
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
import com.b72.keep_canvas.data.field
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

/** Only recipes get the expressive frames, one each in turn; everything else keeps its plain cover. */
@Composable
private fun coverShape(k: Kind, n: Int): Shape = when (k) {
    Kind.Track -> CircleShape
    Kind.Recipe -> SQUARE_SHAPES[n % SQUARE_SHAPES.size].toShape()
    Kind.Link -> RoundedCornerShape(32.dp)
    else -> RoundedCornerShape(28.dp)
}

/** Where to look in a photo so its subject sits in the middle of a square frame (the subject box is read from the cut-out). */
private fun subjectAlign(b: Block): Alignment {
    val box = b.nums("cutBox")?.takeIf { it.size >= 4 } ?: return Alignment.Center
    val ar = b.dbl("cutAspect")?.toFloat()?.takeIf { it > 0f } ?: return Alignment.Center
    val cx = ((box[0] + box[2]) / 2).toFloat(); val cy = ((box[1] + box[3]) / 2).toFloat()
    fun f(c: Float, ratio: Float) = if (ratio == 1f) .5f else ((c * ratio - .5f) / (ratio - 1f)).coerceIn(0f, 1f)
    return if (ar >= 1f) BiasAlignment(2 * f(cx, ar) - 1f, 0f) else BiasAlignment(0f, 2 * f(cy, 1f / ar) - 1f)
}

/** A picture turned into two tones: its darks become [ink], its lights [paper]. */
private fun duotone(ink: androidx.compose.ui.graphics.Color, paper: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.ColorFilter {
    val lr = .2126f; val lg = .7152f; val lb = .0722f
    fun row(i: Float, p: Float) = floatArrayOf((p - i) * lr, (p - i) * lg, (p - i) * lb, 0f, i * 255f)
    val r = row(ink.red, paper.red); val g = row(ink.green, paper.green); val b = row(ink.blue, paper.blue)
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(r + g + b + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
}

/** A record in the colours of its cover: a flat disc, the cover as a duotone label, a spindle hole. [bold] flips which tone is the disc. */
@Composable
private fun Vinyl(b: Block, bold: Boolean, modifier: Modifier) {
    val art = b.path
    val hue by androidx.compose.runtime.produceState(seedOf(b.id) % 360f, art) { art?.let { p -> hueOfPhoto(p)?.let { value = it } } }
    fun c(s: Float, l: Float) = androidx.compose.ui.graphics.Color.hsl(((hue % 360f) + 360f) % 360f, s, l)
    val disc = if (bold) c(.78f, .56f) else c(.3f, .15f)
    val paper = if (bold) c(.75f, .95f) else c(.8f, .62f)
    val deep = if (bold) c(.85f, .32f) else c(.5f, .1f)
    val line = if (bold) androidx.compose.ui.graphics.Color.Black.copy(alpha = .10f) else androidx.compose.ui.graphics.Color.White.copy(alpha = .10f)
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val ct = center; val r = size.minDimension / 2
            drawCircle(disc, r, ct)
            // quiet hairline grooves, and a few specks like a pressing mark
            for (k in 0..3) drawCircle(line, r * (.9f - k * .09f), ct, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            for ((dx, dy) in listOf(-.52f to -.5f, -.44f to -.56f, -.46f to -.44f, -.36f to -.6f)) drawCircle(deep, 2.6.dp.toPx(), androidx.compose.ui.geometry.Offset(ct.x + dx * r, ct.y + dy * r))
        }
        Box(Modifier.fillMaxSize(.42f).clip(CircleShape).background(paper), contentAlignment = Alignment.Center) {
            if (art != null) {
                val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, art) { value = com.b72.keep_canvas.data.Images.load(art, 400) }
                img?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, colorFilter = duotone(deep, paper)) }
            }
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(disc))
    }
}

/** A shape part-way between two polygons, fitted to whatever frame it is clipped to. */
private class MorphShape(val morph: androidx.graphics.shapes.Morph, val progress: Float) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val path = morph.toPath(progress, android.graphics.Path())
        val b = android.graphics.RectF()
        path.computeBounds(b, true)
        val m = android.graphics.Matrix()
        m.setRectToRect(b, android.graphics.RectF(0f, 0f, size.width, size.height), android.graphics.Matrix.ScaleToFit.FILL)
        path.transform(m)
        return androidx.compose.ui.graphics.Outline.Generic(path.asComposePath())
    }
}

private fun lerpDp(a: androidx.compose.ui.unit.Dp, b: androidx.compose.ui.unit.Dp, t: Float) = a + (b - a) * t

/** What a spine is made of, read from the cover: the colour of the cover's own edge, a colour that stands apart from it, and an ink that reads on it. */
private class SpineLook(val bg: androidx.compose.ui.graphics.Color, val ink: androidx.compose.ui.graphics.Color, val accent: androidx.compose.ui.graphics.Color)

/**
 * No source has pictures of spines, so one is made from the cover: a hardback's spine usually carries on the colour at the cover's
 * edge, so that edge sets the cloth, and the cover's brightest other colour becomes the rules and the mark.
 */
private suspend fun spineLook(path: String): SpineLook? {
    val bmp = com.b72.keep_canvas.data.Images.load(path, 160) ?: return null
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val ab = bmp.asAndroidBitmap()
        val all = androidx.palette.graphics.Palette.from(ab).generate()
        val edge = androidx.palette.graphics.Palette.from(ab).setRegion(0, 0, (ab.width * .14f).toInt().coerceAtLeast(2), ab.height).generate().dominantSwatch?.rgb
        val bg = edge ?: all.dominantSwatch?.rgb ?: return@withContext null
        val accent = listOfNotNull(all.vibrantSwatch, all.lightVibrantSwatch, all.darkVibrantSwatch, all.mutedSwatch)
            .map { it.rgb }.firstOrNull { androidx.core.graphics.ColorUtils.calculateContrast(it, bg) >= 1.8 }
        val inkRgb = if (androidx.core.graphics.ColorUtils.calculateLuminance(bg) > .42) 0xFF16130F.toInt() else 0xFFF6F1E7.toInt()
        SpineLook(androidx.compose.ui.graphics.Color(bg), androidx.compose.ui.graphics.Color(inkRgb), androidx.compose.ui.graphics.Color(accent ?: inkRgb))
    }
}

/** A book on the shelf is its spine until it is the one in the middle, when it turns to show its cover; it turns back as it is set aside. */
@Composable
private fun BookItem(b: Block, p: Float, h: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val hue by androidx.compose.runtime.produceState(seedOf(b.id) % 360f, b.path) { b.path?.let { pa -> hueOfPhoto(pa)?.let { value = it } } }
    val look by androidx.compose.runtime.produceState<SpineLook?>(null, b.path) { value = b.path?.let { spineLook(it) } }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val fallback = androidx.compose.ui.graphics.Color.hsl(((hue % 360f) + 360f) % 360f, .55f, if (dark) .3f else .42f)
    val fallbackInk = androidx.compose.ui.graphics.Color.hsl(((hue % 360f) + 360f) % 360f, .7f, .93f)
    val spine = look?.bg ?: fallback
    val ink = look?.ink ?: fallbackInk
    val accent = look?.accent ?: fallbackInk
    // a thicker book has a wider spine
    val pages = b.field("pages").toIntOrNull() ?: 0
    val thick = if (pages > 0) (40 + pages / 9).coerceIn(50, 90).dp else 62.dp
    fun shade(c: androidx.compose.ui.graphics.Color, f: Float) = androidx.compose.ui.graphics.lerp(c, androidx.compose.ui.graphics.Color.Black, f)
    Box(
        Modifier.width(lerpDp(thick, 220.dp, p)).height(h).graphicsLayer {
            cameraDistance = 16f * density
            // the spine turns edge-on, the cover comes round from the other side
            rotationY = if (p < .5f) -p * 180f else (1f - p) * 180f
        }.clickable(onClickLabel = "Open", onClick = onClick),
    ) {
        if (p < .5f) Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 10.dp, bottomEnd = 10.dp))
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(shade(spine, .22f), spine, spine, shade(spine, .16f)))),
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                // the hinge grooves of a bound book, either side of the spine
                for (x in listOf(7.dp.toPx(), size.width - 7.dp.toPx())) drawLine(androidx.compose.ui.graphics.Color.Black.copy(alpha = .16f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
                drawRect(androidx.compose.ui.graphics.Color.White.copy(alpha = .06f), androidx.compose.ui.geometry.Offset(size.width * .3f, 0f), androidx.compose.ui.geometry.Size(size.width * .14f, size.height))
            }
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth(.5f).height(2.5.dp).background(accent))
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth(.5f).height(1.dp).background(accent.copy(alpha = .6f)))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Row(Modifier.wrapContentSize(unbounded = true).width(h - 130.dp).rotate(90f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            b.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = ink, modifier = Modifier.weight(1f),
                            style = TextStyle(fontFamily = FontFamily.Serif, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 19.sp, letterSpacing = .6.sp),
                        )
                        val au = b.field("author")
                        if (au.isNotBlank()) {
                            Spacer(Modifier.width(10.dp))
                            Text(au.uppercase(), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, color = ink.copy(alpha = .72f), style = TextStyle(fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), modifier = Modifier.width(96.dp))
                        }
                    }
                }
                Box(Modifier.size(16.dp).clip(CircleShape).background(accent.copy(alpha = .0f)).border(1.5.dp, accent, CircleShape))
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth(.5f).height(1.dp).background(accent.copy(alpha = .6f)))
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth(.5f).height(2.5.dp).background(accent))
                Spacer(Modifier.height(20.dp))
            }
        } else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp)).background(spine)) {
            if (b.path != null) PhotoImage(b.path, 700, false, spine, Modifier.fillMaxSize())
            else Icon(Icons.Rounded.MenuBook, null, Modifier.align(Alignment.Center).size(56.dp), tint = ink)
        }
    }
}

/** How far a card in the fan leans, in degrees. Flip the sign if the cards behind the front one lean the wrong way. */
private const val FAN_LEAN = -34f

/**
 * The films, as a fan of posters in depth: the one in front stands square, the ones behind it step up and away, each leaning back with
 * the perspective, and the ones you have passed swing off to the left. Drag along the fan to move through it; tap a card behind to bring
 * it forward, the one in front to open it.
 */
@Composable
private fun FilmFan(items: List<Pair<Note, Block>>, index: Int, onIndex: (Int) -> Unit, onOpen: (String) -> Unit, h: androidx.compose.ui.unit.Dp) {
    val cs = MaterialTheme.colorScheme
    val n = items.size
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pos = remember { androidx.compose.animation.core.Animatable(index.coerceIn(0, (n - 1).coerceAtLeast(0)).toFloat()) }
    androidx.compose.runtime.LaunchedEffect(n) { pos.snapTo(pos.value.coerceIn(0f, (n - 1).coerceAtLeast(0).toFloat())) }
    androidx.compose.runtime.LaunchedEffect(pos) { androidx.compose.runtime.snapshotFlow { pos.value }.collect { onIndex(kotlin.math.round(it).toInt().coerceIn(0, (n - 1).coerceAtLeast(0))) } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val step = with(density) { 110.dp.toPx() }
    val drag = androidx.compose.foundation.gestures.rememberDraggableState { dx -> scope.launch { pos.snapTo((pos.value - dx / step).coerceIn(-.25f, n - 1f + .25f)) } }
    val w = h * .66f
    fun settle(to: Int) { scope.launch { pos.animateTo(to.coerceIn(0, n - 1).toFloat(), androidx.compose.animation.core.spring(.8f, 220f)) } }
    Box(
        Modifier.fillMaxWidth().height(h + 36.dp).clipToBounds().draggable(drag, androidx.compose.foundation.gestures.Orientation.Horizontal, onDragStopped = { v ->
            settle(kotlin.math.round(pos.value - v / step * .3f).toInt())
        }),
    ) {
        val p = pos.value
        val order = items.indices.filter { it - p > -1.7f && it - p < 5.5f }.sortedByDescending { kotlin.math.abs(it - p) }
        for (i in order) {
            val d = i - p
            val b = items[i].second
            val front = kotlin.math.abs(d) < .5f
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 28.dp).width(w).height(h)
                    .graphicsLayer {
                        cameraDistance = 14f * density.density
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (d >= 0f) 0f else 1f, .5f)
                        if (d >= 0f) {
                            val k = d.coerceAtMost(4f)
                            translationX = k * 52.dp.toPx() * (1f - .05f * k)
                            translationY = -k * 15.dp.toPx()
                            val sc = (1f - .07f * k).coerceAtLeast(.62f); scaleX = sc; scaleY = sc
                            rotationY = FAN_LEAN * (d.coerceAtMost(1f))
                            alpha = (1f - (d - 3f).coerceIn(0f, 1.5f) / 1.5f)
                        } else {
                            val k = -d
                            translationX = -k * 300.dp.toPx()
                            rotationY = -FAN_LEAN * k.coerceAtMost(1f) * 1.2f
                            rotationZ = -5f * k
                            alpha = (1f + d * .55f).coerceIn(0f, 1f)
                        }
                        shadowElevation = if (d in -.5f..2f) 18.dp.toPx() else 0f
                        shape = RoundedCornerShape(22.dp); clip = true
                    }
                    .background(cs.primaryContainer)
                    .clickable(onClickLabel = if (front) "Open" else "Bring forward") { if (front) onOpen(items[i].first.id) else settle(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (b.path != null) PhotoImage(b.path, 700, false, cs.primary, Modifier.fillMaxSize())
                else Icon(Icons.Rounded.Movie, null, Modifier.size(48.dp), tint = cs.onPrimaryContainer)
            }
        }
        val at = kotlin.math.round(p).toInt().coerceIn(0, (n - 1).coerceAtLeast(0)) + 1
        Text(
            "$at / $n", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = cs.onSurface,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp).border(1.dp, cs.onSurface.copy(alpha = .5f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** A film waits as a frame of film stock, sprocket holes down both edges, and opens out to its whole poster in the middle. */
@Composable
private fun FilmItem(b: Block, p: Float, h: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.width(lerpDp(104.dp, 220.dp, p)).height(h).clip(RoundedCornerShape(lerpDp(8.dp, 30.dp, p))).background(cs.primaryContainer).clickable(onClickLabel = "Open", onClick = onClick),
    ) {
        if (b.path != null) PhotoImage(b.path, 700, false, cs.primary, Modifier.fillMaxSize()) else Icon(Icons.Rounded.Movie, null, Modifier.align(Alignment.Center).size(48.dp), tint = cs.onPrimaryContainer)
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val a = (1f - p).coerceIn(0f, 1f)
            if (a < .02f) return@Canvas
            val band = 16.dp.toPx()
            drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = .78f * a), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Size(band, size.height))
            drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = .78f * a), androidx.compose.ui.geometry.Offset(size.width - band, 0f), androidx.compose.ui.geometry.Size(band, size.height))
            var y = 10.dp.toPx()
            while (y < size.height - 10.dp.toPx()) {
                val hole = androidx.compose.ui.graphics.Color.White.copy(alpha = .85f * a)
                drawRoundRect(hole, androidx.compose.ui.geometry.Offset(4.dp.toPx(), y), androidx.compose.ui.geometry.Size(8.dp.toPx(), 11.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                drawRoundRect(hole, androidx.compose.ui.geometry.Offset(size.width - 12.dp.toPx(), y), androidx.compose.ui.geometry.Size(8.dp.toPx(), 11.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                y += 24.dp.toPx()
            }
        }
    }
}

/** A record keeps spinning while it is the one in the middle, and settles to a stop when it is set aside. */
@Composable
private fun TrackItem(b: Block, look: Int, focused: Boolean, p: Float, h: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val angle = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(focused) {
        if (focused) {
            while (true) { angle.animateTo(angle.value + 360f, androidx.compose.animation.core.tween(7000, easing = androidx.compose.animation.core.LinearEasing)) }
        } else {
            val rest = kotlin.math.ceil(angle.value / 360f) * 360f + 360f
            angle.animateTo(rest, androidx.compose.animation.core.spring(1f, 40f))
            angle.snapTo(0f)
        }
    }
    val m = Modifier.width(220.dp).height(h).graphicsLayer { val k = .8f + .2f * p; scaleX = k; scaleY = k; rotationZ = angle.value }
    if (look == 0) Box(m.clip(CircleShape).background(cs.primaryContainer).clickable(onClickLabel = "Open", onClick = onClick), contentAlignment = Alignment.Center) {
        if (b.path != null) PhotoImage(b.path, 600, false, cs.primary, Modifier.fillMaxSize()) else Icon(Icons.Rounded.MusicNote, null, Modifier.size(56.dp), tint = cs.onPrimaryContainer)
        Box(Modifier.size(34.dp).clip(CircleShape).background(cs.surface))
    } else Vinyl(b, look == 1, m.clickable(onClickLabel = "Open", onClick = onClick))
}

/** A dish sits as a plain round plate and, in the middle, morphs into its own expressive shape. */
@Composable
private fun RecipeItem(b: Block, slot: Int, p: Float, h: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val morph = androidx.compose.runtime.remember(slot) { androidx.graphics.shapes.Morph(MaterialShapes.Circle, SQUARE_SHAPES[slot % SQUARE_SHAPES.size]) }
    Box(
        Modifier.width(220.dp).height(h).graphicsLayer { val k = .78f + .22f * p; scaleX = k; scaleY = k }
            .clip(MorphShape(morph, p.coerceIn(0f, 1f))).background(cs.primaryContainer).clickable(onClickLabel = "Open", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (b.path != null) PhotoImage(b.path, 600, false, cs.primary, Modifier.fillMaxSize(), subjectAlign(b)) else Icon(Icons.Rounded.Restaurant, null, Modifier.size(56.dp), tint = cs.onPrimaryContainer)
    }
}

/** Everything that holds structured data (songs, books, films, recipes, links) lives here, one shelf per kind. */
@Composable
fun LibraryScreen(loaded: Loaded?, onOpen: (String) -> Unit, onAdd: (Kind) -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val notes = loaded?.library?.notes.orEmpty()
    var shelf by rememberSaveable { mutableIntStateOf(0) }
    val counts = remember(notes) { SHELVES.map { s -> notes.count { n -> n.blocks.any { it.kind == s.kind } } } }
    var stage by rememberSaveable { mutableIntStateOf(-1) }
    val all = remember(notes, shelf) {
        notes.asReversed().mapNotNull { n -> n.blocks.firstOrNull { it.kind == SHELVES[shelf].kind }?.let { n to it } }
    }
    val items = remember(all, stage) { if (stage < 0) all else all.filter { it.second.str("status") == STATUS_KEYS[stage] } }
    val stats = remember(all) {
        val done = all.count { it.second.str("status") == "done" }
        val rated = all.mapNotNull { it.second.dbl("rating") }.filter { it > 0 }
        listOfNotNull("${all.size} in all", if (done > 0) "$done ${statusLabels(SHELVES[shelf].kind).getOrNull(2)?.lowercase() ?: "done"}" else null, if (rated.isNotEmpty()) "avg %.1f★".format(rated.average()) else null).joinToString("  ·  ")
    }
    val ls = rememberLazyListState()
    val isFilm = SHELVES[shelf].kind == Kind.Film
    var filmIdx by rememberSaveable { mutableIntStateOf(0) }
    val focus by remember {
        derivedStateOf {
            val info = ls.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset - info.beforeContentPadding) }?.index ?: 0
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().bottomBlur(4.dp, .8f).statusBarsPadding().padding(top = 16.dp)) {
        TabTitle("Library", counts.sum(), Modifier.padding(horizontal = 20.dp))
        Text("Everything you saved that has a shape of its own.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SHELVES.forEachIndexed { i, s ->
                Row(Modifier.clickable(onClickLabel = s.name) { shelf = i; stage = -1 }, verticalAlignment = Alignment.Top) {
                    Text(s.name, style = flexStyle(22.sp, wght = if (i == shelf) 650 else 420, lh = 1f), color = if (i == shelf) cs.onSurface else cs.onSurface.copy(alpha = .35f))
                    Text("${counts[i]}", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = if (i == shelf) cs.primary else cs.onSurface.copy(alpha = .35f), modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
        if (all.isNotEmpty()) {
            Text(stats.uppercase(), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
            val labels = statusLabels(SHELVES[shelf].kind)
            if (labels.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(stage < 0, { stage = -1 }, label = { Text("All") })
                labels.forEachIndexed { i, l -> androidx.compose.material3.FilterChip(stage == i, { stage = if (stage == i) -1 else i }, label = { Text(l) }) }
            }
        }
        if (items.isEmpty()) {
            Text(
                if (all.isNotEmpty()) "Nothing here yet. Mark things from their own page." else "Nothing on the ${SHELVES[shelf].name.lowercase()} shelf yet. Tap Add to put the first one here.",
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(24.dp),
            )
        } else {
            val cur = items.getOrNull((if (isFilm) filmIdx else focus).coerceIn(0, items.lastIndex))!!
            Text(
                cur.second.title.ifBlank { cur.first.title }.ifBlank { SHELVES[shelf].name },
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = flexStyle(40.sp, wght = 560, wdth = 96f, lh = 1f), color = cs.onSurface, modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(cur.second.recSub(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                val side = (maxWidth - 220.dp) / 2
                if (isFilm) FilmFan(items, filmIdx, { filmIdx = it }, onOpen = onOpen, h = 320.dp) else LazyRow(
                    state = ls, flingBehavior = rememberSnapFlingBehavior(ls),
                    contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(items, key = { _, p -> p.second.id }) { i, p ->
                        val b = p.second
                        val focused = i == focus
                        // 0 when it is set aside, 1 when it is the one in the middle; every shelf plays its own animation off this
                        val pr by animateFloatAsState(if (focused) 1f else 0f, androidx.compose.animation.core.spring(.7f, 200f), label = "shelf")
                        val h = when (b.kind) { Kind.Film, Kind.Book -> 320.dp; else -> 220.dp }
                        val slot = items.size - 1 - i // stable as newer items are added in front, and neighbours always differ
                        val go = { onOpen(p.first.id) }
                        when (b.kind) {
                            Kind.Book -> BookItem(b, pr, h, go)
                            Kind.Film -> FilmItem(b, pr, h, go)
                            Kind.Track -> TrackItem(b, slot % 3, focused, pr, h, go)
                            Kind.Recipe -> RecipeItem(b, slot, pr, h, go)
                            else -> Box(
                                Modifier.width(220.dp).height(h).graphicsLayer { val k = .84f + .16f * pr; scaleX = k; scaleY = k; rotationZ = (1f - pr) * (if (i % 2 == 0) -5f else 5f) }
                                    .clip(RoundedCornerShape(32.dp)).background(cs.primaryContainer).clickable(onClickLabel = "Open", onClick = go),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (b.path != null) PhotoImage(b.path, 600, false, cs.primary, Modifier.fillMaxSize())
                                else Icon(SHELVES[shelf].icon, null, Modifier.size(56.dp), tint = cs.onPrimaryContainer)
                            }
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
    androidx.compose.material3.ExtendedFloatingActionButton(
        text = { Text("Add " + listOf("song", "book", "film or show", "recipe", "link")[shelf]) },
        icon = { Icon(Icons.Rounded.Add, null) },
        onClick = { onAdd(SHELVES[shelf].kind) },
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 104.dp),
    )
    }
}
