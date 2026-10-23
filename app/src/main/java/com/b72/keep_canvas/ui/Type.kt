package com.b72.keep_canvas.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.R

/**
 * Google Sans Flex is one variable file; each call picks a point on its axes.
 * wght 1..1000, wdth 25..151, ROND 0..100, opsz 6..144, slnt -10..0.
 */
fun flex(wght: Int = 400, wdth: Float = 100f, rond: Float = 0f, opsz: Float? = null, slnt: Float = 0f): FontFamily {
    val s = buildList {
        add(FontVariation.weight(wght))
        add(FontVariation.width(wdth))
        add(FontVariation.Setting("ROND", rond))
        add(FontVariation.slant(slnt))
        if (opsz != null) add(FontVariation.Setting("opsz", opsz))
    }
    return FontFamily(Font(R.font.googlesansflex, FontWeight(wght), variationSettings = FontVariation.Settings(*s.toTypedArray())))
}

val Serif = FontFamily(
    Font(R.font.instrumentserif_regular, FontWeight.Normal),
    Font(R.font.instrumentserif_italic, FontWeight.Normal, FontStyle.Italic),
)

fun flexStyle(size: TextUnit, wght: Int = 600, wdth: Float = 100f, rond: Float = 0f, lh: Float = 1.05f) =
    TextStyle(fontFamily = flex(wght, wdth, rond), fontSize = size, lineHeight = size * lh, letterSpacing = (-0.01).sp)

/** Google Sans Flex plays the brand role (display, headline, title); body and labels stay plain. */
fun b72Typography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = flex(700, 112f, 40f)),
        displayMedium = base.displayMedium.copy(fontFamily = flex(700, 108f, 40f)),
        displaySmall = base.displaySmall.copy(fontFamily = flex(650, 104f, 30f)),
        headlineLarge = base.headlineLarge.copy(fontFamily = flex(650, 100f, 30f)),
        headlineMedium = base.headlineMedium.copy(fontFamily = flex(600, 100f, 30f)),
        headlineSmall = base.headlineSmall.copy(fontFamily = flex(600, 100f, 20f)),
        titleLarge = base.titleLarge.copy(fontFamily = flex(600, 100f, 20f)),
        titleMedium = base.titleMedium.copy(fontFamily = flex(600, 100f, 10f)),
        titleSmall = base.titleSmall.copy(fontFamily = flex(600, 100f, 10f)),
        bodyLarge = base.bodyLarge.copy(fontFamily = flex(400, 100f, 0f)),
        bodyMedium = base.bodyMedium.copy(fontFamily = flex(400, 100f, 0f)),
        bodySmall = base.bodySmall.copy(fontFamily = flex(400, 100f, 0f)),
        labelLarge = base.labelLarge.copy(fontFamily = flex(550, 100f, 0f)),
        labelMedium = base.labelMedium.copy(fontFamily = flex(550, 100f, 0f)),
        labelSmall = base.labelSmall.copy(fontFamily = flex(550, 100f, 0f)),
    )
}
