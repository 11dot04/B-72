package com.b72.keep_canvas.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.data.newBlock

/** The audio page of the plus menu: record a voice note, or speak and have it written out as text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePane(repo: Repo, noteId: String, onClose: () -> Unit) {
    var mode by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("Record audio", "Dictate to text").forEachIndexed { i, t ->
                SegmentedButton(mode == i, { mode = i }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(t) }
            }
        }
        if (mode == 0) RecordPane(repo, noteId, onClose) else DictatePane(repo, noteId, onClose)
    }
}

/**
 * Speak and watch the words appear. It prefers the phone's offline recogniser, keeps listening through pauses until you tap stop,
 * and adds what it heard as a text block.
 */
@Composable
fun DictatePane(repo: Repo, noteId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var done by remember { mutableStateOf("") }
    var live by remember { mutableStateOf("") }
    var on by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val avail = remember { SpeechRecognizer.isRecognitionAvailable(ctx) }
    val holder = remember { arrayOfNulls<SpeechRecognizer>(1) }
    val state = remember { booleanArrayOf(false) }

    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        runCatching { holder[0]?.startListening(i) }.onFailure { msg = "Speech recognition isn't working on this phone."; on = false; state[0] = false }
    }
    fun first(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    DisposableEffect(Unit) {
        val r = if (avail) SpeechRecognizer.createSpeechRecognizer(ctx) else null
        r?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(p: Bundle?) { live = first(p) }
            override fun onResults(p: Bundle?) {
                val t = first(p)
                if (t.isNotBlank()) done = (done + " " + t).trim()
                live = ""
                if (state[0]) listen()
            }
            override fun onError(e: Int) {
                live = ""
                if (!state[0]) return
                if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) listen()
                else { state[0] = false; on = false; msg = if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) "Microphone access is off. Allow it in the phone's app settings." else "Couldn't listen just now (error $e). Try again." }
            }
        })
        holder[0] = r
        onDispose { state[0] = false; runCatching { r?.cancel() }; runCatching { r?.destroy() }; holder[0] = null }
    }
    fun begin() { msg = null; state[0] = true; on = true; listen() }
    fun stop() { state[0] = false; on = false; runCatching { holder[0]?.stopListening() } }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) begin() else msg = "Microphone access is off. Allow it in the phone's app settings." }

    val text = (done + " " + live).trim()
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 180.dp).verticalScroll(rememberScrollState())) {
            Text(
                text.ifBlank { if (on) "Listening…" else "Tap the mic and talk. Your words show up here." },
                style = MaterialTheme.typography.bodyLarge, color = if (text.isBlank()) cs.onSurfaceVariant else cs.onSurface,
            )
        }
        FilledIconButton(
            onClick = {
                if (on) stop()
                else if (!avail) msg = "This phone has no speech recogniser installed."
                else if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                else ask.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.size(72.dp), shape = CircleShape,
        ) { Icon(if (on) Icons.Rounded.Stop else Icons.Rounded.Mic, if (on) "Stop" else "Start dictating", Modifier.size(32.dp)) }
        msg?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.error) }
        Button(
            enabled = text.isNotBlank(),
            onClick = {
                state[0] = false
                runCatching { holder[0]?.cancel() }
                val t = text.replaceFirstChar { it.uppercase() }
                repo.appendBlock(noteId, newBlock(Kind.Text, "tx${System.nanoTime()}", body = t))
                onClose()
            },
        ) { Text("Add to note") }
    }
}
