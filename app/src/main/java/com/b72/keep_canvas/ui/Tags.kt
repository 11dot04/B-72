@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.b72.keep_canvas.data.Note

/** The tags a note carries, with a field to add more; tags already used elsewhere pop up as you type. */
@Composable
fun TagEditor(
    tags: List<String>, allNotes: List<Note>, pal: NotePalette,
    onAdd: (String) -> Unit, onRemove: (String) -> Unit, onOpen: (String) -> Unit,
    onEditing: (Boolean) -> Unit, modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var had by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val bring = remember { BringIntoViewRequester() }
    val freq = remember(allNotes) { allNotes.flatMap { it.tags }.groupingBy { it }.eachCount() }
    fun norm(t: String): String {
        val c = t.trim().trimStart('#').trim().replace(Regex("\\s+"), " ")
        return freq.keys.firstOrNull { it.equals(c, true) } ?: c.lowercase()
    }
    fun commit(t: String = q) {
        val n = norm(t)
        if (n.isNotEmpty() && n !in tags) onAdd(n)
        q = ""
    }
    val needle = q.trim().trimStart('#').lowercase()
    val suggestions = remember(freq, tags, needle) {
        freq.entries.filter { it.key !in tags && (needle.isEmpty() || it.key.lowercase().contains(needle)) }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.key.lowercase().startsWith(needle) }.thenByDescending { it.value })
            .take(6).map { it.key }
    }
    LaunchedEffect(open) { if (open) runCatching { fr.requestFocus() } }
    LaunchedEffect(suggestions, q) { if (open) runCatching { bring.bringIntoView() } }
    Column(modifier.fillMaxWidth().bringIntoViewRequester(bring)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (t in tags) InputChip(
                selected = false, onClick = { onOpen(t) }, label = { Text(t) },
                trailingIcon = { Icon(Icons.Rounded.Close, "Remove $t", Modifier.size(18.dp).clickable { onRemove(t) }) },
                shape = RoundedCornerShape(50),
            )
            if (!open) AssistChip(onClick = { open = true }, label = { Text("Add tag") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(50))
        }
        if (open) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).background(pal.fg.copy(alpha = .08f), RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Tag", style = MaterialTheme.typography.bodyLarge, color = pal.fg.copy(alpha = .4f))
                    BasicTextField(
                        q, { v -> if (v.endsWith(",") || v.endsWith("\n")) commit(v.dropLast(1)) else q = v },
                        Modifier.fillMaxWidth().focusRequester(fr).onFocusChanged { f ->
                            onEditing(f.isFocused)
                            if (f.isFocused) had = true
                            else if (had) { had = false; if (q.isBlank()) open = false }
                        },
                        singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = pal.fg), cursorBrush = SolidColor(pal.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (q.isBlank()) open = false else commit() }),
                    )
                }
            }
            if (suggestions.isNotEmpty()) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (t in suggestions) SuggestionChip(onClick = { commit(t) }, label = { Text(t) }, shape = RoundedCornerShape(50))
            }
        }
    }
}
