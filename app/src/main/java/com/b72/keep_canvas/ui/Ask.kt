@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Assistant
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.data.searchText
import com.b72.keep_canvas.data.tripText

/**
 * Ask your notes: the notes closest in meaning (plus word matches) go to the on-device assistant with the question, and it answers
 * from them only, citing each by number. Every number is a tappable source underneath. Nothing leaves the phone.
 */
@Composable
fun AskSheet(repo: Repo, question: String, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var sources by remember { mutableStateOf<List<Note>>(emptyList()) }
    var answer by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(question) {
        val all = repo.loaded?.library?.notes.orEmpty()
        val words = question.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }
        val byWords = all.map { n -> n to words.count { it in n.searchText() } }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        val src = (repo.similar(question, 4) + byWords).distinctBy { it.id }.take(4)
        sources = src
        if (src.isEmpty()) { answer = "I couldn't find any notes about that."; return@LaunchedEffect }
        val ctxText = src.mapIndexed { i, n ->
            "[${i + 1}] ${n.title}\n" + n.blocks.joinToString("\n") { it.tripText() }.take(700)
        }.joinToString("\n\n")
        val prompt = "Answer the question using ONLY the notes below. Cite the notes you used like [1] or [2]. " +
            "If the notes do not contain the answer, say you could not find it. Reply in plain text, under 90 words.\n\nNotes:\n$ctxText\n\nQuestion: $question"
        answer = Assistant.generate(ctx, prompt) ?: "The assistant could not answer. Try again."
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ask your notes", style = flexStyle(32.sp, wght = 720, wdth = 108f, rond = 40f), color = cs.onSurface)
            Text(question, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
            if (answer == null) LoadingIndicator(Modifier.size(48.dp).align(Alignment.CenterHorizontally))
            else Surface(shape = RoundedCornerShape(28.dp), color = cs.tertiaryContainer) {
                SelectionContainer {
                    Text(answer!!, style = TextStyle(fontFamily = Serif, fontSize = 18.sp, lineHeight = 25.sp, color = cs.onTertiaryContainer), modifier = Modifier.padding(20.dp))
                }
            }
            if (sources.isNotEmpty()) {
                Text("FROM", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = .8.sp), color = cs.onSurfaceVariant)
                sources.forEachIndexed { i, n ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh)
                            .clickable { onClose(); onOpen(n.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("${i + 1}", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp), color = cs.primary)
                        Text(n.title.ifBlank { n.snippet(60) }.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
