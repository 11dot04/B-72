@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Cand
import com.b72.keep_canvas.data.RECORD_KINDS
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.data.newBlock
import kotlinx.coroutines.launch

/** A quote, figure, info list or link being edited as a small card. */
private class Extra(val kind: Kind, val src: Block) {
    var body by mutableStateOf(src.body)
    var title by mutableStateOf(src.title)
}

private val EXTRA_KINDS = setOf(Kind.Quote, Kind.Stat, Kind.Info)

private fun label(k: Kind) = when (k) { Kind.Quote -> "Quote"; Kind.Stat -> "Figure"; Kind.Info -> "Info"; else -> k.name }
private fun hints(k: Kind) = when (k) {
    Kind.Quote -> "Who said it" to "Quote"
    Kind.Stat -> "What it measures" to "Figure, e.g. 319 ms"
    Kind.Info -> "Heading" to "One per line, like Where: Echo Bridge"
    else -> "Title" to "https://..."
}

private class EditState(n: Note?, initial: Kind?, tag: String?) {
    private var c = 0
    fun fresh() = "${System.currentTimeMillis() * 1000}_${c++}"

    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var list by mutableStateOf("")
    var tags by mutableStateOf(n?.tags?.joinToString(", ") ?: (tag ?: ""))
    var smart by mutableStateOf(false)
    val photos = mutableStateListOf<Block>()
    val extras = mutableStateListOf<Extra>()
    val recs = mutableStateListOf<Rec>()
    /** Blocks this form doesn't edit (extra text blocks). They are kept as they are. */
    val kept = mutableListOf<Block>()
    var bareB: Block? = null
    var textB: Block? = null
    var listB: Block? = null

    init {
        for (b in n?.blocks ?: emptyList()) {
            when {
                b.kind == Kind.Photo -> photos.add(b)
                b.kind == Kind.Bare && bareB == null -> { bareB = b; title = b.title }
                b.kind == Kind.Checklist && listB == null -> { listB = b; list = b.checkItems.joinToString("\n") }
                b.kind in EXTRA_KINDS -> extras.add(Extra(b.kind, b))
                b.kind in RECORD_KINDS -> recs.add(Rec(b.kind, b))
                (b.kind == Kind.Text || b.kind == Kind.Poster) && textB == null -> {
                    textB = b; smart = b.kind == Kind.Poster; body = b.body
                    if (title.isEmpty()) title = b.title
                }
                else -> kept.add(b)
            }
        }
        if (n == null && initial != null && initial in EXTRA_KINDS) extras.add(Extra(initial, newBlock(initial, fresh())))
        if (n == null && initial != null && initial in RECORD_KINDS) recs.add(Rec(initial, newBlock(initial, fresh())))
    }

    fun filled(e: Extra) = e.title.isNotBlank() || e.body.isNotBlank()
    fun sig() = listOf(title, body, list, tags, smart, photos.map { it.id }, extras.map { it.title + "|" + it.body }, recs.map { listOf(it.title, it.body, it.path, it.rating, it.f.toSortedMap().toString()) }).toString()
    val empty get() = body.isBlank() && list.isBlank() && photos.isEmpty() && extras.none { filled(it) } && recs.none { it.filled } && kept.isEmpty()

    fun build(old: Note?): Note {
        val t = title.trim(); val tx = body.trim(); val li = list.trim()
        val blocks = buildList {
            if (t.isNotEmpty()) add((bareB ?: newBlock(Kind.Bare, fresh())).copy(k = Kind.Bare.ordinal, title = t, body = ""))
            if (tx.isNotEmpty()) add((textB ?: newBlock(Kind.Text, fresh())).copy(k = (if (smart) Kind.Poster else Kind.Text).ordinal, title = "", body = tx))
            if (li.isNotEmpty()) {
                val base = listB ?: newBlock(Kind.Checklist, fresh())
                add(base.copy(k = Kind.Checklist.ordinal, title = base.title.ifEmpty { "List" }, body = li, items = li.lines().filter { it.isNotBlank() }))
            }
            for (e in extras) if (filled(e)) add(e.src.copy(k = e.kind.ordinal, title = e.title.trim(), body = e.body.trim()))
            for (r in recs) if (r.filled) add(r.block())
            addAll(kept)
            addAll(photos)
        }
        val tagSet = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return (old ?: Note(id = (System.currentTimeMillis() * 1000).toString())).copy(blocks = blocks, tags = tagSet)
    }
}

