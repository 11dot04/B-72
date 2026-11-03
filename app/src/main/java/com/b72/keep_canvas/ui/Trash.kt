@file:OptIn(ExperimentalMaterial3Api::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.Repo

/** Archived notes (put away) and the trash (deleted, kept 30 days): bring either back, or remove a trashed note for good. */
@Composable
fun TrashSheet(repo: Repo, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableStateOf(0) }
    val lib = repo.loaded?.library
    val list: List<Note> = if (tab == 0) lib?.archive.orEmpty() else lib?.trash.orEmpty().sortedByDescending { it.trashedAt }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Archive & trash", style = flexStyle(32.sp, wght = 720, wdth = 108f, rond = 40f), color = cs.onSurface)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Archive (${lib?.archive?.size ?: 0})", "Trash (${lib?.trash?.size ?: 0})").forEachIndexed { i, t ->
                    SegmentedButton(tab == i, { tab = i }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(t) }
                }
            }
            if (tab == 1 && list.isNotEmpty()) Text("Deleted notes are removed for good after 30 days.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (list.isEmpty()) Text(if (tab == 0) "Nothing archived. Use Archive in a note's menu to put it away." else "The trash is empty.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.id }) { n ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(n.title.ifBlank { n.snippet(60) }.ifBlank { "Untitled" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton({ repo.restore(n.id) }) { Text("Restore") }
                        if (tab == 1) TextButton({ repo.purge(n.id) }) { Text("Delete", color = cs.error) }
                    }
                }
            }
            if (tab == 1 && list.isNotEmpty()) TextButton({ repo.purge(null) }) { Text("Empty the trash", color = cs.error) }
        }
    }
}
