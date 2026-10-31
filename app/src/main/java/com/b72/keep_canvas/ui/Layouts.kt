@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.cutPath
import kotlin.math.max
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.seedOf

/** Page layouts for plain text and photo notes (smart extraction is for notes built around one fact). */
class PageLayout(val id: String, val name: String, val blurb: String)

val LAYOUTS = listOf(
    PageLayout("", "Plain", "Title, photo, words"),
    PageLayout("cover", "Cover", "The title set in the empty space of your photo; words in a box below"),
    PageLayout("poster", "Poster", "A high-contrast photo bleeding off one edge, title beneath, a tall column of words"),
    PageLayout("wordmark", "Wordmark", "A giant title, small captions, photos on a loose grid"),
    PageLayout("column", "Column", "A panel of colour: small caption left, large light title right, photos below"),
    PageLayout("script", "Script", "A bold italic serif heading, serif text, first-line indents"),
    PageLayout("ledger", "Ledger", "Serif text with line numbers in the margin, every fifth"),
)

/** Layouts that replace the title and photo area with their own header. */
val HEADER_LAYOUTS = setOf("cover", "wordmark", "poster", "column")

/** Layouts that need a photo to exist. */
val NEEDS_PHOTO = setOf("cover", "poster")

private fun upper(title: String, n: Int = 3) = breakTitle(title.uppercase(), n)

private fun onColor(c: Color) = if (c.luminance() > .5f) Color(0xFF14110F) else Color(0xFFFFFBF5)

@Composable
private fun Snap(photo: Block, pal: NotePalette, tint: Boolean, aspect: Float, modifier: Modifier, shape: Shape = RoundedCornerShape(20.dp), onView: () -> Unit) {
    PhotoImage(photo.path!!, 900, tint, pal.primary, modifier.aspectRatio(aspect).clip(shape).clickable(onClickLabel = "View photo", onClick = onView))
}

/** Average brightness (0..1) of an area of a "cover"-fitted photo, so the title can pick light or dark ink. */
private fun lumaIn(bm: ImageBitmap?, w: Float, h: Float, r: Rect): Float {
    bm ?: return .3f
    val bw = bm.width; val bh = bm.height
    val px = IntArray(bw * bh)
    bm.readPixels(px)
    val s = max(w / bw, h / bh); val ox = (w - bw * s) / 2; val oy = (h - bh * s) / 2
    var sum = 0f
    for (gx in 0 until 8) for (gy in 0 until 8) {
        val x = r.left + (gx + .5f) / 8 * r.width; val y = r.top + (gy + .5f) / 8 * r.height
        val c = px[((y - oy) / s).toInt().coerceIn(0, bh - 1) * bw + ((x - ox) / s).toInt().coerceIn(0, bw - 1)]
        sum += (0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)) / 255f
    }
    return sum / 64f
}

/**
 * Cover: the title is set in the photo's own negative space, found from the subject ML Kit cut out. Where the subject can
 * stand proud, it is laid back over the lettering. With no clear subject the title sits below the photo instead.
 * The page keeps its colour all the way down and the words sit in their own box underneath.
 */
@Composable
fun CoverBlock(title: String, photo: Block, pal: NotePalette, seed: String, tint: Boolean, onTitle: () -> Unit, onView: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(36.dp)
    val cut by produceState<ImageBitmap?>(null, photo.id, photo.path) { value = if (canPopOut(photo)) photo.cutPath()?.let { Images.load(it, 1100) } else null }
    val small by produceState<ImageBitmap?>(null, photo.path) { value = Images.load(photo.path!!, 240) }
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        val hPx = wPx * 1.25f
        val subj = subjectIn(photo, wPx, hPx)
        val topRoom = subj?.top ?: 0f
        val botRoom = if (subj != null) hPx - subj.bottom else 0f
        val roomy = subj != null && (cut != null || max(topRoom, botRoom) > hPx * .24f)
        val top = cut != null || topRoom >= botRoom
        val band = if (!roomy) Rect(0f, 0f, wPx, hPx * .3f) else if (top) Rect(0f, 0f, wPx, max(topRoom, hPx * .3f)) else Rect(0f, hPx - botRoom, wPx, hPx)
        val ink = if (lumaIn(small, wPx, hPx, band) > .55f) Color(0xFF14110F) else Color(0xFFFFFBF5)
        val lines = upper(title, if (roomy && max(topRoom, botRoom) > hPx * .4f) 3 else 2)
        val type: @Composable (Color, Modifier) -> Unit = { c, m ->
            Column(m.clickable(onClickLabel = "Edit title", onClick = onTitle)) {
                for (l in lines) FitLine(l, flexStyle(400.sp, wght = 900, wdth = 64f, lh = .86f), c, min = 22f)
            }
        }
        if (roomy) Box(Modifier.fillMaxWidth().aspectRatio(.8f).clip(shape)) {
            PhotoImage(photo.path!!, 1200, tint, pal.primary, Modifier.fillMaxSize().clickable(onClickLabel = "View photo", onClick = onView))
            type(ink, Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 16.dp).rotate(if (seedOf(seed) % 2 == 0) -3f else 3f))
            cut?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        } else Column {
            Snap(photo, pal, tint, .9f, Modifier.fillMaxWidth(), shape, onView)
            Spacer(Modifier.height(14.dp))
            type(pal.fg, Modifier)
        }
    }
}

