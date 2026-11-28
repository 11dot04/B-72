@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
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
 * A sticker: one emoji in the one-colour Noto font, printed in ink on an iridescent sweep of the given colours, with a glossy sheen, a thick white die-cut edge
 * and a soft shadow, set at a tilt. The ink is printed twice, a hair out of register, like a cheap sticker sheet. [shape] is any M3 Expressive polygon (a cookie, a flower...);
 * a rounded square when none is given. (The emoji are written without the "emoji presentation" selector, so the font draws outlines instead of the coloured system ones.)
 */
@Composable
fun EmojiSticker(glyph: String, modifier: Modifier = Modifier, size: Dp = 96.dp, tilt: Float = -8f, colors: List<Color>, shape: androidx.graphics.shapes.RoundedPolygon? = null) {
    val ctx = LocalContext.current
    val fam = remember { EmojiFont.family(ctx, 700) }
    val sh: androidx.compose.ui.graphics.Shape = shape?.toShape() ?: RoundedCornerShape(size * .4f)
    val ink = Color(0xFF1B1B1F)
    val cs = if (colors.size >= 2) colors else listOf(Color(0xFFE8DEF8), Color(0xFFFFD8E4))
    // built from plain clipped layers (a dark one offset for the shadow, a white one for the die-cut edge, the colours a little smaller on top):
    // no shadow elevation and no stroked outline, which are the expensive parts of drawing an odd shape every frame of a drag
    Box(modifier.graphicsLayer { rotationZ = tilt }.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = 4.dp.toPx(); translationX = 1.dp.toPx() }.clip(sh).background(Color.Black.copy(alpha = .22f)))
        Box(Modifier.fillMaxSize().clip(sh).background(Color.White))
        Box(
            Modifier.fillMaxSize().graphicsLayer { scaleX = .88f; scaleY = .88f }.clip(sh)
                .background(Brush.sweepGradient(cs + cs.first()))
                .background(Brush.linearGradient(0f to Color.White.copy(alpha = .55f), .45f to Color.White.copy(alpha = .0f), .55f to Color.White.copy(alpha = .22f), 1f to Color.Transparent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, fontFamily = fam, fontSize = (size.value * .52f).sp, color = ink.copy(alpha = .28f), modifier = Modifier.graphicsLayer { translationX = 3.dp.toPx(); translationY = 3.dp.toPx() })
            Text(glyph, fontFamily = fam, fontSize = (size.value * .52f).sp, color = ink)
        }
    }
}
