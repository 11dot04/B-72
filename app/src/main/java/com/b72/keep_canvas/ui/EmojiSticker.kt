package com.b72.keep_canvas.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Noto Emoji, the one-colour variable font, when the build has fetched it (res/font/notoemoji.ttf). */
object EmojiFont {
    private val cache = HashMap<Int, FontFamily>()

    fun family(ctx: Context, weight: Int): FontFamily? {
        cache[weight]?.let { return it }
        val id = ctx.resources.getIdentifier("notoemoji", "font", ctx.packageName)
        if (id == 0) return null
        val fam = runCatching { FontFamily(Font(id, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))) }.getOrNull() ?: return null
        cache[weight] = fam
        return fam
    }
}

/**
 * A sticker: one emoji in the one-colour Noto font, ink on a pastel gradient, with a white die-cut edge and a little shadow, set at a tilt.
 * (The emoji are written without the "emoji presentation" selector, so the font draws them as outlines instead of the coloured system ones.)
 */
@Composable
fun EmojiSticker(glyph: String, modifier: Modifier = Modifier, size: Dp = 84.dp, tilt: Float = -8f, colors: List<Color>) {
    val ctx = LocalContext.current
    val fam = remember { EmojiFont.family(ctx, 600) }
    val shape = RoundedCornerShape(size * .4f)
    Box(
        modifier.graphicsLayer { rotationZ = tilt }.size(size).shadow(6.dp, shape).clip(shape)
            .background(Brush.linearGradient(colors)).border(4.dp, Color.White, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontFamily = fam, fontSize = (size.value * .52f).sp, color = Color(0xFF1B1B1F))
    }
}