@Composable
fun Editor(repo: Repo, noteId: String?, initial: Kind?, tag: String? = null, share: com.b72.keep_canvas.data.Share? = null, onDone: (deleted: Boolean) -> Unit) {
    val old = remember { noteId?.let { repo.note(it) } }
    val st = remember { EditState(old, initial, tag) }
    val startSig = remember { st.sig() }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var sheet by remember { mutableStateOf<Pair<Rec, List<Cand>>?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        scope.launch {
            busy = true
            for (u in uris) repo.importPhoto(u, st.fresh())?.let { st.photos.add(it) }
            busy = false
        }
    }
    fun addPhotos() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    LaunchedEffect(Unit) { if (old == null && initial == Kind.Photo && share == null) addPhotos() }
    LaunchedEffect(share?.token) {
        if (share != null && old == null) {
            if (share.title.isNotEmpty()) st.title = share.title
            if (share.text.isNotEmpty()) st.body = share.text
            busy = true
            for (u in share.uris) repo.importPhoto(u, st.fresh())?.let { st.photos.add(it) }
            busy = false
        }
    }

    fun close() { if (st.sig() != startSig) askDiscard = true else onDone(false) }
    BackHandler { close() }

    fun save() {
        if (st.empty) { scope.launch { snack.showSnackbar("Add some text or media. A title on its own is not enough.") }; return }
        scope.launch {
            // links typed into the text become link cards with a preview
            busy = true
            for (m in Regex("https?://[^\\s]+").findAll(st.body).take(2)) {
                val u = m.value
                if (st.recs.any { it.kind == Kind.Link && it.body == u }) continue
                val r = Rec(Kind.Link, newBlock(Kind.Link, st.fresh(), body = u))
                r.fetchLink(ctx)
                st.recs.add(r)
            }
            busy = false
            // a note that is only a shared link is just the link card
            if (st.recs.any { it.kind == Kind.Link && it.body == st.body.trim() }) st.body = ""
            repo.upsert(st.build(old))
            onDone(false)
        }
    }

    val field = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
        focusedContainerColor = cs.secondaryContainer.copy(alpha = .43f), unfocusedContainerColor = cs.secondaryContainer.copy(alpha = .43f),
    )
    val round = RoundedCornerShape(20.dp)

    @Composable fun cap(t: String) = Text(t.uppercase(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))

    Scaffold(
        containerColor = cs.surface,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::close) { Icon(Icons.Rounded.Close, "Close") }
                Text(if (old == null) "New note" else "Edit note", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (old != null) IconButton(onClick = { askDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
                Button(onClick = ::save, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Save")
                }
            }
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp),
        ) {
            TextField(
                st.title, { st.title = it }, Modifier.fillMaxWidth(),
                textStyle = flexStyle(30.sp, wght = 700, rond = 30f),
                placeholder = { Text("Title (optional)", style = flexStyle(30.sp, wght = 700, rond = 30f)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                ),
            )
            cap("Text")
            TextField(st.body, { st.body = it }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 10, shape = round, colors = field, placeholder = { Text("Write something") })
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Smart layout", style = MaterialTheme.typography.titleSmall)
                    Text("Finds dates, places, prices and figures in the text and lays them out as a zine page", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Switch(st.smart, { st.smart = it })
            }
            cap("Checklist")
            TextField(st.list, { st.list = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 10, shape = round, colors = field, placeholder = { Text("One item per line (start with x if done)") })
            cap("Photos")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(st.photos, key = { it.id }) { p -> Thumb(p) { st.photos.remove(p) } }
                item {
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(cs.primaryContainer).clickable(enabled = !busy) { addPhotos() }, contentAlignment = Alignment.Center) {
                        if (busy) androidx.compose.material3.LoadingIndicator(Modifier.size(40.dp)) else Icon(Icons.Rounded.AddPhotoAlternate, "Add photos", tint = cs.onPrimaryContainer)
                    }
                }
            }
            cap("More")
            for (r in st.recs) {
                RecordForm(
                    r,
                    onSearch = {
                        scope.launch {
                            val res = r.search()
                            if (res.isEmpty()) snack.showSnackbar(if (r.title.isBlank()) "Type a title first." else "Nothing found. Check the title and your connection.")
                            else sheet = r to res
                        }
                    },
                    onImport = { scope.launch { if (!r.importFromUrl(ctx)) snack.showSnackbar("Couldn't find a recipe on that page. You can fill it in by hand.") } },
                    onFetch = { scope.launch { if (!r.fetchLink(ctx)) snack.showSnackbar("Couldn't read a preview. The link will still be saved.") } },
                    onRemove = { st.recs.remove(r) },
                )
            }
            for (e in st.extras) {
                val (th, bh) = hints(e.kind)
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label(e.kind).uppercase(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { st.extras.remove(e) }) { Icon(Icons.Rounded.Close, "Remove", Modifier.size(18.dp)) }
                        }
                        TextField(e.body, { e.body = it }, Modifier.fillMaxWidth(), minLines = if (e.kind == Kind.Quote || e.kind == Kind.Info) 2 else 1, maxLines = 8, shape = round, colors = field, placeholder = { Text(bh) })
                        Spacer(Modifier.size(8.dp))
                        TextField(e.title, { e.title = it }, Modifier.fillMaxWidth(), shape = round, colors = field, placeholder = { Text(th) })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in listOf(Kind.Film, Kind.Track, Kind.Book, Kind.Recipe, Kind.Link)) {
                    AssistChip(onClick = { st.recs.add(Rec(k, newBlock(k, st.fresh()))) }, label = { Text(recLabel(k)) })
                }
                for (k in listOf(Kind.Quote, Kind.Stat, Kind.Info)) {
                    AssistChip(onClick = { st.extras.add(Extra(k, newBlock(k, st.fresh()))) }, label = { Text(label(k)) })
                }
            }
            cap("Tags")
            TextField(st.tags, { st.tags = it }, Modifier.fillMaxWidth(), singleLine = true, shape = round, colors = field, placeholder = { Text("Comma separated") })
        }
    }

    sheet?.let { (rec, list) ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            LazyColumn(Modifier.padding(bottom = 24.dp)) {
                items(list) { c ->
                    ListItem(
                        headlineContent = { Text(c.title) },
                        supportingContent = { Text(c.sub) },
                        modifier = Modifier.clickable { sheet = null; scope.launch { rec.applyCand(c, ctx) } },
                    )
                }
            }
        }
    }
    if (askDiscard) AlertDialog(
        onDismissRequest = { askDiscard = false },
        title = { Text("Discard changes?") },
        text = { Text("What you typed on this page hasn't been saved.") },
        confirmButton = { TextButton({ askDiscard = false; onDone(false) }) { Text("Discard") } },
        dismissButton = { TextButton({ askDiscard = false }) { Text("Keep editing") } },
    )
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text("Delete this note?") },
        text = { Text("This can't be undone.") },
        confirmButton = { TextButton({ askDelete = false; old?.let { repo.delete(it.id) }; onDone(true) }) { Text("Delete") } },
        dismissButton = { TextButton({ askDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Thumb(p: Block, onRemove: () -> Unit) {
    val img by produceState<ImageBitmap?>(null, p.path) { value = p.path?.let { Images.load(it, 300) } }
    Box(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        // a 48dp touch target around the small round button
        Box(Modifier.align(Alignment.TopEnd).size(48.dp).clickable(onClickLabel = "Remove photo", onClick = onRemove), contentAlignment = Alignment.Center) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Remove photo", Modifier.size(14.dp), tint = Color.White)
            }
        }
    }
}
