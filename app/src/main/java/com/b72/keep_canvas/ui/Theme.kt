package com.b72.keep_canvas.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF4A5A2E), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE5A5), onPrimaryContainer = Color(0xFF111F00),
    secondaryContainer = Color(0xFFE6DDC2), onSecondaryContainer = Color(0xFF211B04),
    tertiaryContainer = Color(0xFFF7CDBE), onTertiaryContainer = Color(0xFF3A0B00),
    surface = Color(0xFFFBF8EF), onSurface = Color(0xFF1C1B14),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB2CA8B), onPrimary = Color(0xFF1F3100),
    primaryContainer = Color(0xFF334516), onPrimaryContainer = Color(0xFFCDE5A5),
    secondaryContainer = Color(0xFF4A442C), onSecondaryContainer = Color(0xFFE6DDC2),
    tertiaryContainer = Color(0xFF5B3023), onTertiaryContainer = Color(0xFFF7CDBE),
    surface = Color(0xFF14130C), onSurface = Color(0xFFE6E2D3),
)

/** Spring motion everywhere via MotionScheme.expressive(); shapes lean large and soft. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun B72Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(40.dp),
        ),
        typography = b72Typography(),
        content = content,
    )
}
