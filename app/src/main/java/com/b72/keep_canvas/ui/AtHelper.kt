package com.b72.keep_canvas.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.b72.keep_canvas.data.Assistant
import com.b72.keep_canvas.data.Cloud
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.searchBooks
import com.b72.keep_canvas.data.searchFilmsAndShows
import com.b72.keep_canvas.data.searchText
import com.b72.keep_canvas.data.searchTracks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One thing a request found: what to show, a line of detail, and the words that replace the request in the note. */
class AtOption(val label: String, val sub: String, val insert: String)

/** One request typed into a note, and its answer as it arrives. [state]: 0 looking, 1 found, 2 nothing, 3 could not. */
class AtSug(val id: Long, val cmd: String, val query: String, val token: String, val st: RichState) {
    var state by mutableStateOf(0)
    var options by mutableStateOf(emptyList<AtOption>())
    var message by mutableStateOf("")
}

/**
 * Ask for something without leaving the sentence. Write `@look the year the Brooklyn Bridge opened` and press Enter: the writing carries on
 * while the answer is found in the background and offered in a card; tapping it swaps the request for the answer, and ignoring it leaves your
 * words exactly as they were. One word per place to look, so the app knows where:
 * `@look` a fact, date or source (the assistant), `@mine` your own notes, `@read` a book, `@watch` a film or show, `@listen` a song.
 */
class AtHelper(private val ctx: Context, private val scope: CoroutineScope, private val notes: () -> List<Note>, private val selfId: String) {
    val sugs = mutableStateListOf<AtSug>()
    var hint by mutableStateOf<Pair<RichState, String>?>(null)
    private var next = 0L

    private val request = Regex("(?:^|\\s)(@(look|mine|read|watch|listen)\\s+(\\S.{1,}))$", RegexOption.IGNORE_CASE)
    private val partial = Regex("(?:^|\\s)@([a-z]{0,6})$", RegexOption.IGNORE_CASE)

    /** The line a writer just finished: a request when it ends in `@word and what they need`. */
    fun submit(st: RichState, line: String) {
        val m = request.find(line.trimEnd()) ?: return
        val token = m.groupValues[1].trim()
        val s = AtSug(next++, m.groupValues[2].lowercase(), m.groupValues[3].trim(), token, st)
        hint = null
        sugs.add(0, s)
        while (sugs.size > 3) sugs.removeAt(sugs.lastIndex)
        scope.launch { run(s) }
    }

    /** While typing `@lo…`: offer the words that complete it. */
    fun typing(st: RichState, lineBeforeCaret: String) {
        val m = partial.find(lineBeforeCaret)
        hint = if (m != null && COMMANDS.any { it.first.startsWith(m.groupValues[1].lowercase()) }) st to m.groupValues[1] else null
    }

    fun complete(cmd: String) {
        val (st, prefix) = hint ?: return
        st.completeBeforeCaret(prefix.length + 1, "@$cmd ")
        hint = null
    }

    fun accept(s: AtSug, o: AtOption) {
        if (s.st.replaceToken(s.token, o.insert)) sugs.remove(s) else { s.state = 2; s.message = "That line has changed since, so nothing was swapped." }
    }

    fun dismiss(s: AtSug) { sugs.remove(s) }

    private suspend fun run(s: AtSug) {
        try {
            when (s.cmd) {
                "look" -> {
                    if (!Assistant.installed && !Cloud.on) { s.state = 3; s.message = "Needs the assistant: switch on the cloud one in Settings, or install the on-device one."; return }
                    val a = Assistant.generate(ctx, "A writer is in the middle of a note and needs a fact. Answer in one or two plain sentences, at most 40 words, with the answer first. Give only what you are confident of; if you are not sure, start with \"Not sure:\" and give your best guess. No preamble.\nWhat they need: ${s.query}")
                    if (a == null) { s.state = 3; s.message = "Could not get an answer just now. Check the key or the day's allowance in Assistant settings."; return }
                    s.options = listOf(AtOption(a, "", a)); s.state = 1
                }
                "mine" -> {
                    val words = s.query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
                    val hits = withContext(Dispatchers.Default) {
                        notes().filter { it.id != selfId && it.title.isNotBlank() }
                            .map { n -> val t = n.searchText().lowercase(); n to words.count { it in t } + (if (words.any { it in n.title.lowercase() }) 2 else 0) }
                            .filter { it.second > 0 }.sortedByDescending { it.second }.take(3).map { it.first }
                    }
                    s.options = hits.map { AtOption(it.title, it.blocks.firstNotNullOfOrNull { b -> b.body.takeIf { x -> x.isNotBlank() } }.orEmpty().replace('\n', ' ').take(70), "[[${it.title}]]") }
                }
                "read" -> s.options = searchBooks(s.query).take(3).map(::fromCand)
                "watch" -> s.options = searchFilmsAndShows(s.query).take(4).map(::fromCand)
                "listen" -> s.options = searchTracks(s.query).take(3).map(::fromCand)
            }
            if (s.state == 0) { s.state = if (s.options.isEmpty()) 2 else 1; if (s.options.isEmpty()) s.message = "Nothing found for that." }
        } catch (e: Throwable) { s.state = 3; s.message = "Could not look that up just now." }
    }

    private fun fromCand(c: com.b72.keep_canvas.data.Cand) = AtOption(c.title, c.sub, c.title + (if (c.sub.isNotBlank()) " — " + c.sub.replace(" · ", ", ") else ""))

    companion object {
        val COMMANDS = listOf("look" to "a fact, date or source", "mine" to "one of your notes", "read" to "a book", "watch" to "a film or show", "listen" to "a song")
    }
}

/** The cards for requests in flight or answered, and the words that complete a half-typed `@`. Sits over the keyboard. */
@Composable
fun AtBar(h: AtHelper) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        h.hint?.let { (_, prefix) ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((cmd, what) in AtHelper.COMMANDS.filter { it.first.startsWith(prefix.lowercase()) }) AssistChip(onClick = { h.complete(cmd) }, label = { Text("@$cmd") })
            }
        }
        for (s in h.sugs.toList().take(2)) {
            Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHighest, tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("@${s.cmd}  ${s.query}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { h.dismiss(s) }, Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, "Dismiss", Modifier.size(18.dp)) }
                    }
                    when (s.state) {
                        0 -> Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("  Looking… keep writing", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        1 -> for (o in s.options) Row(
                            Modifier.fillMaxWidth().clickable { h.accept(s, o) }.padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(o.label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (o.sub.isNotBlank()) Text(o.sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Rounded.Add, "Use this", Modifier.padding(start = 10.dp).size(22.dp), tint = cs.primary)
                        }
                        else -> Text(s.message, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp, end = 12.dp))
                    }
                }
            }
        }
    }
}
