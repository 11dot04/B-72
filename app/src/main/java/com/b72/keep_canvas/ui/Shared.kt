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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
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
 * Container transform: the card you tap and the screen it opens are one container. Its bounds travel on the expressive spatial spring,
 * the card's contents fade out as the screen's fade in, and nothing else needs to be shared. Content is scaled between the two sizes,
 * not re-laid-out every frame, so a long page costs no more to open than a short one. Put this before the clip and background, so
 * both are part of what travels. [radius] is the card's corner while the container is in flight.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.container(ctx: SharedCtx, key: String, radius: Dp = 28.dp): Modifier {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val bounds = BoundsTransform { _, _ -> spec }
    return with(ctx.st) {
        sharedBounds(
            rememberSharedContentState(key), ctx.av,
            enter = fadeIn(tween(160, delayMillis = 70)), exit = fadeOut(tween(90)),
            boundsTransform = bounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(radius)),
        )
    }
}

/** The transition scope of the screen showing right now, so any card that opens a note can be a container without it being passed down. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalShared = compositionLocalOf<SharedCtx?> { null }

/** A card that opens the note [id]: the same container transform as a home tile, so it grows into the page wherever the card is. */
@Composable
fun Modifier.noteContainer(id: String, radius: Dp = 28.dp): Modifier {
    val c = LocalShared.current ?: return this
    return container(c, "card-$id", radius)
}

/** Any container that is not a note: a canvas, say. Same transform, keyed by [key]. */
@Composable
fun Modifier.keyContainer(key: String, radius: Dp = 28.dp): Modifier {
    val c = LocalShared.current ?: return this
    return container(c, key, radius)
}
