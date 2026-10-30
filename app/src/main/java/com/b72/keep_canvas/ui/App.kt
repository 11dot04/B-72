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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableIntStateOf
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
    // what to offer straight away on a new note (a photo picker, a quote box…)
    var addFirst by remember { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // the tag whose canvas is showing; a note opened from it returns here
    var board by rememberSaveable { mutableStateOf<String?>(null) }
    // 0 notes, 1 canvas, 2 stickers: the three top-level places in the floating bar
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // a note just started from the + menu opens on its page with the cursor in it
    var draft by remember { mutableStateOf<String?>(null) }
    fun leave() { open?.let { repo.discardIfEmpty(it) }; draft = null; open = null }
    fun begin(kind: Kind, tag: String?) {
        if (kind == Kind.Text || kind == Kind.Checklist) { val id = repo.newDraft(kind, tag); draft = id; open = id }
        else { val id = repo.newDraft(Kind.Text, tag); draft = id; open = id; addFirst = when (kind) { Kind.Photo -> "photo"; Kind.Quote -> "quote"; Kind.Link -> "link"; Kind.Film -> "film"; Kind.Book -> "book"; Kind.Track -> "track"; Kind.Recipe -> "recipe"; else -> null } }
    }
    // something shared from another app opens a new note, ready to save
    LaunchedEffect(share, loaded != null) {
        if (share != null && loaded != null) { scope.launch { val id = repo.addShare(share); open = id; board = null }; onShareHandled() }
    }
    // a launcher shortcut starts a new note of one kind; a reminder notification opens its note
    LaunchedEffect(start) {
        if (start != null) {
            start.openId?.let { open = it; board = null }
            start.newKind?.let { begin(it, null) }
            onLaunchHandled()
        }
    }
    val grid = rememberLazyStaggeredGridState()
    val snack = remember { androidx.compose.material3.SnackbarHostState() }
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    BackHandler(tab != 0 && open == null && board == null) { tab = 0 }
    BackHandler((open != null || board != null)) { if (open != null) leave() else board = null }

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
                if (note != null) NotePage(note, shared, onBack = { leave() }, autoFocus = draft == note.id,
                    onStar = { repo.toggleStar(note.id) }, onTitle = { repo.setTitle(note.id, it) },
                    onRich = { b, t, sp, ind -> repo.setRich(note.id, b, t, sp, ind) }, onItems = { b, l -> repo.setItems(note.id, b, l) }, onEdit = {},
                    repo = repo, addFirst = if (addFirst != null && draft == note.id) addFirst else null, onAddFirstDone = { addFirst = null },
                    onLayout = { l -> repo.upsert(note.copy(layout = l)) }, onTag = { open = null; board = it },
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
                    onSmart = { on ->
                        repo.setSmart(note.id, on)
                        if (on && !com.b72.keep_canvas.data.Assistant.installed) android.widget.Toast.makeText(ctx, "Smart layout is on. Install the assistant for deeper extraction.", android.widget.Toast.LENGTH_LONG).show()
                    },
                    onAppend = { repo.appendText(note.id, it) },
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
                    onAdd = { begin(Kind.Text, tag) },
                    onStickers = { board = null; tab = 2 },
                )
                else when (tab) {
                    1 -> CanvasHub(loaded) { board = it }
                    2 -> StickersScreen(repo, loaded) { tab = 0 }
                    else -> HomeScreen(repo, loaded, grid, shared, onOpen = { open = it }, onAdd = { begin(it, null) }, onTag = { board = it },
                        onMatch = { title, tag -> val id = repo.newDraft(Kind.Text, tag); repo.setTitle(id, title); draft = id; open = id })
                }
            }
        }
        // the nav floats over a soft darkening so it reads against anything scrolling under it
        AnimatedVisibility(
            visible = open == null && board == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(fx), exit = fadeOut(fx),
        ) {
            Box(
                Modifier.fillMaxWidth().height(150.dp).background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = .34f))),
                ),
            )
        }
        // the floating bar belongs to the three top-level places only
        AnimatedVisibility(
            visible = open == null && board == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(fx),
            exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeOut(fx),
        ) { FloatingNav(tab, { tab = it }, Modifier.navigationBarsPadding().padding(bottom = 14.dp)) }
        androidx.compose.material3.SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
      }
    }
}
