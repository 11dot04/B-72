@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.PointerEventPass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun App(share: com.b72.keep_canvas.data.Share? = null, onShareHandled: () -> Unit = {}, start: com.b72.keep_canvas.data.Launch? = null, onLaunchHandled: () -> Unit = {}) {
    val ctx = LocalContext.current
    UiPrefs.init(ctx); Rf.init(ctx); CodeFace.init(ctx); TitleFaces.init(ctx)
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
    // 0 notes, 1 canvas, 2 library, 3 artifacts: the top-level places in the floating bar
    var tab by UiPrefs.tab
    androidx.compose.runtime.LaunchedEffect(tab) { if (tab != 2) LibraryPos.reset() }
    // when the screen was last touched: a page that is still shrinking back into its tile when this changes has been interrupted (a swipe, say)
    var lastTouch by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    // a note just started from the + menu opens on its page with the cursor in it
    var draft by remember { mutableStateOf<String?>(null) }
    fun leave() {
        open?.let { repo.discardIfEmpty(it); if (com.b72.keep_canvas.data.Cloud.readMode(ctx) == 1) repo.readNow(it) }
        draft = null; open = null
    }
    // a note started from a + button is not opened: it is a card holding only what was asked for, and when it is kept it lands in the list as a tile
    var cardFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    fun begin(kind: Kind, tag: String?) {
        if (kind == Kind.Checklist) { val id = repo.newDraft(kind, tag); draft = id; open = id }
        else { val id = repo.newDraft(Kind.Text, tag); draft = id; open = id; addFirst = when (kind) { Kind.Photo -> "photo"; Kind.Quote -> "quote"; Kind.Link -> "link"; Kind.Film -> "film"; Kind.Book -> "book"; Kind.Track -> "track"; Kind.Recipe -> "recipe"; Kind.Audio -> "audio"; Kind.Drawing -> "draw"; else -> null } }
    }
    // something shared from another app opens a new note, ready to save
    LaunchedEffect(share, loaded != null) {
        if (share != null && loaded != null) { scope.launch { val id = repo.addShare(share); open = id; board = null }; onShareHandled() }
    }
    // a launcher shortcut starts a new note of one kind; a reminder notification opens its note
    LaunchedEffect(start) {
        if (start != null) {
            // a reminder for a note in another profile brings that profile up first
            start.openId?.let { id -> repo.awaitLoaded(); repo.profileOf(id)?.takeIf { it != repo.profileId }?.let { repo.switchProfile(it) }; open = id; board = null }
            start.newKind?.let { begin(it, null) }
            start.tab?.let { tab = it; open = null; board = null }
            onLaunchHandled()
        }
    }
    // another profile: whatever was open belongs to the one being left
    fun switchTo(id: String) { scope.launch { leave(); board = null; tab = 0; repo.switchProfile(id) } }
    val grid = rememberLazyStaggeredGridState()
    val snack = remember { androidx.compose.material3.SnackbarHostState() }
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val scaleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    BackHandler(tab != 0 && open == null && board == null) { tab = 0 }
    androidx.compose.runtime.LaunchedEffect(open, board) { if (open == null && board == null) Opening.clear() }
    // predictive back: while the gesture is held the page shrinks and slides with the finger, as the system previews it; letting go closes it,
    // and the container transform takes it from there
    val backScope = androidx.compose.runtime.rememberCoroutineScope()
    val backA = remember { androidx.compose.animation.core.Animatable(0f) }
    var backEdge by remember { mutableStateOf(0) }
    androidx.activity.compose.PredictiveBackHandler(open != null || board != null) { progress ->
        try {
            progress.collect { ev -> backA.snapTo(ev.progress * .5f); backEdge = ev.swipeEdge }
            // on release the preview eases out while the container transform takes over: no snap, so no jolt
            backScope.launch { backA.animateTo(0f, tween(320, easing = androidx.compose.animation.core.CubicBezierEasing(.2f, 0f, 0f, 1f))) }
            if (open != null) leave() else board = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            backScope.launch { backA.animateTo(0f, tween(220)) }
            throw e
        }
    }

    val notesNow by androidx.compose.runtime.rememberUpdatedState(loaded?.library?.notes.orEmpty())
    WarmText(notesNow)
    Redraws("app")
    InsightsOverlay()
    androidx.compose.runtime.LaunchedEffect(Unit) { Opening.warm = { wid -> notesNow.firstOrNull { it.id == wid }?.let { Warm.note(it) } } }
    androidx.compose.runtime.SideEffect { Opening.paging = open != null || board != null }
    Surface(color = MaterialTheme.colorScheme.surface) {
      Box(Modifier.fillMaxSize().pointerInput(Unit) {
          // every new touch is noted and never consumed
          awaitPointerEventScope { while (true) { val e = awaitPointerEvent(PointerEventPass.Initial); if (e.changes.any { it.changedToDown() }) lastTouch = System.nanoTime() } }
      }) {
        SharedTransitionLayout {
            AnimatedContent(
                targetState = open to board,
                // a note grows out of the card that opened it (container transform): the screen it leaves is held in place underneath, and the new one fades in over it
                transitionSpec = { fadeIn(fx) togetherWith androidx.compose.animation.ExitTransition.None },
                label = "screen",
            ) { (id, tag) ->
                val shared = SharedCtx(this@SharedTransitionLayout, this)
                androidx.compose.runtime.CompositionLocalProvider(LocalShared provides shared) {
                // a page leaving with no tile to shrink into (the list was scrolled, or another tab is showing) just fades, quickly, instead of lingering
                val goneKey = id?.let { "card-$it" }
                // touching the screen while the page is still on its way back (to swipe to the next item, say) lets it go at once, instead of riding along with the swipe
                var exitedAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
                val leaving = transition.targetState == androidx.compose.animation.EnterExitState.PostExit
                androidx.compose.runtime.LaunchedEffect(leaving) { if (leaving && exitedAt == 0L) exitedAt = System.nanoTime() }
                val touched = exitedAt != 0L && lastTouch > exitedAt
                val ghost by transition.animateFloat(transitionSpec = { tween(150, delayMillis = 30) }, label = "ghost") { s ->
                    if (s == androidx.compose.animation.EnterExitState.Visible || goneKey == null) 1f else if (touched) 0f else if ((Opening.live[goneKey] ?: 0) > 1) 1f else 0f
                }
                androidx.compose.runtime.CompositionLocalProvider(LocalBackProgress provides { backA.value }) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = ghost }) {
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
                            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) repo.restore(note.id)
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
                    onInsert = { label, lines -> repo.appendSection(note.id, label, lines) },
                    onAddTag = { t -> repo.upsert(note.copy(tags = (note.tags + t).distinct())) },
                    onRemoveTag = { t -> repo.upsert(note.copy(tags = note.tags - t)) },
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
                    onStickers = { board = null; tab = 3 },
                )
                else AnimatedContent(
                    targetState = tab,
                    // Material's fade through: what leaves is gone first, then what arrives fades in and settles up from a touch smaller, so two tabs are never on screen together
                    transitionSpec = {
                        (fadeIn(tween(210, delayMillis = 90, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) +
                            androidx.compose.animation.scaleIn(tween(300, delayMillis = 90, easing = androidx.compose.animation.core.LinearOutSlowInEasing), initialScale = .94f)) togetherWith
                            fadeOut(tween(90, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                    },
                    label = "tabs",
                ) { t -> when (t) {
                    1 -> CanvasHub(loaded, onRename = { a, b -> repo.renameTag(a, b) }) { board = it }
                    2 -> LibraryScreen(loaded, { open = it }, onAdd = { begin(it, null) })
                    4 -> ShotsScreen()
                    3 -> StickersScreen(repo, loaded, onBack = { tab = 0 }, onCanvas = { board = it })
                    else -> androidx.compose.runtime.key(repo.profileId) { HomeScreen(repo, loaded, grid, shared,
                        onSwitchProfile = { switchTo(it) }, onNewProfile = { n, h -> scope.launch { leave(); board = null; tab = 0; repo.addProfile(n, h) } }, onDeleteProfile = { scope.launch { leave(); board = null; repo.removeProfile(it) } }, onOpen = { open = it }, onAdd = { begin(it, null) }, onTag = { board = it }, tab = tab, onTab = { tab = it },
                        onMatch = { title, tag -> val id = repo.newDraft(Kind.Text, tag); repo.setTitle(id, title); draft = id; open = id }) }
                } }
                }
                }
                }
            }
        }
        cardFor?.let { (cid, ckind) -> AddSheet(repo, cid, hasList = false, initial = ckind) { cardFor = null; repo.discardIfEmpty(cid) } }
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
        ) { FloatingNav(tab, { tab = it }, Modifier.navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) }
        androidx.compose.material3.SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
      }
    }
}
