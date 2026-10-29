@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
fun App(share: com.b72.keep_canvas.data.Share? = null, onShareHandled: () -> Unit = {}, start: com.b72.keep_canvas.data.Launch? = null, onLaunchHandled: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { Repo(ctx.applicationContext, scope) }
    LaunchedEffect(Unit) { com.b72.keep_canvas.data.Assistant.refresh(ctx.applicationContext); repo.init() }
    val loaded = repo.loaded
    // photos that have not been read yet are read once, in the background, so search can find their text
    val unread = loaded?.library?.notes?.sumOf { n -> n.blocks.count { com.b72.keep_canvas.data.needsWork(it) } } ?: 0
    LaunchedEffect(unread) { if (unread > 0) repo.prepare() }
    // null = no editor; (null, kind) = a new note; (id, null) = editing that note
    var editing by remember { mutableStateOf<Pair<String?, Kind?>?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // the tag whose canvas is showing; a note opened from it returns here
    var board by rememberSaveable { mutableStateOf<String?>(null) }
    var stickers by rememberSaveable { mutableStateOf(false) }
    var editTag by remember { mutableStateOf<String?>(null) }
    var shareIn by remember { mutableStateOf<com.b72.keep_canvas.data.Share?>(null) }
    // something shared from another app opens a new note, ready to save
    LaunchedEffect(share) {
        if (share != null) { shareIn = share; editTag = null; editing = null to null; onShareHandled() }
    }
    // a launcher shortcut starts a new note of one kind; a reminder notification opens its note
    LaunchedEffect(start) {
        if (start != null) {
            start.openId?.let { open = it; board = null }
            start.newKind?.let { editTag = null; editing = null to it }
            onLaunchHandled()
        }
    }
    val grid = rememberLazyStaggeredGridState()
    val snack = remember { androidx.compose.material3.SnackbarHostState() }
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    BackHandler(stickers) { stickers = false }
    BackHandler((open != null || board != null) && editing == null && !stickers) { if (open != null) open = null else board = null }

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
                val relatedOf by androidx.compose.runtime.produceState(emptyList<com.b72.keep_canvas.data.Note>(), note?.id, loaded?.library?.notes?.size) {
                    value = note?.let { repo.related(it.id) }.orEmpty()
                }
                if (note != null) NotePage(note, shared, onBack = { open = null }, onEdit = { editing = note.id to null }, onTag = { open = null; board = it },
                    onDelete = {
                        repo.delete(note.id)
                        open = null
                        scope.launch {
                            val r = snack.showSnackbar("Note deleted", actionLabel = "Undo", withDismissAction = true, duration = androidx.compose.material3.SnackbarDuration.Long)
                            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) repo.upsert(note)
                        }
                    },
                    onToggle = { b, i -> repo.toggleItem(note.id, b, i) },
                    onSetData = { b, k, v -> repo.setData(note.id, b, k, kotlinx.serialization.json.JsonPrimitive(v)) },
                    related = relatedOf,
                    allNotes = loaded?.library?.notes.orEmpty(),
                    onAddTag = { t -> repo.upsert(note.copy(tags = (note.tags + t).distinct())) },
                    onOpenNote = { open = it },
                    onRemind = { at ->
                        repo.upsert(note.copy(remind = at))
                        if (at > 0) com.b72.keep_canvas.data.Reminders.schedule(ctx.applicationContext, note.id, note.title, at)
                        else com.b72.keep_canvas.data.Reminders.cancel(ctx.applicationContext, note.id)
                    },
                )
                else if (tag != null) BoardScreen(
                    repo, loaded, tag,
                    onBack = { board = null }, onOpen = { open = it },
                    onAdd = { editTag = tag; editing = null to null },
                    onStickers = { stickers = true },
                )
                else HomeScreen(repo, loaded, grid, shared, onOpen = { open = it }, onAdd = { editTag = null; editing = null to it }, onTag = { board = it }, onStickers = { stickers = true })
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
                    Editor(repo, id, kind, tag = editTag, share = shareIn) { deleted ->
                        shareIn = null
                        if (deleted) open = null
                        editing = null
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = stickers,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 4 } + fadeIn(fx),
            exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 4 } + fadeOut(fx),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { StickersScreen(repo, loaded) { stickers = false } }
        }
        androidx.compose.material3.SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
      }
    }
}
