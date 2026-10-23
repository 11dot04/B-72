package com.b72.keep_canvas.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
fun Modifier.shared(ctx: SharedCtx, key: String, text: Boolean = false): Modifier {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val bounds = BoundsTransform { _, _ -> spec }
    return with(ctx.st) {
        if (text) {
            sharedBounds(
                rememberSharedContentState(key), ctx.av,
                enter = fadeIn(), exit = fadeOut(),
                boundsTransform = bounds,
            )
        } else {
            sharedElement(rememberSharedContentState(key), ctx.av, boundsTransform = bounds)
        }
    }
}