/**
 * Poster: a high-contrast photo set to one edge and a little into the bleed, the title beneath it at the photo's own width,
 * and a tall column of words on the other side. The edge alternates from note to note.
 */
@Composable
fun PosterHeader(title: String, photo: Block, tags: List<String>, pal: NotePalette, seed: String, tint: Boolean, onTitle: () -> Unit, onView: () -> Unit, bodies: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val left = seedOf(seed) % 2 == 0
    val extra = 22.dp
    val cap = TextStyle(fontSize = 10.sp, letterSpacing = 1.4.sp, color = pal.fg.copy(alpha = .65f))
    BoxWithConstraints(modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        val photoW = maxWidth * .56f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (left) Arrangement.Start else Arrangement.End) {
            val photoCol = @Composable {
                Column(
                    Modifier.width(photoW).layout { m, c ->
                        val p = m.measure(c.copy(minWidth = c.maxWidth + extra.roundToPx(), maxWidth = c.maxWidth + extra.roundToPx()))
                        layout(c.maxWidth, p.height) { p.place(if (left) -extra.roundToPx() else 0, 0) }
                    },
                ) {
                    PhotoImage(photo.path!!, 1000, tint, pal.primary, Modifier.fillMaxWidth().aspectRatio(.74f).clickable(onClickLabel = "View photo", onClick = onView))
                    Column(Modifier.padding(start = if (left) extra else 0.dp, end = if (left) 0.dp else extra, top = 10.dp).clickable(onClickLabel = "Edit title", onClick = onTitle)) {
                        Text(tags.firstOrNull()?.uppercase() ?: "NOTE", style = cap, modifier = Modifier.padding(bottom = 4.dp))
                        for (l in upper(title, 4)) FitLine(l, flexStyle(400.sp, wght = 900, wdth = 56f, lh = .86f), pal.fg, min = 16f)
                    }
                }
            }
            val words = @Composable { Column(Modifier.weight(1f).padding(start = if (left) 14.dp else 0.dp, end = if (left) 0.dp else 14.dp)) { bodies() } }
            if (left) { photoCol(); words() } else { words(); photoCol() }
        }
    }
}

/** Wordmark: the title as one huge line of capitals per line break, small caption rows, photos staggered on a loose grid. */
@Composable
fun WordmarkHeader(title: String, photos: List<Block>, tags: List<String>, pal: NotePalette, tint: Boolean, onTitle: () -> Unit, onView: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cap = TextStyle(fontSize = 10.sp, letterSpacing = 1.2.sp, color = pal.fg.copy(alpha = .7f))
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("NOTE", style = cap); Text(tags.firstOrNull()?.uppercase().orEmpty(), style = cap)
        }
        Column(Modifier.clickable(onClickLabel = "Edit title", onClick = onTitle)) {
            for (l in upper(title)) FitLine(l, flexStyle(400.sp, wght = 860, lh = .86f), pal.fg, min = 18f)
        }
        Spacer(Modifier.height(18.dp))
        photos.forEachIndexed { i, p ->
            val wide = i % 2 == 0
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = if (wide) Alignment.Start else Alignment.End) {
                Snap(p, pal, tint, if (wide) 1.35f else .85f, Modifier.fillMaxWidth(if (wide) .74f else .56f), RoundedCornerShape(4.dp)) { onView(i) }
                Row(Modifier.fillMaxWidth(if (wide) .74f else .56f).padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("%02d".format(i + 1), style = cap); Text(tags.getOrNull(i)?.uppercase().orEmpty(), style = cap)
                }
            }
        }
    }
}

/** Column: a panel of the page's colour, a small caption on the left, the title large and light on the right, photos side by side. */
@Composable
fun ColumnHeader(title: String, photos: List<Block>, tags: List<String>, pal: NotePalette, tint: Boolean, onTitle: () -> Unit, onView: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ink = onColor(pal.primary)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(pal.primary).padding(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
            Text(
                (tags.take(2).joinToString("\n").ifEmpty { "Note" }).uppercase(),
                style = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp, color = ink.copy(alpha = .75f)),
                modifier = Modifier.weight(.3f),
            )
            Text(
                title, style = flexStyle(40.sp, wght = 300, wdth = 90f, lh = 1.04f), color = ink, textAlign = TextAlign.End,
                modifier = Modifier.weight(.7f).clickable(onClickLabel = "Edit title", onClick = onTitle),
            )
        }
        if (photos.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            photos.take(2).forEachIndexed { i, p -> Snap(p, pal, tint, if (photos.size == 1) 1.3f else .8f, Modifier.weight(1f), RoundedCornerShape(22.dp)) { onView(i) } }
        }
    }
}

/** Script: a large bold italic serif heading with a short rule beneath. */
@Composable
fun ScriptTitle(title: String, pal: NotePalette, modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = 10.dp)) {
        Text(title, style = TextStyle(fontFamily = Script, fontSize = 52.sp, lineHeight = 56.sp, color = pal.fg))
        Box(Modifier.padding(top = 8.dp).width(56.dp).height(3.dp).background(pal.primary))
    }
}
