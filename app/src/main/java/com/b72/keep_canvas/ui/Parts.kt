@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.field

private fun describe(b: Block): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> = when (b.kind) {
    Kind.Photo -> Icons.Rounded.Image to "Photo"
    Kind.Checklist -> Icons.Rounded.Checklist to "Checklist · ${b.checkItems.count { it.isNotBlank() }} items"
    Kind.Quote -> Icons.Rounded.FormatQuote to "Quote · " + b.body.take(40)
    Kind.Link -> Icons.Rounded.Link to "Link · " + b.title.ifBlank { b.body }.take(40)
    Kind.Film -> Icons.Rounded.Movie to "Film or show · " + b.title
    Kind.Book -> Icons.Rounded.MenuBook to "Book · " + b.title
    Kind.Track -> Icons.Rounded.MusicNote to "Track · " + b.title
    Kind.Recipe -> Icons.Rounded.Restaurant to "Recipe · " + b.title
    Kind.Audio -> Icons.Rounded.GraphicEq to "Recording"
    Kind.Poster, Kind.Info, Kind.Stat -> Icons.Rounded.Notes to "Details · " + b.title.ifBlank { b.body }.take(40)
    else -> Icons.Rounded.Notes to "Text · " + b.body.lines().firstOrNull { it.isNotBlank() }.orEmpty().take(44).ifBlank { "(empty)" }
}

/** Everything the note is made of, one row each, so any part (a photo, a list, a link, something an idea added) can be taken out. Undo is in the top bar. */
@Composable
fun PartsSheet(note: Note, onRemove: (Block) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onClose) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("Parts of this note", style = MaterialTheme.typography.titleLarge)
            Text("Take out anything you added. Undo is at the top of the note.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            LazyColumn(Modifier.padding(top = 4.dp)) {
                items(note.blocks, key = { it.id }) { b ->
                    val (icon, label) = describe(b)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                            if (b.kind == Kind.Photo && b.path != null) PhotoImage(b.path, 160, false, cs.primary, Modifier.size(44.dp))
                            else Icon(icon, null, tint = cs.onSecondaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton({ onRemove(b) }) { Icon(Icons.Rounded.DeleteOutline, "Remove", tint = cs.error) }
                    }
                }
            }
        }
    }
}
