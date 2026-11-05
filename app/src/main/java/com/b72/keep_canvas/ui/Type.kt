package com.b72.keep_canvas.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.R

private val families = HashMap<String, FontFamily>()

/**
 * Google Sans Flex is one variable file; each call picks a point on its axes and is cached,
 * because building a typeface per frame is what made the old animation stutter.
 * wght 1..1000, wdth 25..151, ROND 0..100, opsz 6..144, GRAD, slnt -10..0.
 */
fun flex(wght: Int = 400, wdth: Float = 100f, rond: Float = 0f, slnt: Float = 0f, grade: Float = 0f, opsz: Float? = null): FontFamily {
    val key = "$wght|${wdth.toInt()}|${rond.toInt()}|${slnt.toInt()}|${grade.toInt()}|${opsz?.toInt()}"
    return families.getOrPut(key) {
        val s = buildList {
            add(FontVariation.weight(wght))
            add(FontVariation.width(wdth.coerceIn(25f, 151f)))
            add(FontVariation.Setting("ROND", rond))
            add(FontVariation.Setting("GRAD", grade))
            add(FontVariation.slant(slnt))
            if (opsz != null) add(FontVariation.Setting("opsz", opsz.coerceIn(6f, 144f)))
        }
        FontFamily(Font(R.font.googlesansflex, FontWeight(wght), variationSettings = FontVariation.Settings(*s.toTypedArray())))
    }
}

/**
 * Roboto Flex, fetched by CI when it is reachable (the app falls back to Google Sans Flex without it). Thirteen axes; the ones used are
 * wght, wdth, opsz and GRAD for the usual things, plus XTRA (counter width, so a line can be fitted without thickening strokes) and
 * YTDE / YTAS (descender depth / ascender height, so a lowercase title can be tightened or given room without changing its size).
 */
object Rf {
    private var id = 0
    private val cache = HashMap<String, FontFamily>()
    fun init(ctx: android.content.Context) { if (id == 0) id = ctx.resources.getIdentifier("robotoflex", "font", ctx.packageName) }
    val ready get() = id != 0

    fun family(wght: Int = 400, wdth: Float = 100f, opsz: Float = 14f, grad: Float = 0f, xtra: Float? = null, ytde: Float? = null, ytas: Float? = null): FontFamily {
        if (id == 0) return flex(wght, wdth)
        val key = "$wght|${wdth.toInt()}|${opsz.toInt()}|${grad.toInt()}|${xtra?.toInt()}|${ytde?.toInt()}|${ytas?.toInt()}"
        return cache.getOrPut(key) {
            val s = buildList {
                add(FontVariation.weight(wght)); add(FontVariation.width(wdth.coerceIn(25f, 151f)))
                add(FontVariation.Setting("opsz", opsz.coerceIn(8f, 144f))); add(FontVariation.Setting("GRAD", grad.coerceIn(-200f, 150f)))
                xtra?.let { add(FontVariation.Setting("XTRA", it.coerceIn(323f, 603f))) }
                ytde?.let { add(FontVariation.Setting("YTDE", it.coerceIn(-305f, -98f))) }
                ytas?.let { add(FontVariation.Setting("YTAS", it.coerceIn(649f, 854f))) }
            }
            FontFamily(Font(id, FontWeight(wght), variationSettings = FontVariation.Settings(*s.toTypedArray())))
        }
    }
}

/** Like [flexStyle], but in Roboto Flex when it is there. Display sizes get the optical size to match. */
fun rflexStyle(size: TextUnit, wght: Int = 600, wdth: Float = 100f, lh: Float = 1.05f, grad: Float = 0f, ytde: Float? = null) =
    TextStyle(fontFamily = Rf.family(wght, wdth, size.value.coerceIn(8f, 144f), grad, ytde = ytde), fontSize = size, lineHeight = size * lh, letterSpacing = (-0.01).sp)

/** The script face: Playfair Display Italic at Bold, shipped with the app (the phone's own 'cursive' font is missing on many devices). */
val Script = FontFamily(Font(R.font.playfair_italic, FontWeight.Bold, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(700))))

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
        displayLarge = base.displayLarge.copy(fontFamily = flex(700, 112f, 40f), lineBreak = LineBreak.Heading),
        displayMedium = base.displayMedium.copy(fontFamily = flex(700, 108f, 40f), lineBreak = LineBreak.Heading),
        displaySmall = base.displaySmall.copy(fontFamily = flex(650, 104f, 30f), lineBreak = LineBreak.Heading),
        headlineLarge = base.headlineLarge.copy(fontFamily = flex(650, 100f, 30f), lineBreak = LineBreak.Heading),
        headlineMedium = base.headlineMedium.copy(fontFamily = flex(600, 100f, 30f), lineBreak = LineBreak.Heading),
        headlineSmall = base.headlineSmall.copy(fontFamily = flex(600, 100f, 20f), lineBreak = LineBreak.Heading),
        titleLarge = base.titleLarge.copy(fontFamily = flex(600, 100f, 20f), lineBreak = LineBreak.Heading),
        titleMedium = base.titleMedium.copy(fontFamily = flex(600, 100f, 10f), lineBreak = LineBreak.Heading),
        titleSmall = base.titleSmall.copy(fontFamily = flex(600, 100f, 10f), lineBreak = LineBreak.Heading),
        bodyLarge = base.bodyLarge.copy(fontFamily = flex(400, 100f, 0f), lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto),
        bodyMedium = base.bodyMedium.copy(fontFamily = flex(400, 100f, 0f), lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto),
        bodySmall = base.bodySmall.copy(fontFamily = flex(400, 100f, 0f), lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto),
        labelLarge = base.labelLarge.copy(fontFamily = flex(550, 100f, 0f)),
        labelMedium = base.labelMedium.copy(fontFamily = flex(550, 100f, 0f)),
        labelSmall = base.labelSmall.copy(fontFamily = flex(550, 100f, 0f)),
    )
}

/** The one heading every top-level tab uses: Material 3 display type with the count as a small label beside it. */
@androidx.compose.runtime.Composable
fun TabTitle(text: String, count: Int? = null, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, color: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface) {
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        androidx.compose.material3.Text(text, style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = color)
        if (count != null) androidx.compose.material3.Text(
            "$count", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = color.copy(alpha = .55f),
            modifier = androidx.compose.ui.Modifier.padding(start = 10.dp, bottom = 6.dp),
        )
    }
}
