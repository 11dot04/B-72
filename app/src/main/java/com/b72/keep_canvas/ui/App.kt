@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.b72.keep_canvas.data.Loaded
import com.b72.keep_canvas.data.Store

@Composable
fun App() {
    val ctx = LocalContext.current
    val loaded by produceState<Loaded?>(null) { value = Store.load(ctx) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val grid = rememberLazyStaggeredGridState()
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    BackHandler(open != null) { open = null }

    Surface(color = MaterialTheme.colorScheme.surface) {
        SharedTransitionLayout {
            AnimatedContent(
                targetState = open,
                transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) },
                label = "screen",
            ) { id ->
                val shared = SharedCtx(this@SharedTransitionLayout, this)
                val note = id?.let { wanted -> loaded?.library?.notes?.firstOrNull { it.id == wanted } }
                if (note == null) HomeScreen(loaded, grid, shared) { open = it }
                else NotePage(note, shared) { open = null }
            }
        }
    }
}
