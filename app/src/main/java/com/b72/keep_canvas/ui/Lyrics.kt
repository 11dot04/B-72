@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.fetchLyrics
import com.b72.keep_canvas.data.seedOf
import com.b72.keep_canvas.ui.board.swatchOf

/** The part of a song someone loved, set as a card: big italic serif on a colour that belongs to the song. */
@Composable
fun LyricsCard(text: String, title: String, artist: String, seed: String, modifier: Modifier = Modifier, onEdit: (() -> Unit)? = null) {
    val h = (seedOf(seed) % 360).toFloat()
    val style = seedOf(seed + "ly") % 3
    val (bg, ink) = when (style) { 0 -> swatchOf(h, true); 1 -> swatchOf(h, false); else -> androidx.compose.ui.graphics.Color(0xFF17151B) to swatchOf(h, true).first }
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .8.sp, color = ink.copy(alpha = .75f))
    val lines = text.lines().filter { it.isNotBlank() }
    val fs = when { text.length < 60 -> 42.sp; text.length < 130 -> 33.sp; else -> 26.sp }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(bg).let { m -> if (onEdit != null) m.clickable(onClickLabel = "Edit the lyrics card") { onEdit() } else m }.padding(24.dp)) {
        Text("“", style = TextStyle(fontFamily = Serif, fontSize = 88.sp, lineHeight = 64.sp, color = ink.copy(alpha = .35f)))
        for (l in lines) Text(l, style = TextStyle(fontFamily = Serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = fs, lineHeight = fs * 1.12f, color = ink))
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.GraphicEq, null, Modifier.size(16.dp), tint = ink.copy(alpha = .75f))
            Spacer(Modifier.width(8.dp))
            Text(listOf(title, artist).filter { it.isNotBlank() }.joinToString("  ·  ").uppercase(), style = mono, maxLines = 1)
        }
    }
}

/** Pick the lines you loved from the fetched lyrics, or type your own. Entirely optional. */
@Composable
fun LyricsSheet(title: String, artist: String, current: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var typing by remember { mutableStateOf(current.isNotBlank()) }
    var text by remember { mutableStateOf(current) }
    var lines by remember { mutableStateOf<List<String>?>(null) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(title, artist) { lines = fetchLyrics(artist, title) }
    ModalBottomSheet(onClose) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("Lyrics card", style = flexStyle(26.sp, wght = 720, rond = 40f))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!typing, { typing = false }, { Text("Pick from the song") })
                FilterChip(typing, { typing = true }, { Text("Write it myself") })
            }
            if (typing) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("The lines you love") }, minLines = 3)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ onSave(text.trim()); onClose() }, enabled = text.isNotBlank() || current.isNotBlank()) { Text(if (text.isBlank()) "Remove card" else "Make the card") }
                }
            } else {
                val ls = lines
                when {
                    ls == null -> LoadingIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp).size(44.dp))
                    ls.isEmpty() -> Text("Couldn't find the lyrics for this one. You can still write the lines yourself.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
                    else -> {
                        Text("Tap the lines you loved (up to 8).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                        LazyColumn(Modifier.height(320.dp).padding(top = 6.dp)) {
                            itemsIndexed(ls) { i, l ->
                                val on = i in picked
                                Text(
                                    l, style = MaterialTheme.typography.bodyLarge,
                                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                        .clickable { picked = if (on) picked - i else if (picked.size < 8) picked + i else picked }.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                        Button({ onSave(picked.sorted().joinToString("\n") { ls[it] }); onClose() }, enabled = picked.isNotEmpty(), modifier = Modifier.padding(top = 10.dp)) { Text("Make the card") }
                    }
                }
            }
            if (current.isNotBlank() && !typing) TextButton({ onSave(""); onClose() }) { Text("Remove the card") }
        }
    }
}
