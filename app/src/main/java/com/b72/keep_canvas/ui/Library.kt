@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
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
            val white = androidx.compose.ui.graphics.Color.White
            val hair = androidx.compose.ui.graphics.drawscope.Stroke(.8.dp.toPx())
            drawCircle(disc, r, ct)
            // the pressing: a smooth lead-in at the edge, then rings of fine grooves with a few wider gaps where the tracks end
            val inner = r * .42f * 1.1f; val outer = r * .955f
            for (k in 0..58) {
                val rr = inner + (outer - inner) * k / 58f
                val gap = k % 14 == 0
                drawCircle(if (gap) deep.copy(alpha = .55f) else line.copy(alpha = .35f + (k * 37 % 7) / 14f), rr, ct, style = if (gap) androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()) else hair)
            }
            // light catching the grooves: two opposite wedges, soft at both sides
            drawCircle(
                androidx.compose.ui.graphics.Brush.sweepGradient(
                    0f to androidx.compose.ui.graphics.Color.Transparent, .07f to white.copy(alpha = .2f), .14f to androidx.compose.ui.graphics.Color.Transparent,
                    .5f to androidx.compose.ui.graphics.Color.Transparent, .57f to white.copy(alpha = .2f), .64f to androidx.compose.ui.graphics.Color.Transparent,
                    1f to androidx.compose.ui.graphics.Color.Transparent, center = ct,
                ), r * .97f, ct,
            )
            // the bevelled edge
            drawCircle(white.copy(alpha = .16f), r - .7.dp.toPx(), ct, style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx()))
            drawCircle(androidx.compose.ui.graphics.Color.Black.copy(alpha = .28f), r * .965f, ct, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        Box(Modifier.fillMaxSize(.42f).clip(CircleShape).background(paper), contentAlignment = Alignment.Center) {
            if (art != null) {
                val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, art) { value = com.b72.keep_canvas.data.Images.load(art, 400) }
                img?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, colorFilter = duotone(deep, paper)) }
            }
        }
        // the ring the label sits in, and the spindle hole
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize(.44f)) {
            drawCircle(androidx.compose.ui.graphics.Color.Black.copy(alpha = .3f), size.minDimension / 2, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
        Box(Modifier.size(11.dp).clip(CircleShape).background(disc).border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .3f), CircleShape))
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

/**
 * A book on the shelf is its spine until it is the one in the middle, when it turns to show its cover; it turns back as it is set aside.
 * The spine is set like a small paperback's: the author at one end, the title at the other, and a pale band between them carrying a
 * mark and the book's number. Flat colour, no effects.
 */
@Composable
private fun BookItem(b: Block, p: Float, h: androidx.compose.ui.unit.Dp, no: Int, thick: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val hue by androidx.compose.runtime.produceState(seedOf(b.id) % 360f, b.path) { b.path?.let { pa -> hueOfPhoto(pa)?.let { value = it } } }
    val look by androidx.compose.runtime.produceState<SpineLook?>(null, b.path) { value = b.path?.let { spineLook(it) } }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val fallback = androidx.compose.ui.graphics.Color.hsl(((hue % 360f) + 360f) % 360f, .5f, if (dark) .22f else .3f)
    val fallbackInk = androidx.compose.ui.graphics.Color.hsl(((hue % 360f) + 360f) % 360f, .6f, .93f)
    val spine = look?.bg ?: fallback
    val ink = look?.ink ?: fallbackInk
    val band = look?.accent ?: ink
    val onBand = if (androidx.core.graphics.ColorUtils.calculateLuminance(band.toArgb()) > .5) androidx.compose.ui.graphics.Color(0xFF16130F) else androidx.compose.ui.graphics.Color(0xFFF6F1E7)
    val titleSp = (thick.value * .2f).coerceIn(13f, 19f)
    val authorSp = (thick.value * .13f).coerceIn(10f, 13f)
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            cameraDistance = 16f * density
            // the spine turns edge-on, the cover comes round from the other side
            rotationY = if (p < .5f) -p * 180f else (1f - p) * 180f
        }.clickable(onClickLabel = "Open", onClick = onClick),
    ) {
        if (p < .5f) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(spine), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Row(Modifier.wrapContentSize(unbounded = true).width(h).height(thick).rotate(90f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(.8f).fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(b.field("author"), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End, color = ink.copy(alpha = .85f), style = flexStyle(authorSp.sp, wght = 520, lh = 1.15f))
                }
                Box(Modifier.width(34.dp).fillMaxSize().background(band), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(12.dp).border(2.dp, onBand, CircleShape))
                        Text("%02d".format(no), color = onBand, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp))
                    }
                }
                Box(Modifier.weight(1.3f).fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    Text(b.title.ifBlank { "Untitled" }, maxLines = 2, overflow = TextOverflow.Ellipsis, color = ink, style = flexStyle(titleSp.sp, wght = 680, wdth = 90f, lh = 1.1f))
                }
            }
        } else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp)).background(spine)) {
            if (b.path != null) PhotoImage(b.path, 700, false, spine, Modifier.fillMaxSize())
            else Icon(Icons.Rounded.MenuBook, null, Modifier.align(Alignment.Center).size(56.dp), tint = ink)
        }
    }
}

