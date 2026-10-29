package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Saves [value] [ms] after the last keystroke, and straight away when the field leaves the screen. */
@Composable
private fun SaveSoon(value: String, saved: String, ms: Long = 700, onSave: (String) -> Unit) {
    val latest by rememberUpdatedState(value)
    val savedNow by rememberUpdatedState(saved)
    val cb by rememberUpdatedState(onSave)
    LaunchedEffect(value) { if (value != saved) { delay(ms); cb(value) } }
    DisposableEffect(Unit) { onDispose { if (latest != savedNow) cb(latest) } }
}

/** The title as a field: big, in the page's colours. Leaves edit mode when it loses focus with something in it. */
@Composable
fun InlineTitle(initial: String, placeholder: String, pal: NotePalette, focus: Boolean, onDone: () -> Unit, onChange: (String) -> Unit) {
    var tv by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val fr = remember { FocusRequester() }
    var had by remember { mutableStateOf(false) }
    SaveSoon(tv.text, initial, onSave = onChange)
    LaunchedEffect(Unit) { if (focus) runCatching { fr.requestFocus() } }
    val style = flexStyle(40.sp, wght = 700, rond = 40f, lh = 1.05f).copy(color = pal.fg)
    Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        if (tv.text.isEmpty()) Text(placeholder, style = style, color = pal.fg.copy(alpha = .35f), maxLines = 2)
        BasicTextField(
            tv, { tv = it }, Modifier.fillMaxWidth().focusRequester(fr).onFocusChanged { if (it.isFocused) had = true else if (had && tv.text.isNotEmpty()) onDone() },
            textStyle = style, cursorBrush = SolidColor(pal.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        )
    }
}

/** A block of words edited in place. With links in it, it reads as live text until you tap it. */
@Composable
fun InlineBody(
    body: String, placeholder: String, pal: NotePalette, linked: AnnotatedString, focus: Boolean, onChange: (String) -> Unit,
) {
    var tv by remember { mutableStateOf(TextFieldValue(body, TextRange(body.length))) }
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val links = remember(linked) { linked.getLinkAnnotations(0, linked.length).isNotEmpty() }
    var editing by remember { mutableStateOf(!links) }
    SaveSoon(tv.text, body, onSave = onChange)
    LaunchedEffect(body) { if (!focused && tv.text != body) tv = TextFieldValue(body, TextRange(body.length)) }
    LaunchedEffect(Unit) { if (focus) runCatching { fr.requestFocus() } }
    LaunchedEffect(editing) { if (editing && links && !focused) runCatching { fr.requestFocus() } }
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp, color = pal.fg)
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(bottom = 12.dp)) {
        if (!editing && links && tv.text.isNotEmpty()) {
            Text(
                linked, style = style,
                modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { editing = true },
            )
        } else {
            if (tv.text.isEmpty()) Text(placeholder, style = style, color = pal.fg.copy(alpha = .4f))
            BasicTextField(
                tv, { tv = it },
                Modifier.fillMaxWidth().focusRequester(fr).onFocusChanged { focused = it.isFocused; if (!it.isFocused && links) editing = false },
                textStyle = style, cursorBrush = SolidColor(pal.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
}

/** A checklist edited in place: tick a row, type in it, Enter adds the next, Backspace on an empty row removes it. */
@Composable
fun InlineChecklist(items0: List<String>, pal: NotePalette, focus: Boolean, onItems: (List<String>) -> Unit) {
    val items = remember { mutableStateListOf<String>().also { it.addAll(items0.ifEmpty { listOf("") }) } }
    val saved = remember(items0) { items0.joinToString("\n") }
    val now = items.filter { it.isNotBlank() }.joinToString("\n")
    SaveSoon(now, saved, ms = 500) { onItems(items.toList()) }
    val focusers = remember { mutableMapOf<Int, FocusRequester>() }
    var want by remember { mutableStateOf<Int?>(if (focus) 0 else null) }
    LaunchedEffect(want) {
        val w = want ?: return@LaunchedEffect
        delay(30)
        focusers[w]?.let { runCatching { it.requestFocus() } }
        want = null
    }
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, color = pal.fg)
    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, s ->
            val done = s.startsWith("x ")
            val fr = focusers.getOrPut(i) { FocusRequester() }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClickLabel = if (done) "Untick" else "Tick") {
                        if (s.isNotBlank()) items[i] = if (done) s.substring(2) else "x $s"
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (done) pal.primary else pal.fg) }
                Spacer(Modifier.width(6.dp))
                if (done) ScribbleText(s.substring(2), true, pal.fg, pal.primary, style, Modifier.weight(1f))
                else Box(Modifier.weight(1f)) {
                    if (s.isEmpty() && i == items.lastIndex) Text("List item", style = style, color = pal.fg.copy(alpha = .4f))
                    BasicTextField(
                        s, { v ->
                            if (v.contains('\n')) {
                                val parts = v.split('\n')
                                items[i] = parts.first()
                                items.add(i + 1, parts.drop(1).joinToString(" "))
                                want = i + 1
                            } else items[i] = v
                        },
                        Modifier.fillMaxWidth().focusRequester(fr).onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace && s.isEmpty() && items.size > 1) {
                                items.removeAt(i); want = (i - 1).coerceAtLeast(0); true
                            } else false
                        },
                        textStyle = style, cursorBrush = SolidColor(pal.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { items.add(i + 1, ""); want = i + 1 }),
                    )
                }
                if (s.isNotEmpty() && items.size > 1) Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Remove item") { items.removeAt(i) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = pal.fg.copy(alpha = .5f)) }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { items.add(""); want = items.lastIndex }.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(22.dp), tint = pal.fg.copy(alpha = .6f))
            Spacer(Modifier.width(14.dp))
            Text("Add item", style = style, color = pal.fg.copy(alpha = .6f))
        }
    }
}
