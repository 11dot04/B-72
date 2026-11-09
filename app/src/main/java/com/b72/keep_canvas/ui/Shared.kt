package com.b72.keep_canvas.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect

/** The two scopes a shared element needs: the transition layout and the screen currently visible in it. */
@OptIn(ExperimentalSharedTransitionApi::class)
class SharedCtx(val st: SharedTransitionScope, val av: AnimatedVisibilityScope)

/**
 * Marks something that flies between the grid and the note page. Bounds follow the expressive
 * spatial spring, so they may overshoot slightly instead of easing.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.shared(ctx: SharedCtx, key: String, text: Boolean = false, remeasure: Boolean = false): Modifier {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val bounds = BoundsTransform { _, _ -> spec }
    return with(ctx.st) {
        if (text && !remeasure) {
            sharedBounds(
                rememberSharedContentState(key), ctx.av,
                enter = fadeIn(), exit = fadeOut(),
                boundsTransform = bounds,
            )
        } else {
            // the picture is re-laid-out at every frame's bounds, so it stays cropped to the frame all the way (no flash of the uncropped image)
            sharedBounds(
                rememberSharedContentState(key), ctx.av,
                enter = fadeIn(), exit = fadeOut(),
                boundsTransform = bounds,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        }
    }
}

/**
 * Container transform, after the Material motion spec: the card you tap and the screen it opens are one container whose bounds travel on a
 * spatial spring (the slow one when it fills the screen, the default one when it only grows into a card). The incoming content fades in
 * over the outgoing within the first quarter, and the outgoing stays solid underneath until the end, so the container never goes
 * see-through. On the way back the outgoing page holds until the last third, then fades. [full] containers also lose their rounded
 * corners as they approach the size of the screen, so there is no jump when the transition ends. Put this before the clip and
 * background so both are part of what travels.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.container(ctx: SharedCtx, key: String, radius: Dp = 28.dp, full: Boolean = true, reveal: Boolean = false, revealShift: Dp = 0.dp, revealMin: Dp = 56.dp): Modifier {
    // the spec's emphasized-decelerate curve: it leaves fast and takes its time to settle, which is what reads as smooth rather than abrupt
    val spec = tween<Rect>(if (full) 500 else 380, easing = androidx.compose.animation.core.CubicBezierEasing(.05f, .7f, .1f, 1f))
    val bounds = BoundsTransform { _, _ -> spec }
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val screen = cfg.screenWidthDp.toFloat() * cfg.screenHeightDp
    val clip = remember(radius, full, screen) {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                val r = if (!full) radius.value else {
                    val area = (size.width / density.density) * (size.height / density.density) / screen
                    radius.value * (1f - ((area - .12f) / .88f).coerceIn(0f, 1f))
                }
                val px = r * density.density
                return androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(px)))
            }
        }
    }
    // a destination whose first row sits [revealShift] below its own top (under the status bar, say) keeps that row on the container's top edge while it is small
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shiftPx = with(density) { revealShift.toPx() }
    val minPx = with(density) { revealMin.toPx() }
    val align: androidx.compose.ui.Alignment = if (revealShift == 0.dp) androidx.compose.ui.Alignment.TopCenter else remember(shiftPx, minPx) {
        object : androidx.compose.ui.Alignment {
            override fun align(size: androidx.compose.ui.unit.IntSize, space: androidx.compose.ui.unit.IntSize, layoutDirection: androidx.compose.ui.unit.LayoutDirection): androidx.compose.ui.unit.IntOffset {
                val t = if (size.height > minPx) ((space.height - minPx) / (size.height - minPx)).coerceIn(0f, 1f) else 1f
                return androidx.compose.ui.unit.IntOffset((space.width - size.width) / 2, (-shiftPx * (1f - t)).toInt())
            }
        }
    }
    return with(ctx.st) {
        sharedBounds(
            rememberSharedContentState(key), ctx.av,
            // fade through, as in the spec: what leaves is gone in 90 ms (so no scaled-up ghost of it rides the growing shape), then what arrives fades in
            enter = fadeIn(tween(210, delayMillis = 90)), exit = fadeOut(tween(90)),
            boundsTransform = bounds,
            // a destination is revealed, not shrunk: it is laid out at its real size and the growing container uncovers it from the top, as in the spec's videos
            resizeMode = if (reveal) SharedTransitionScope.ResizeMode.scaleToBounds(androidx.compose.ui.layout.ContentScale.None, align) else SharedTransitionScope.ResizeMode.scaleToBounds(),
            clipInOverlayDuringTransition = OverlayClip(clip),
        )
    }
}

/** The transition scope of the screen showing right now, so any card that opens a note can be a container without it being passed down. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalShared = compositionLocalOf<SharedCtx?> { null }

/** A card that opens the note [id]: the same container transform as a home tile, so it grows into the page wherever the card is. */
@Composable
fun Modifier.noteContainer(id: String, radius: Dp = 28.dp, reveal: Boolean = false): Modifier {
    val c = LocalShared.current ?: return this
    return container(c, "card-$id", radius, reveal = reveal)
}

/** Any container that is not a note: a canvas, say. Same transform, keyed by [key]. */
@Composable
fun Modifier.keyContainer(key: String, radius: Dp = 28.dp, reveal: Boolean = false): Modifier {
    val c = LocalShared.current ?: return this
    return container(c, key, radius, reveal = reveal)
}


/**
 * Neighbours react while a note opens: the tapped tile grows into the page, and every other tile drifts away from it and fades,
 * the way the Material reference shows. [press] is called just before the note opens; [clear] when the grid is back.
 */
object Opening {
    var id by androidx.compose.runtime.mutableStateOf<String?>(null)
    private val centres = HashMap<String, androidx.compose.ui.geometry.Offset>()
    fun press(tapped: String) { id = tapped }
    fun clear() { id = null }
    fun centre(k: String) = centres[k]
    fun put(k: String, c: androidx.compose.ui.geometry.Offset) { centres[k] = c }
    fun drop(k: String) { centres.remove(k) }
}

/** Registers this tile's centre and, while another tile is opening, slides away from it and fades. */
@Composable
fun Modifier.opener(key: String): Modifier {
    val opening = Opening.id
    val away = opening != null && opening != key
    val t by androidx.compose.animation.core.animateFloatAsState(
        if (away) 1f else 0f, tween(if (away) 380 else 260, easing = androidx.compose.animation.core.CubicBezierEasing(.05f, .7f, .1f, 1f)), label = "opener",
    )
    androidx.compose.runtime.DisposableEffect(key) { onDispose { Opening.drop(key) } }
    return this
        .onGloballyPositioned { Opening.put(key, it.boundsInRoot().center) }
        .graphicsLayer {
            val from = opening?.let { Opening.centre(it) }
            val me = Opening.centre(key)
            if (from != null && me != null && t > 0f) {
                val d = me - from
                val len = kotlin.math.sqrt(d.x * d.x + d.y * d.y).coerceAtLeast(1f)
                translationX = d.x / len * 56.dp.toPx() * t
                translationY = d.y / len * 56.dp.toPx() * t
            }
            alpha = 1f - .85f * t
        }
}
