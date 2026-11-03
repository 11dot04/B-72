@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.b72.keep_canvas.data.str
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.cutPath
import com.b72.keep_canvas.data.dbl
import com.b72.keep_canvas.data.field
import com.b72.keep_canvas.data.seedOf
import kotlin.math.roundToInt

/** The three stages a thing goes through, in the words that suit it. Stored as want, doing, done. */
fun statusLabels(kind: Kind): List<String> = when (kind) {
    Kind.Film -> listOf("To watch", "Watching", "Watched")
    Kind.Book -> listOf("To read", "Reading", "Read")
    Kind.Recipe -> listOf("To try", "Cooking", "Made")
    Kind.Track -> listOf("To hear", "On repeat", "Loved")
    else -> emptyList()
}
val STATUS_KEYS = listOf("want", "doing", "done")

/** Where you are with it, and what you thought: tap a stage, tap a star (tap the same star again to clear it). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Tracker(b: Block, fg: androidx.compose.ui.graphics.Color, onData: (String, kotlinx.serialization.json.JsonElement) -> Unit) {
    val labels = statusLabels(b.kind)
    val cur = b.str("status").orEmpty()
    val r = b.dbl("rating")?.roundToInt() ?: 0
    Column(Modifier.padding(bottom = 16.dp)) {
        if (labels.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l ->
                androidx.compose.material3.FilterChip(cur == STATUS_KEYS[i], { onData("status", kotlinx.serialization.json.JsonPrimitive(if (cur == STATUS_KEYS[i]) "" else STATUS_KEYS[i])) }, label = { Text(l) })
            }
        }
        Row(Modifier.padding(top = 10.dp)) {
            for (i in 1..5) Icon(
                if (i <= r) Icons.Rounded.Star else Icons.Rounded.StarOutline, "Rate $i",
                Modifier.size(34.dp).clip(CircleShape).clickable { onData("rating", kotlinx.serialization.json.JsonPrimitive(if (i == r) 0 else i)) }.padding(3.dp), tint = fg,
            )
        }
    }
}

@Composable
private fun Stars(r: Int, color: androidx.compose.ui.graphics.Color, size: Dp) {
    Row { for (i in 1..5) Icon(if (i <= r) Icons.Rounded.Star else Icons.Rounded.StarOutline, null, Modifier.size(size), tint = color) }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, fg: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(fg.copy(alpha = .11f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = fg)
    }
}

@Composable
private fun Label(t: String, fg: androidx.compose.ui.graphics.Color) =
    Text(t, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp), color = fg.copy(alpha = .67f), modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

@Composable
private fun Notes(l: String, text: String, fg: androidx.compose.ui.graphics.Color) {
    if (text.isEmpty()) return
    Column {
        Label(l, fg)
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp), color = fg)
    }
}

/** A film, book, track, recipe or link on the note page. [w] is the content width. */
@Composable
fun RecSection(b: Block, noteTitle: String, pal: NotePalette, w: Dp, modifier: Modifier = Modifier, onLyric: ((String) -> Unit)? = null, onData: ((String, kotlinx.serialization.json.JsonElement) -> Unit)? = null) {
    var lyricSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val fg = pal.fg
    val showTitle = b.title != noteTitle
    val has = b.path != null
    fun v(k: String) = b.field(k)
    val rating = b.dbl("rating")?.roundToInt() ?: 0
    Column(modifier.padding(bottom = 26.dp)) {
        if (onData != null && b.kind != Kind.Link) Tracker(b, fg, onData)
        when (b.kind) {
            Kind.Film, Kind.Book -> {
                val book = b.kind == Kind.Book
                val meta = if (book) listOf(v("year"), v("pages").takeIf { it.isNotEmpty() && it != "0" }?.let { "$it pages" } ?: "", v("genre"))
                else listOf(if (v("series").isNotEmpty()) "Series" else "", v("year"), v("seasons").takeIf { it.isNotEmpty() }?.let { "$it seasons" } ?: v("runtime").takeIf { it.isNotEmpty() }?.let { "$it min" } ?: "", v("genre"))
                val by = if (book) v("author").takeIf { it.isNotEmpty() }?.let { "By $it" } ?: "" else v("director").takeIf { it.isNotEmpty() }?.let { (if (v("series").isNotEmpty()) "Created by " else "Directed by ") + it } ?: ""
                Row {
                    if (has) {
                        PhotoImage(b.path!!, 700, false, pal.primary, Modifier.width(w * .38f).aspectRatio(2f / 3f).clip(RoundedCornerShape(22.dp)))
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        if (showTitle && b.title.isNotEmpty()) Text(b.title, style = flexStyle(26.sp, wght = 760, rond = 30f, lh = 1.05f), color = fg)
                        Spacer(Modifier.height(8.dp))
                        Text(meta.filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp), color = fg.copy(alpha = .78f))
                        if (by.isNotEmpty()) Text(by, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = fg, modifier = Modifier.padding(top = 6.dp))
                        if (rating > 0) Box(Modifier.padding(top = 12.dp)) { Stars(rating, fg, 26.dp) }
                    }
                }
                if (!book && v("overview").isNotEmpty()) Text(v("overview"), maxLines = 6, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp), color = fg.copy(alpha = .78f), modifier = Modifier.padding(top = 16.dp))
                Notes("YOUR THOUGHTS", b.body, fg)
            }
            Kind.Track -> {
                // after the early Material "top songs" concept: a colour block from the cover, its picture in two tones, a huge condensed title, small mono labels
                val hue by androidx.compose.runtime.produceState(seedOf(b.id) % 360f, b.path) { b.path?.let { pa -> hueOfPhoto(pa)?.let { value = it } } }
                val h0 = ((hue % 360f) + 360f) % 360f
                val dark = androidx.compose.foundation.isSystemInDarkTheme()
                val block = androidx.compose.ui.graphics.Color.hsl(h0, if (dark) .4f else .45f, if (dark) .22f else .78f)
                val ink = androidx.compose.ui.graphics.Color.hsl(h0, .5f, if (dark) .92f else .1f)
                val deep = androidx.compose.ui.graphics.Color.hsl(h0, .6f, if (dark) .12f else .2f)
                val light = androidx.compose.ui.graphics.Color.hsl(h0, .6f, if (dark) .75f else .9f)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(block).padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOP", style = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .8.sp), color = ink.copy(alpha = .8f))
                        Text("SONGS", style = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .8.sp), color = ink.copy(alpha = .8f))
                    }
                    if (has) {
                        val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, b.path) { value = com.b72.keep_canvas.data.Images.load(b.path!!, 800) }
                        img?.let {
                            androidx.compose.foundation.Image(
                                it, null, Modifier.padding(top = 12.dp).fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(22.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(run {
                                    val lr = .2126f; val lg = .7152f; val lb = .0722f
                                    fun row(i: Float, p: Float) = floatArrayOf((p - i) * lr, (p - i) * lg, (p - i) * lb, 0f, i * 255f)
                                    row(deep.red, light.red) + row(deep.green, light.green) + row(deep.blue, light.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
                                })),
                            )
                        }
                    }
                    if (showTitle && b.title.isNotEmpty()) Text(b.title.uppercase(), style = flexStyle(46.sp, wght = 700, wdth = 45f, lh = .95f), color = ink, modifier = Modifier.padding(top = 14.dp))
                    if (v("artist").isNotEmpty()) Text(v("artist").uppercase(), style = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = .8.sp), color = ink.copy(alpha = .85f), modifier = Modifier.padding(top = 6.dp))
                    Text(listOf(v("album"), v("year"), v("genre")).filter { it.isNotEmpty() }.joinToString("  ·  ").uppercase(), style = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .6.sp), color = ink.copy(alpha = .65f), modifier = Modifier.padding(top = 4.dp))
                }
                if (v("url").startsWith("http")) {
                    val uri = LocalUriHandler.current
                    FilledTonalButton(onClick = { runCatching { uri.openUri(v("url")) } }, modifier = Modifier.padding(top = 12.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("OPEN IN ${v("src").ifEmpty { "APP" }.uppercase()}")
                    }
                }
                if (v("lyric").isNotBlank()) LyricsCard(v("lyric"), b.title, v("artist"), b.id, Modifier.padding(top = 18.dp), onEdit = onLyric?.let { { lyricSheet = true } })
                else if (onLyric != null) androidx.compose.material3.TextButton({ lyricSheet = true }, Modifier.padding(top = 6.dp)) { Text("Add a lyrics card") }
                if (lyricSheet && onLyric != null) LyricsSheet(b.title, v("artist"), v("lyric"), onLyric) { lyricSheet = false }
                Notes("WHY YOU LOVE IT", b.body, fg)
            }
            Kind.Recipe -> {
                val ing = v("ingredients").lines().filter { it.isNotBlank() }
                val steps = v("steps").lines().filter { it.isNotBlank() }
                // a dish with a clear subject breaks out of an expressive shape; otherwise a plain photo
                if (has && canPopOut(b)) PopOut(b, pal.primary, pal.primary.copy(alpha = .47f), seedOf(b.id), Modifier.fillMaxWidth().height(w * .95f))
                else if (has) PhotoImage(b.path!!, 1000, false, pal.primary, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(28.dp)))
                if (showTitle && b.title.isNotEmpty()) Text(b.title, style = flexStyle(30.sp, wght = 760, rond = 30f, lh = 1.05f), color = fg, modifier = Modifier.padding(top = 14.dp))
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (v("time").isNotEmpty() && v("time") != "0") InfoChip(Icons.Rounded.Timer, "${v("time")} min", fg)
                    if (v("servings").isNotEmpty()) InfoChip(Icons.Rounded.Group, "Serves ${v("servings")}", fg)
                }
                if (ing.isNotEmpty()) Label("INGREDIENTS", fg)
                for (e in ing) Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 10.dp, end = 12.dp).size(6.dp).clip(CircleShape).background(fg))
                    Text(e, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 23.sp), color = fg)
                }
                if (steps.isNotEmpty()) Label("STEPS", fg)
                steps.forEachIndexed { i, s ->
                    Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.Top) {
                        Text("${i + 1}", style = flexStyle(32.sp, wght = 800, lh = 1f), color = fg.copy(alpha = .43f), modifier = Modifier.width(46.dp))
                        Text(s, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp), color = fg, modifier = Modifier.weight(1f))
                    }
                }
                Notes("NOTES", b.body, fg)
            }
            else -> { // link
                val uri = LocalUriHandler.current
                val url = if (b.body.startsWith("http")) b.body else "https://${b.body}"
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(fg.copy(alpha = .09f))) {
                    if (has) PhotoImage(b.path!!, 1000, false, pal.primary, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Column(Modifier.padding(18.dp)) {
                        if (v("site").isNotEmpty()) Text(v("site").uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .8.sp), color = fg.copy(alpha = .67f))
                        if (b.title.isNotEmpty()) Text(b.title, style = flexStyle(24.sp, wght = 720, rond = 30f, lh = 1.1f), color = fg, modifier = Modifier.padding(top = 6.dp))
                        if (v("stats").isNotEmpty()) StatGrid(v("stats"), v("src"), fg)
                        if (v("desc").isNotEmpty() && v("stats").isEmpty()) Text(v("desc"), maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp), color = fg.copy(alpha = .82f), modifier = Modifier.padding(top = 8.dp))
                        Text(b.body, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = fg.copy(alpha = .59f), modifier = Modifier.padding(top = 10.dp))
                        FilledTonalButton(onClick = { runCatching { uri.openUri(url) } }, modifier = Modifier.padding(top = 14.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("OPEN")
                        }
                        if (v("note").isNotEmpty()) Text(v("note"), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp), color = fg, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

/** The numbers of a shared activity or track as big figures, with a small felt-tip picture of the service's world in the corner. */
@Composable
private fun StatGrid(stats: String, src: String, fg: androidx.compose.ui.graphics.Color) {
    val rows = stats.lines().mapNotNull { l -> l.split('|', limit = 2).takeIf { it.size == 2 && it[1].isNotBlank() }?.let { it[0] to it[1] } }
    if (rows.isEmpty()) return
    val pic = when (src) { "Spotify" -> Pic.Music; "Google Maps" -> Pic.Pin; "Goodreads" -> Pic.Book; "Letterboxd" -> Pic.Camera; "YouTube" -> Pic.Music; else -> Pic.Mountain }
    Box(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val sz = size.height.coerceAtMost(size.width * .4f)
            drawPic(pic, size.width - sz, size.height - sz, sz, fg.copy(alpha = .13f), src.hashCode())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for ((k, v) in rows) Column {
                Text(v, style = flexStyle(if (v.length > 9) 22.sp else 30.sp, wght = 780, rond = 30f, lh = 1f), color = fg)
                Text(k.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = .8.sp), color = fg.copy(alpha = .6f), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
