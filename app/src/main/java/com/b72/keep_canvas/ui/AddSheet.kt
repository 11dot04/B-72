@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalDining
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.b72.keep_canvas.data.Cand
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.data.newBlock
import com.b72.keep_canvas.data.searchBooks
import com.b72.keep_canvas.data.searchFilms
import com.b72.keep_canvas.data.searchTracks
import kotlinx.coroutines.launch

private class Opt(val id: String, val label: String, val icon: ImageVector)

private val OPTS = listOf(
    Opt("photo", "Photos", Icons.Rounded.AddPhotoAlternate), Opt("list", "Checklist", Icons.Rounded.Checklist),
    Opt("link", "Link", Icons.Rounded.Link), Opt("quote", "Quote", Icons.Rounded.FormatQuote),
    Opt("film", "Film", Icons.Rounded.Movie), Opt("book", "Book", Icons.Rounded.MenuBook),
    Opt("track", "Track", Icons.Rounded.MusicNote), Opt("recipe", "Recipe", Icons.Rounded.LocalDining),
)

/** The plus menu of the note page: everything that can be added to a note, without leaving it. */
@Composable
fun AddSheet(repo: Repo, noteId: String, hasList: Boolean, initial: String? = null, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var text2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Cand>>(emptyList()) }
    var err by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) scope.launch { repo.appendPhotos(noteId, uris) }
        onClose()
    }
    fun go(id: String) {
        when (id) {
            "photo" -> pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            "list" -> {
                if (!hasList) repo.appendBlock(noteId, newBlock(Kind.Checklist, "in_c${System.nanoTime()}", title = "List", items = listOf("")))
                onClose()
            }
            else -> { mode = id; text = ""; text2 = ""; results = emptyList(); err = "" }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { initial?.let { go(it) } }
    ModalBottomSheet(onClose) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            if (mode == null) {
                Text("Add to this note", style = flexStyle(androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp), wght = 720, rond = 40f))
                LazyVerticalGrid(GridCells.Fixed(4), Modifier.padding(top = 16.dp).height(210.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(OPTS) { o ->
                        Column(
                            Modifier.clip(RoundedCornerShape(22.dp)).background(cs.secondaryContainer).clickable(onClickLabel = o.label) { go(o.id) }.padding(vertical = 16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(o.icon, null, tint = cs.onSecondaryContainer)
                            Text(o.label, style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            } else {
                val m = mode!!
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ mode = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Text(OPTS.first { it.id == m }.label, style = MaterialTheme.typography.titleLarge)
                }
                when (m) {
                    "quote" -> {
                        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("The quote") }, minLines = 2)
                        OutlinedTextField(text2, { text2 = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Who said it") }, singleLine = true)
                        Button({
                            if (text.isNotBlank()) { repo.appendBlock(noteId, newBlock(Kind.Quote, "q${System.nanoTime()}", title = text2.trim(), body = text.trim())); onClose() }
                        }, Modifier.padding(top = 12.dp)) { Text("Add quote") }
                    }
                    "link", "recipe" -> {
                        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(if (m == "link") "Web address" else "Address of a recipe page") }, singleLine = true)
                        if (err.isNotEmpty()) Text(err, color = cs.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        Button({
                            val u = text.trim().let { if (it.startsWith("http")) it else "https://$it" }
                            if (text.isBlank()) return@Button
                            if (m == "link") { repo.appendLink(noteId, u); onClose() }
                            else scope.launch { busy = true; val ok = repo.appendRecipe(noteId, u); busy = false; if (ok) onClose() else err = "No recipe found on that page. Add it as a link instead." }
                        }, Modifier.padding(top = 12.dp), enabled = !busy) { Text(if (busy) "Reading…" else "Add") }
                    }
                    else -> {
                        OutlinedTextField(
                            text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                            label = { Text("Search ${OPTS.first { it.id == m }.label.lowercase()}s") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                                scope.launch { busy = true; results = when (m) { "film" -> searchFilms(text); "book" -> searchBooks(text); else -> searchTracks(text) }; busy = false; if (results.isEmpty()) err = "Nothing found" else err = "" }
                            }),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        )
                        if (busy) LoadingIndicator(Modifier.align(Alignment.CenterHorizontally).padding(12.dp).size(40.dp))
                        if (err.isNotEmpty()) Text(err, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        LazyColumn(Modifier.height(320.dp)) {
                            items(results, key = { it.title + it.sub }) { c ->
                                val img by produceState<ImageBitmap?>(null, c.image) { value = c.image?.let { Images.load(it, 200) } }
                                Row(
                                    Modifier.fillMaxWidth().clickable(onClickLabel = "Add") {
                                        val kind = when (m) { "film" -> Kind.Film; "book" -> Kind.Book; else -> Kind.Track }
                                        scope.launch { repo.appendCand(noteId, kind, c) }; onClose()
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh)) {
                                        img?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop) }
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                        Text(c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
