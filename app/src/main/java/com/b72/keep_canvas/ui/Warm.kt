package com.b72.keep_canvas.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Lays a note's words out before the page asks for them. Text shaping is the slow part of the first frames after a page opens, and the
 * system keeps what it has shaped in a cache that a different note pushes out; shaping the words on a background thread ahead of the tap
 * (the most recent notes when the library loads, and the tapped one the moment it is tapped) leaves that cache full of exactly what the page needs.
 */
object Warm {
    var note: (Note) -> Unit = {}
}

@Composable
fun WarmText(notes: List<Note>) {
    val tm = rememberTextMeasurer(cacheSize = 2)
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp)
    val scope = rememberCoroutineScope()
    val seen = remember { HashSet<String>() }
    fun run(n: Note) {
        for (b in n.blocks) {
            if (b.kind != Kind.Text || b.body.isBlank()) continue
            val key = b.id + b.body.length + b.str("spans").orEmpty().length
            if (!seen.add(key)) continue
            scope.launch(Dispatchers.Default) {
                runCatching { tm.measure(styled(b.body.take(4000), parseSpans(b.str("spans")), 18f), style, constraints = Constraints(maxWidth = 1000)) }
            }
        }
    }
    LaunchedEffect(Unit) { Warm.note = { run(it) } }
    // the most recent notes, a few at a time so start-up is not crowded
    LaunchedEffect(notes.size) {
        withContext(Dispatchers.Default) { kotlinx.coroutines.delay(1500) }
        for (n in notes.sortedByDescending { it.edited }.take(10)) { run(n); kotlinx.coroutines.delay(120) }
    }
}