/** How far a card in the fan leans, in degrees. Flip the sign if the cards behind the front one lean the wrong way. */
private const val FAN_LEAN = -34f

/**
 * The cube view is the concept's block of cards: the front poster stands turned a little, and the cards behind it show as a stack of
 * thin edges down its side, like a deck seen at an angle. Turned this far, the front face and the edges show together.
 */
private const val CUBE_REST = -26f
private const val CUBE_EDGES = 14
private val CUBE_EDGE_W = 7.dp

/**
 * The films, as a fan of posters in depth: the one in front stands square, the ones behind it step up and away, each leaning back with
 * the perspective, and the ones you have passed swing off to the left. Drag along the fan to move through it; tap a card behind to bring
 * it forward, the one in front to open it. Everything that moves while you drag is read in the draw phase, so a drag recomposes nothing.
 */
@Composable
private fun FilmFan(items: List<Pair<Note, Block>>, index: Int, onIndex: (Int) -> Unit, onOpen: (String) -> Unit, h: androidx.compose.ui.unit.Dp) {
    var cubePref by UiPrefs.cube
    val cube = cubePref == 1
    val cs = MaterialTheme.colorScheme
    val n = items.size
    val last = (n - 1).coerceAtLeast(0)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pos = remember { androidx.compose.animation.core.Animatable(index.coerceIn(0, last).toFloat()) }
    androidx.compose.runtime.LaunchedEffect(n) { pos.snapTo(pos.value.coerceIn(0f, last.toFloat())) }
    // which card is nearest the front changes rarely, so only that is read while composing; the cards are stacked by it
    val near by remember { androidx.compose.runtime.derivedStateOf { kotlin.math.round(pos.value).toInt().coerceIn(0, last) } }
    val fl by remember { androidx.compose.runtime.derivedStateOf { kotlin.math.floor(pos.value).toInt().coerceIn(0, last) } }
    androidx.compose.runtime.LaunchedEffect(Unit) { androidx.compose.runtime.snapshotFlow { near }.collect { onIndex(it) } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val step = with(density) { (if (cube) 170.dp else 110.dp).toPx() }
    val drag = androidx.compose.foundation.gestures.rememberDraggableState { dx ->
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { pos.snapTo((pos.value - dx / step).coerceIn(-.25f, last + .25f)) }
    }
    val w = h * .66f
    fun settle(to: Int) { scope.launch { pos.animateTo(to.coerceIn(0, last).toFloat(), androidx.compose.animation.core.spring(.85f, 260f)) } }
    Box(
        Modifier.fillMaxWidth().height(h + 72.dp).draggable(drag, androidx.compose.foundation.gestures.Orientation.Horizontal, onDragStopped = { v ->
            settle(kotlin.math.round(pos.value - v / step * .3f).toInt())
        }),
    ) {
        val order = if (cube) listOf(fl + 1, fl).filter { it in 0..last } else (near - 2..near + 8).filter { it in 0..last }.sortedByDescending { kotlin.math.abs(it - near) }
        // the edges of the cards behind the front one, down the side of the block; the stack thins as the cards are used up
        if (cube) {
            val edges = ((fl + 2)..(fl + 1 + CUBE_EDGES)).filter { it in 0..last }
            if (edges.isNotEmpty()) Row(
                Modifier.align(Alignment.Center).width(CUBE_EDGE_W * CUBE_EDGES).height(h)
                    .graphicsLayer {
                        val wPx = w.toPx(); val ePx = CUBE_EDGE_W.toPx() * CUBE_EDGES
                        val cxf = -(ePx * kotlin.math.cos(Math.toRadians((CUBE_REST + 90f).toDouble())).toFloat()) / 2f
                        cameraDistance = 3000f * density.density
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)
                        scaleX = ((last - pos.value - 1f).coerceAtLeast(0f) / CUBE_EDGES).coerceIn(0f, 1f)
                        rotationY = CUBE_REST + 90f
                        translationX = cxf + wPx / 2f * kotlin.math.cos(Math.toRadians(CUBE_REST.toDouble())).toFloat() + ePx / 2f
                        shape = RoundedCornerShape(3.dp); clip = true
                    },
            ) {
                for (e in edges) Box(Modifier.width(CUBE_EDGE_W).fillMaxHeight().background(cs.primaryContainer).border(.5.dp, androidx.compose.ui.graphics.Color.Black.copy(alpha = .4f))) {
                    items[e].second.path?.let { PhotoImage(it, 120, false, cs.primary, Modifier.fillMaxSize()) }
                }
            }
            if (edges.isNotEmpty()) Box(Modifier.align(Alignment.Center).width(CUBE_EDGE_W * CUBE_EDGES).height(h).graphicsLayer {
                val wPx = w.toPx(); val ePx = CUBE_EDGE_W.toPx() * CUBE_EDGES
                val cxf = -(ePx * kotlin.math.cos(Math.toRadians((CUBE_REST + 90f).toDouble())).toFloat()) / 2f
                cameraDistance = 3000f * density.density
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)
                scaleX = ((last - pos.value - 1f).coerceAtLeast(0f) / CUBE_EDGES).coerceIn(0f, 1f)
                rotationY = CUBE_REST + 90f
                translationX = cxf + wPx / 2f * kotlin.math.cos(Math.toRadians(CUBE_REST.toDouble())).toFloat() + ePx / 2f
                shape = RoundedCornerShape(3.dp); clip = true
                alpha = .42f
            }.background(androidx.compose.ui.graphics.Color.Black))
        }
        for (i in order) androidx.compose.runtime.key(items[i].second.id) {
            val b = items[i].second
            Box(
                Modifier.align(if (cube) Alignment.Center else Alignment.CenterStart).padding(start = if (cube) 0.dp else 28.dp).width(w).height(h)
                    .graphicsLayer {
                        val cubed = cube
                        val d = i - pos.value
                        if (cubed) {
                            // the front card stands at rest turned a little; one that is used leaves to the left, turning edge-on as it goes
                            val ePx = CUBE_EDGE_W.toPx() * CUBE_EDGES
                            val cxf = -(ePx * kotlin.math.cos(Math.toRadians((CUBE_REST + 90f).toDouble())).toFloat()) / 2f
                            cameraDistance = 3000f * density.density
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                            rotationY = if (d >= 0f) CUBE_REST else CUBE_REST + d.coerceAtLeast(-1.2f) * 60f
                            translationX = if (d >= 0f) cxf else cxf + d * w.toPx() * .95f
                            alpha = if (d >= 0f) 1f else (1f + d * 1.1f).coerceIn(0f, 1f)
                        } else if (d >= 0f) {
                            cameraDistance = 14f * density.density
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)
                            val k = d.coerceAtMost(4f)
                            translationX = k * 52.dp.toPx() * (1f - .05f * k)
                            translationY = -k * 15.dp.toPx()
                            val sc = (1f - .07f * k).coerceAtLeast(.62f); scaleX = sc; scaleY = sc
                            rotationY = FAN_LEAN * d.coerceAtMost(1f)
                            alpha = 1f - (d - 3f).coerceIn(0f, 1.5f) / 1.5f
                        } else {
                            cameraDistance = 14f * density.density
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, .5f)
                            val k = -d
                            translationX = -k * 300.dp.toPx()
                            rotationY = -FAN_LEAN * k.coerceAtMost(1f) * 1.2f
                            rotationZ = -5f * k
                            alpha = (1f + d * .55f).coerceIn(0f, 1f)
                        }
                        shape = RoundedCornerShape(if (cubed) 6.dp else 22.dp); clip = true
                    }
                    .background(cs.primaryContainer)
                    .clickable(onClickLabel = "Open") { if (i == near) onOpen(items[i].first.id) else settle(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (b.path != null) PhotoImage(b.path, 700, false, cs.primary, Modifier.fillMaxSize())
                else Icon(Icons.Rounded.Movie, null, Modifier.size(48.dp), tint = cs.onPrimaryContainer)
                // depth without a shadow: the further back a poster is, the more it dims
                Box(Modifier.fillMaxSize().graphicsLayer {
                    // the further back a poster is, the more it dims; on the block a face is lit by how squarely it faces you
                    alpha = if (cube) (i - pos.value).coerceIn(0f, 1f) * .5f
                    else ((i - pos.value).coerceIn(0f, 4f) * .11f)
                }.background(androidx.compose.ui.graphics.Color.Black))
            }
        }
        androidx.compose.material3.FilledTonalIconButton(onClick = { cubePref = 1 - cubePref }, Modifier.align(Alignment.BottomStart).padding(start = 20.dp)) {
            Icon(if (cube) Icons.Rounded.ViewCarousel else Icons.Rounded.ViewInAr, if (cube) "Fan view" else "Cube view")
        }
        Text(
            "${near + 1} / $n", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = cs.onSurface,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp).border(1.dp, cs.onSurface.copy(alpha = .5f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** How wide a book's spine is: in step with its page count, a quarter of a dp to the page, within what text can still be set on. */
private fun bookThick(b: Block): androidx.compose.ui.unit.Dp {
    val pages = b.field("pages").toIntOrNull() ?: 0
    return (if (pages > 0) pages * .24f else 72f).coerceIn(50f, 150f).dp
}

/**
 * The books, side by side as spines, the one in the middle opened out to its cover. Where it is on the shelf is one number, [pos], and
 * every spine's width and place follow from it, so the shelf can always be dragged back to the very first book and out to the last.
 */
@Composable
private fun BookShelf(items: List<Pair<Note, Block>>, index: Int, onIndex: (Int) -> Unit, onOpen: (String) -> Unit, h: androidx.compose.ui.unit.Dp) {
    val n = items.size
    val last = (n - 1).coerceAtLeast(0)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pos = remember { androidx.compose.animation.core.Animatable(index.coerceIn(0, last).toFloat()) }
    androidx.compose.runtime.LaunchedEffect(n) { pos.snapTo(pos.value.coerceIn(0f, last.toFloat())) }
    val near by remember { androidx.compose.runtime.derivedStateOf { kotlin.math.round(pos.value).toInt().coerceIn(0, last) } }
    val fl by remember { androidx.compose.runtime.derivedStateOf { kotlin.math.floor(pos.value).toInt().coerceIn(0, last) } }
    androidx.compose.runtime.LaunchedEffect(Unit) { androidx.compose.runtime.snapshotFlow { near }.collect { onIndex(it) } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val step = with(density) { 84.dp.toPx() }
    val drag = androidx.compose.foundation.gestures.rememberDraggableState { dx ->
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { pos.snapTo((pos.value - dx / step).coerceIn(0f, last.toFloat())) }
    }
    fun settle(to: Int) { scope.launch { pos.animateTo(to.coerceIn(0, last).toFloat(), androidx.compose.animation.core.spring(.85f, 260f)) } }
    val thick = remember(items) { items.map { bookThick(it.second).value } }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(h).clipToBounds().draggable(drag, androidx.compose.foundation.gestures.Orientation.Horizontal, onDragStopped = { v ->
            settle(kotlin.math.round(pos.value - v / step * .35f).toInt())
        }),
    ) {
        val vw = maxWidth.value
        val p = pos.value
        val gap = 12f
        fun open(i: Int) = (1f - kotlin.math.abs(i - p)).coerceIn(0f, 1f)
        val width = FloatArray(n) { thick[it] + (220f - thick[it]) * open(it) }
        val left = FloatArray(n)
        for (i in 1 until n) left[i] = left[i - 1] + width[i - 1] + gap
        val lo = kotlin.math.floor(p).toInt().coerceIn(0, last); val hi = (lo + 1).coerceAtMost(last)
        val frac = (p - lo).coerceIn(0f, 1f)
        val centre = (left[lo] + width[lo] / 2) * (1f - frac) + (left[hi] + width[hi] / 2) * frac
        val shift = vw / 2 - centre
        for (i in 0 until n) {
            val x = left[i] + shift
            if (x > vw || x + width[i] < 0f) continue
            androidx.compose.runtime.key(items[i].second.id) {
                Box(Modifier.offset(x = x.dp).width(width[i].dp).height(h)) {
                    BookItem(items[i].second, open(i), h, n - i, thick[i].dp) { if (i == near) onOpen(items[i].first.id) else settle(i) }
                }
            }
        }
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
    var shelf by UiPrefs.shelf
    val counts = remember(notes) { SHELVES.map { s -> notes.count { n -> n.blocks.any { it.kind == s.kind } } } }
    var stage by rememberSaveable { mutableIntStateOf(-1) }
    val all = remember(notes, shelf) {
        // a note can hold several links, and each is a bookmark of its own; every other shelf shows a note's first
        if (SHELVES[shelf].kind == Kind.Link) notes.asReversed().flatMap { n -> n.blocks.filter { it.kind == Kind.Link }.map { n to it } }
        else notes.asReversed().mapNotNull { n -> n.blocks.firstOrNull { it.kind == SHELVES[shelf].kind }?.let { n to it } }
    }
    val items = remember(all, stage) { if (stage < 0) all else all.filter { it.second.str("status") == STATUS_KEYS[stage] } }
    val stats = remember(all) {
        val done = all.count { it.second.str("status") == "done" }
        val rated = all.mapNotNull { it.second.dbl("rating") }.filter { it > 0 }
        listOfNotNull("${all.size} in all", if (done > 0) "$done ${statusLabels(SHELVES[shelf].kind).getOrNull(2)?.lowercase() ?: "done"}" else null, if (rated.isNotEmpty()) "avg %.1f★".format(rated.average()) else null).joinToString("  ·  ")
    }
    val ls = rememberLazyListState()
    val isFilm = SHELVES[shelf].kind == Kind.Film
    val isBook = SHELVES[shelf].kind == Kind.Book
    var filmIdx by UiPrefs.filmIdx
    var bookIdx by UiPrefs.bookIdx
    val page = rememberScrollState()
    val focus by remember {
        derivedStateOf {
            val info = ls.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset - info.beforeContentPadding) }?.index ?: 0
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().bottomBlur(4.dp, .8f).statusBarsPadding().verticalScroll(page).padding(top = 16.dp)) {
        TabTitle("Library", counts.sum(), Modifier.padding(horizontal = 20.dp))
        Text("Everything you saved that has a shape of its own.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
        FitTabs(SHELVES.map { it.name }, counts.map { it.toString() }, shelf, { shelf = it; stage = -1 }, Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
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
            if (SHELVES[shelf].kind == Kind.Link) {
                LinkShelf(items, onOpen)
                Spacer(Modifier.height(150.dp))
                return@Column
            }
            val cur = items.getOrNull((if (isFilm) filmIdx else if (isBook) bookIdx else focus).coerceIn(0, items.lastIndex))!!
            Text(
                cur.second.title.ifBlank { cur.first.title }.ifBlank { SHELVES[shelf].name },
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = flexStyle(40.sp, wght = 560, wdth = 96f, lh = 1f), color = cs.onSurface, modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(cur.second.recSub(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                val side = (maxWidth - 220.dp) / 2
                if (isFilm) FilmFan(items, filmIdx, { filmIdx = it }, onOpen = onOpen, h = 320.dp) else if (isBook) BookShelf(items, bookIdx, { bookIdx = it }, onOpen, 320.dp) else LazyRow(
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
                            Kind.Book -> BookItem(b, pr, h, items.size - i, 88.dp, go)
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
                Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth().noteContainer(note.id, 32.dp).clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainerHigh)
                    .clickable(onClickLabel = "Open the note") { onOpen(note.id) }.padding(20.dp),
            ) {
                // what it is, then what it is about (and, under that, what you made of it), as the concept's panel has it
                val blk = cur.second
                val about = blk.field("overview").ifBlank { blk.field("desc") }
                val label = when (blk.kind) {
                    Kind.Film -> listOf(blk.field("genre"), blk.field("runtime").takeIf { it.isNotEmpty() }?.let { "$it min" } ?: "").filter { it.isNotEmpty() }.joinToString(" · ")
                    Kind.Book -> listOf(blk.field("publisher"), blk.field("pages").takeIf { it.isNotEmpty() && it != "0" }?.let { "$it pages" } ?: "").filter { it.isNotEmpty() }.joinToString(" · ")
                    else -> blk.field("genre")
                }.ifBlank { (note.tags.firstOrNull() ?: "saved") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label.uppercase(), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = .6.sp), color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowOutward, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                }
                if (about.isNotBlank()) Text(
                    about, maxLines = 5, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp), color = cs.onSurface,
                    modifier = Modifier.padding(top = 10.dp),
                )
                val said = blk.body.ifBlank { note.snippet(140) }
                if (said.isNotBlank()) Text(
                    said, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Serif, fontSize = 17.sp, lineHeight = 23.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = if (about.isBlank()) cs.onSurface else cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = if (about.isBlank()) 10.dp else 12.dp),
                ) else if (about.isBlank()) Text("Open it to add your thoughts.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }
        }
        // room below the last thing, so it can be scrolled clear of the floating bar
        Spacer(Modifier.height(150.dp))
    }
    androidx.compose.material3.ExtendedFloatingActionButton(
        text = { Text("Add " + listOf("song", "book", "film or show", "recipe", "link")[shelf]) },
        icon = { Icon(Icons.Rounded.Add, null) },
        onClick = { onAdd(SHELVES[shelf].kind) },
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 104.dp),
    )
    }
}


/** The Links shelf is a list, not a carousel: bookmarks are looked through and searched, so they are filtered by site and each says what it is. */
@Composable
private fun LinkShelf(items: List<Pair<Note, Block>>, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var site by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(15) }
    val sites = remember(items) { items.groupingBy { linkHost(it.second) }.eachCount().filterKeys { it.isNotEmpty() }.entries.sortedByDescending { it.value }.take(10) }
    val shown = remember(items, site) { if (site.isEmpty()) items else items.filter { linkHost(it.second) == site } }
    if (sites.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.FilterChip(site.isEmpty(), { site = "" }, label = { Text("All sites") })
        for ((h, n) in sites) androidx.compose.material3.FilterChip(site == h, { site = if (site == h) "" else h }, label = { Text("$h  $n") })
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((n, b) in shown.take(limit)) LinkRow(n, b, { onOpen(n.id) })
        if (shown.size > limit) androidx.compose.material3.TextButton({ limit += 20 }, Modifier.align(Alignment.CenterHorizontally)) { Text("Show more (${shown.size - limit})") }
    }
}
