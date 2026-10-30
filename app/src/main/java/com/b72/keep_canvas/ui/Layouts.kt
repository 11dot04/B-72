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
import androidx.compose.foundation.layout.layout
import androidx.compose.foundation.layout.offset
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
    PageLayout("cover", "Cover", "The title across a photo in a Material shape"),
    PageLayout("wordmark", "Wordmark", "A giant title, small captions, photos on a loose grid"),
    PageLayout("glow", "Glow", "A soft glowing disc behind a light, centred title"),
    PageLayout("poster", "Poster", "Stacked, tilted type, a dot, a photo with a hard shadow"),
    PageLayout("bleed", "Bleed", "Condensed type running off the edge on a block of colour"),
    PageLayout("script", "Script", "Handwritten heading, serif text, first-line indents"),
    PageLayout("ledger", "Ledger", "Serif text with line numbers in the margin, every fifth"),
)

/** Layouts that replace the title and photo area with their own header. */
val HEADER_LAYOUTS = setOf("cover", "wordmark", "glow", "poster", "bleed")

/** A curated few of Material's shapes that hold a photo well; each note keeps its own. */
@Composable
private fun frameFor(seed: String): Pair<Shape, Float> = when (seedOf(seed) % 6) {
    0 -> MaterialShapes.Arch.toShape() to .82f
    1 -> MaterialShapes.Cookie9Sided.toShape() to 1f
    2 -> MaterialShapes.Clover4Leaf.toShape() to 1f
    3 -> MaterialShapes.SoftBurst.toShape() to 1f
    4 -> MaterialShapes.Pill.toShape() to .78f
    else -> MaterialShapes.Flower.toShape() to 1f
}

private fun upper(title: String) = breakTitle(title.uppercase(), 3)

@Composable
private fun Snap(photo: Block, pal: NotePalette, tint: Boolean, aspect: Float, modifier: Modifier, shape: Shape = RoundedCornerShape(20.dp), onView: () -> Unit) {
    PhotoImage(photo.path!!, 900, tint, pal.primary, modifier.aspectRatio(aspect).clip(shape).clickable(onClickLabel = "View photo", onClick = onView))
}

/** Cover: a Material-shaped frame pushed to one side, a touch off level, the title set across its lower part. */
@Composable
fun CoverBlock(title: String, photo: Block, pal: NotePalette, seed: String, tint: Boolean, onTitle: () -> Unit, onView: () -> Unit, modifier: Modifier = Modifier) {
    val (shape, aspect) = frameFor(seed)
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth(.92f).align(Alignment.TopEnd).aspectRatio(aspect).rotate(if (seedOf(seed) % 2 == 0) 2f else -2f).clip(shape)) {
            PhotoImage(photo.path!!, 1200, tint, pal.primary, Modifier.fillMaxSize().clickable(onClickLabel = "View photo", onClick = onView))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.42f to Color.Transparent, 1f to Color.Black.copy(alpha = .75f))))
        }
        Box(Modifier.fillMaxWidth(.66f).align(Alignment.BottomCenter).padding(bottom = 26.dp).clickable(onClickLabel = "Edit title", onClick = onTitle)) {
            FitTitle(title, Color.White, seed, maxLines = 3)
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

/** Glow: a soft disc of the page's colour behind a light, centred title; photos in Material shapes beneath. */
@Composable
fun GlowHeader(title: String, photos: List<Block>, pal: NotePalette, tint: Boolean, onTitle: () -> Unit, onView: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.05f).clickable(onClickLabel = "Edit title", onClick = onTitle), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * .5f
                drawCircle(Brush.radialGradient(listOf(pal.primary.copy(alpha = .95f), pal.primary.copy(alpha = .7f), pal.primary.copy(alpha = 0f)), Offset(size.width / 2, size.height / 2), r), r)
            }
            Text(
                upper(title).joinToString("\n") { it.lowercase().replaceFirstChar { c -> c.uppercase() } },
                style = flexStyle(36.sp, wght = 320, lh = 1.08f), color = pal.bg, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 36.dp),
            )
        }
        if (photos.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            photos.take(2).forEachIndexed { i, p ->
                val shape = if (i == 0) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Arch.toShape()
                Snap(p, pal, tint, 1f, Modifier.weight(1f), shape) { onView(i) }
            }
        }
    }
}

/** Poster: each line of the title huge, tilted this way and that, an orange dot in the gap, a photo with a hard shadow. */
@Composable
fun PosterHeader(title: String, photos: List<Block>, pal: NotePalette, tint: Boolean, onTitle: () -> Unit, onView: (Int) -> Unit, modifier: Modifier = Modifier) {
    val lines = upper(title)
    Column(modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Column(Modifier.clickable(onClickLabel = "Edit title", onClick = onTitle)) {
            lines.forEachIndexed { i, l ->
                Box(Modifier.fillMaxWidth().rotate(listOf(-4f, 2.5f, -2f)[i % 3]).padding(vertical = 2.dp)) {
                    FitLine(l, flexStyle(400.sp, wght = 900, lh = .82f), pal.fg, min = 24f)
                }
                if (i == 0 && lines.size > 1) Box(Modifier.padding(start = 90.dp, top = 6.dp, bottom = 6.dp).size(54.dp).clip(CircleShape).background(pal.primary))
            }
        }
        if (photos.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(top = 18.dp, end = 10.dp), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.fillMaxWidth(.62f).rotate(2f)) {
                Box(Modifier.matchParentSizeShadow().offset(10.dp, 10.dp).background(pal.fg.copy(alpha = .88f)))
                Snap(photos[0], pal, tint, 1.3f, Modifier.fillMaxWidth(), RoundedCornerShape(2.dp)) { onView(0) }
            }
        }
    }
}

private fun Modifier.matchParentSizeShadow(): Modifier = this.fillMaxSize()

/** Bleed: a full-width block of colour, a duotone photo bleeding off the left, condensed type running off the right. */
@Composable
fun BleedHeader(title: String, photos: List<Block>, pal: NotePalette, onTitle: () -> Unit, onView: (Int) -> Unit, modifier: Modifier = Modifier) {
    val panel = pal.primary
    val ink = pal.bg
    Box(
        modifier.layout { m, c ->
            val extra = 32.dp.roundToPx()
            val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
            layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
        }.background(panel).clipToBounds().padding(bottom = 22.dp),
    ) {
        Column {
            if (photos.isNotEmpty()) Box(Modifier.fillMaxWidth(.66f).padding(top = 0.dp)) {
                PhotoImage(photos[0].path!!, 900, true, panel, Modifier.fillMaxWidth().aspectRatio(.95f).clickable(onClickLabel = "View photo") { onView(0) })
            }
            Column(Modifier.padding(top = 18.dp).clickable(onClickLabel = "Edit title", onClick = onTitle).layout { m, c ->
                // wider than the screen: the last letters run off the edge
                val w = (c.maxWidth * 1.12f).toInt()
                val p = m.measure(c.copy(minWidth = w, maxWidth = w))
                layout(c.maxWidth, p.height) { p.place(8.dp.roundToPx(), 0) }
            }) {
                for (l in upper(title)) FitLine(l, flexStyle(400.sp, wght = 800, wdth = 42f, lh = .84f), ink, min = 24f)
            }
        }
    }
}

/** Script: a large handwritten heading. */
@Composable
fun ScriptTitle(title: String, pal: NotePalette, modifier: Modifier = Modifier) {
    Text(title, style = TextStyle(fontFamily = Script, fontSize = 64.sp, lineHeight = 58.sp, color = pal.fg), modifier = modifier.padding(bottom = 6.dp))
}
