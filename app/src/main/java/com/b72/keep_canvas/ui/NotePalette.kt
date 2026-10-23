package com.b72.keep_canvas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every note page is tinted by its own cover photo: the page, its text and the accent shape. */
class NotePalette(val bg: Color, val fg: Color, val primary: Color)

private fun tone(argb: Int, l: Float, sMax: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    hsl[1] = minOf(hsl[1], sMax)
    hsl[2] = l
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
fun rememberNotePalette(note: Note): NotePalette {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val seed by produceState<Int?>(null, note.id) {
        val p = note.cover?.path ?: return@produceState
        val bmp = Images.load(p, 160) ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val pal = Palette.from(bmp.asAndroidBitmap()).generate()
            (pal.vibrantSwatch ?: pal.dominantSwatch)?.rgb
        }
    }
    val s = seed ?: return NotePalette(cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
    return if (dark) NotePalette(tone(s, .16f, .5f), tone(s, .90f, .3f), tone(s, .72f, .7f))
    else NotePalette(tone(s, .86f, .6f), tone(s, .12f, .4f), tone(s, .36f, .7f))
}
