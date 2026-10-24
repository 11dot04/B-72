@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Repo
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { Repo(ctx.applicationContext, scope) }
    LaunchedEffect(Unit) { repo.init() }
    val loaded = repo.loaded
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // the tag whose canvas is showing; a note opened from it returns here
    var board by rememberSaveable { mutableStateOf<String?>(null) }
    var editTag by remember { mutableStateOf<String?>(null) }
    // null = no editor; (null, kind) = a new note; (id, null) = editing that note
    var editing by remember { mutableStateOf<Pair<String?, Kind?>?>(null) }
    val grid = rememberLazyStaggeredGridState()
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    BackHandler((open != null || board != null) && editing == null) { if (open != null) open = null else board = null }

    Surface(color = MaterialTheme.colorScheme.surface) {
      Box(Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            AnimatedContent(
                targetState = open to board,
                transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) },
                label = "screen",
            ) { (id, tag) ->
                val shared = SharedCtx(this@SharedTransitionLayout, this)
                val note = id?.let { wanted -> loaded?.library?.notes?.firstOrNull { it.id == wanted } }
                if (note != null) NotePage(note, shared, onBack = { open = null }, onEdit = { editing = note.id to null }, onTag = { open = null; board = it })
                else if (tag != null) BoardScreen(
                    repo, loaded, tag,
                    onBack = { board = null }, onOpen = { open = it },
                    onAdd = { editTag = tag; editing = null to null },
                )
                else HomeScreen(loaded, grid, shared, onOpen = { open = it }, onAdd = { editTag = null; editing = null to it }, onTag = { board = it })
            }
        }
        AnimatedVisibility(
            visible = editing != null,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 3 } + fadeIn(fx),
            exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 3 } + fadeOut(fx),
        ) {
            // keep the last target while the exit animation plays
            val hold = remember { object { var v: Pair<String?, Kind?>? = null } }
            if (editing != null) hold.v = editing
            val target = hold.v
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                target?.let { (id, kind) ->
                    Editor(repo, id, kind, tag = editTag) { deleted ->
                        if (deleted) open = null
                        editing = null
                    }
                }
            }
        }
      }
    }
}
