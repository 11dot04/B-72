@file:OptIn(ExperimentalMaterial3Api::class)

package com.b72.keep_canvas.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.CUT_KEYS
import com.b72.keep_canvas.data.Cand
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.dbl
import com.b72.keep_canvas.data.imageDir
import com.b72.keep_canvas.data.importRecipe
import com.b72.keep_canvas.data.linkPreview
import com.b72.keep_canvas.data.saveImage
import com.b72.keep_canvas.data.searchBooks
import com.b72.keep_canvas.data.searchFilms
import com.b72.keep_canvas.data.searchTracks
import kotlinx.serialization.json.JsonPrimitive

/** A film, book, track, recipe or link being edited. All of its text lives in plain observable fields. */
class Rec(val kind: Kind, val src: Block) {
    var title by mutableStateOf(src.title)
    var body by mutableStateOf(src.body)
    var path by mutableStateOf(src.path)
    var busy by mutableStateOf(false)
    var rating by mutableIntStateOf(src.dbl("rating")?.toInt() ?: 0)
    val f = mutableStateMapOf<String, String>().apply {
        for ((k, v) in src.data) if (k !in CUT_KEYS && k != "rating") (v as? JsonPrimitive)?.content?.let { put(k, it) }
    }

    fun read(key: String) = when (key) { "title" -> title; "body" -> body; else -> f[key] ?: "" }
    fun write(key: String, v: String) { when (key) { "title" -> title = v; "body" -> body = v; else -> f[key] = v } }

    val filled get() = title.isNotBlank() || body.isNotBlank() || (f["ingredients"] ?: "").isNotBlank() || (kind == Kind.Link && body.isNotBlank())

    fun block(): Block {
        var data = src.data.filterKeys { path == src.path || it !in CUT_KEYS }
        data = data + f.mapValues { JsonPrimitive(it.value) as kotlinx.serialization.json.JsonElement }
        data = if (rating > 0) data + ("rating" to JsonPrimitive(rating)) else data - "rating"
        return src.copy(k = kind.ordinal, title = title.trim(), body = body.trim(), path = path, data = data)
    }
}

private val FIELDS = mapOf(
    Kind.Film to listOf("year" to "Year", "director" to "Director", "genre" to "Genre", "runtime" to "Runtime (min)"),
    Kind.Track to listOf("artist" to "Artist", "album" to "Album", "year" to "Year", "genre" to "Genre"),
    Kind.Book to listOf("author" to "Author", "year" to "Year", "genre" to "Genre", "pages" to "Pages"),
    Kind.Recipe to listOf("time" to "Time (min)", "servings" to "Serves"),
)

fun recLabel(k: Kind) = when (k) { Kind.Film -> "Film"; Kind.Track -> "Track"; Kind.Recipe -> "Recipe"; Kind.Link -> "Link"; Kind.Book -> "Book"; else -> k.name }

// ---- actions (each returns a message to show when something went wrong) ----

suspend fun Rec.search(): List<Cand> {
    val q = title.trim()
    if (q.isEmpty()) return emptyList()
    busy = true
    return try { when (kind) { Kind.Film -> searchFilms(q); Kind.Book -> searchBooks(q); else -> searchTracks(q) } } finally { busy = false }
}

suspend fun Rec.applyCand(c: Cand, ctx: Context) {
    title = c.title
    for ((k, v) in c.data) if (v.isNotEmpty()) write(k, v)
    busy = true
    saveImage(c.image, imageDir(ctx))?.let { path = it }
    busy = false
}

suspend fun Rec.importFromUrl(ctx: Context): Boolean {
    val url = (f["url"] ?: "").trim()
    if (url.isEmpty()) return true
    busy = true
    val r = importRecipe(url)
    if (r.isEmpty()) { busy = false; return false }
    for (k in listOf("title", "ingredients", "steps", "time", "servings", "url")) r[k]?.takeIf { it.isNotEmpty() }?.let { write(k, it) }
    saveImage(r["image"], imageDir(ctx))?.let { path = it }
    busy = false
    return true
}

suspend fun Rec.fetchLink(ctx: Context): Boolean {
    val url = body.trim()
    if (url.isEmpty()) return true
    busy = true
    val p = linkPreview(url)
    if (p.isNotEmpty()) {
        if (title.isEmpty()) title = p["title"] ?: ""
        f["desc"] = p["desc"] ?: ""; f["site"] = p["site"] ?: ""
        saveImage(p["image"], imageDir(ctx))?.let { path = it }
    }
    busy = false
    return p.isNotEmpty()
}

// ---- form ----

@Composable
fun RecordForm(rec: Rec, onSearch: () -> Unit, onImport: () -> Unit, onFetch: () -> Unit, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val round = RoundedCornerShape(16.dp)
    val colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        focusedContainerColor = cs.secondaryContainer.copy(alpha = .43f), unfocusedContainerColor = cs.secondaryContainer.copy(alpha = .43f),
    )
    val k = rec.kind
    @Composable fun action(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, f: () -> Unit) {
        if (rec.busy) CircularProgressIndicator(Modifier.padding(12.dp).size(18.dp), strokeWidth = 2.dp)
        else IconButton(onClick = f) { Icon(icon, desc) }
    }
    @Composable fun field(key: String, label: String, lines: Int = 1, trailing: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier.fillMaxWidth()) {
        TextField(
            rec.read(key), { rec.write(key, it) }, modifier.padding(top = 8.dp),
            label = { Text(label) }, minLines = lines, maxLines = if (lines == 1) 1 else 8, shape = round, colors = colors, trailingIcon = trailing,
        )
    }
    val img by produceState<ImageBitmap?>(null, rec.path) { value = rec.path?.let { Images.load(it, 400) } }
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(recLabel(k).uppercase(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove", Modifier.size(18.dp)) }
            }
            if (k != Kind.Link) img?.let { Image(it, null, Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
            when (k) {
                Kind.Film, Kind.Track, Kind.Book ->
                    field("title", when (k) { Kind.Film -> "Film title"; Kind.Book -> "Book title"; else -> "Song title" }, trailing = { action(Icons.Rounded.Search, "Search", onSearch) })
                Kind.Recipe -> {
                    field("url", "Recipe link (optional)", trailing = { action(Icons.Rounded.Download, "Import", onImport) })
                    field("title", "Recipe name")
                }
                else -> {
                    field("body", "Link", trailing = { action(Icons.Rounded.TravelExplore, "Fetch preview", onFetch) })
                    field("title", "Title")
                }
            }
            val pairs = FIELDS[k] ?: emptyList()
            for (i in pairs.indices step 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    field(pairs[i].first, pairs[i].second, modifier = Modifier.weight(1f))
                    if (i + 1 < pairs.size) field(pairs[i + 1].first, pairs[i + 1].second, modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                }
            }
            if (k == Kind.Film || k == Kind.Book) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your rating  ", style = MaterialTheme.typography.bodyMedium)
                    for (i in 1..5) Icon(
                        if (i <= rec.rating) Icons.Rounded.Star else Icons.Rounded.StarOutline, "$i stars",
                        Modifier.size(30.dp).clickable { rec.rating = if (rec.rating == i) 0 else i },
                    )
                }
            }
            if (k == Kind.Recipe) {
                field("ingredients", "Ingredients (one per line)", lines = 4)
                field("steps", "Steps (one per line)", lines = 4)
            }
            field(if (k == Kind.Link) "note" else "body", when (k) { Kind.Film, Kind.Book -> "Your thoughts"; Kind.Track -> "Why you love it"; else -> "Notes" }, lines = 2)
        }
    }
}
