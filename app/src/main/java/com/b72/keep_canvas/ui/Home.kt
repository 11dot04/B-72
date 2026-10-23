package com.b72.keep_canvas.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Loaded
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.Source
import com.b72.keep_canvas.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** Tile heights snap to a few ratios (height / width) so the two columns stay calm. */
private val Ratios = listOf(1.0, 1.25, 1.5)
private fun snap(r: Double) = Ratios.minBy { abs(it - r) }

/** Films and books are portrait posters, tracks are squares, everything else follows its photo. */
private fun tileRatio(n: Note): Double {
    val c = n.cover ?: return 1.0
    return when (c.kind) {
        Kind.Film, Kind.Book -> 1.5
        Kind.Track -> 1.0
        else -> snap(c.aspect.coerceIn(.8, 1.6))
    }
}

private suspend fun decode(path: String, target: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val f = File(path)
        if (!f.exists()) return@runCatching null
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, b)
        var s = 1
        while (b.outWidth / (s * 2) >= target) s *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val state by produceState<Loaded?>(null) { value = Store.load(ctx) }
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { inner ->
        val loaded = state
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                    Text("Collection", style = flexStyle(44.sp, wght = 700, wdth = 112f, rond = 40f), color = MaterialTheme.colorScheme.onSurface)
                    val line = when {
                        loaded == null -> "Loading…"
                        loaded.error != null -> loaded.error
                        loaded.source == Source.ImportedFromFlutter -> "Brought over ${loaded.library.notes.size} notes from the previous version."
                        else -> "${loaded.library.notes.size} notes"
                    }
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (loaded != null) {
                items(loaded.library.notes.asReversed(), key = { it.id }) { n -> Tile(n) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Tile(n: Note) {
    val cs = MaterialTheme.colorScheme
    val tones = remember(cs) {
        listOf(cs.primaryContainer to cs.onPrimaryContainer, cs.secondaryContainer to cs.onSecondaryContainer, cs.tertiaryContainer to cs.onTertiaryContainer)
    }
    val (bg, fg) = tones[(n.id.hashCode() and 0x7fffffff) % tones.size]
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // spatial spring: the tile changes size, so it may overshoot a little
    val scale by animateFloatAsState(if (pressed) .96f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    val ratio = tileRatio(n)
    val img by produceState<ImageBitmap?>(null, n.id) { value = n.cover?.path?.let { decode(it, 600) } }
    Box(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .aspectRatio((1 / ratio).toFloat())
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(interactionSource = src, indication = null) { },
    ) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        val t = n.title
        if (t.isNotEmpty()) {
            val onPhoto = img != null
            Box(
                Modifier
                    .align(if (onPhoto) Alignment.BottomStart else Alignment.TopStart)
                    .fillMaxWidth()
                    .then(if (onPhoto) Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))) else Modifier)
                    .padding(14.dp),
            ) {
                Text(
                    t,
                    style = flexStyle(if (onPhoto) 20.sp else 26.sp, wght = 650, wdth = if (onPhoto) 100f else 110f, rond = 30f),
                    color = if (onPhoto) Color.White else fg,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
