@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.b72.keep_canvas.data.Assistant
import com.b72.keep_canvas.data.Cloud
import kotlinx.coroutines.launch

/** The assistant: where it gets its answers from (the cloud, Gemini Nano on a phone that has it, or a model kept on the phone), and a check of what this phone can do. */
@Composable
fun AssistantDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Assistant.refresh(ctx) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            note = if (Assistant.import(ctx, uri)) "Assistant ready" else "That file could not be used"
            importing = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assistant") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "A language model that adds a one-line summary and suggested tags to your notes, picks out the figure that matters in smart layouts " +
                        "and reads tickets more reliably. It answers from the first of these that is available: the cloud assistant (if you switch it on below), " +
                        "Gemini Nano (if this phone has it), or a model kept on the phone. Without any of them everything still works.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text("This phone", style = MaterialTheme.typography.titleSmall)
                val nanoText = when (Assistant.nanoState) {
                    1 -> "Gemini Nano: ready. Used when the app is open; nothing is downloaded into B-72."
                    2 -> "Gemini Nano: this phone supports it, but the system has not downloaded it yet."
                    3 -> "Gemini Nano: downloading…"
                    4 -> "Gemini Nano: not available on this phone (it needs a supported device, a locked bootloader and an up-to-date AICore)."
                    else -> "Gemini Nano: not checked yet."
                }
                Text(nanoText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Assistant.nanoState == 2) Button(onClick = { Assistant.downloadNano() }) { Text("Download Gemini Nano") }
                    TextButton(onClick = { scope.launch { Assistant.checkNano() } }) { Text("Check again") }
                }
                Text(
                    "Reading text in photos, labelling, cut-outs, dates and addresses in notes, and handwriting work on any phone. They fetch small models from Google the first time.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                Text("A model kept on this phone", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                when {
                    Assistant.progress >= 0f -> {
                        LinearWavyProgressIndicator(progress = { Assistant.progress }, modifier = Modifier.fillMaxWidth())
                        Text("Downloading… ${(Assistant.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    }
                    importing -> Text("Copying the model…", style = MaterialTheme.typography.labelLarge)
                    Assistant.local -> {
                        Text(if (Assistant.legacy) "An old model is installed. Remove it to choose one of the Gemma models." else "${Assistant.modelName}: ready. Notes are read in the background when you save them.", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = { Assistant.remove(ctx); note = "Assistant removed" }, modifier = Modifier.padding(top = 12.dp)) { Text("Remove the model") }
                    }
                    else -> {
                        var token by remember { mutableStateOf(Assistant.token(ctx)) }
                        Text("Google asks you to accept the Gemma licence once on a model's Hugging Face page (for example litert-community/Gemma3-1B-IT); then paste a read token from that account here. One token works for both models.", style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.OutlinedTextField(token, { token = it; Assistant.setToken(ctx, it) }, singleLine = true, label = { Text("Hugging Face token") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        for (m in Assistant.MODELS) {
                            Text("${m.name}  ·  ${m.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                            Text(m.blurb, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { Assistant.download(ctx, m, token) }, modifier = Modifier.padding(top = 4.dp)) { Text("Download (best on Wi-Fi)") }
                        }
                        TextButton(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Use a model file I already have") }
                    }
                }
                Spacer(Modifier.height(20.dp))
                var readMode by remember { mutableStateOf(Cloud.readMode(ctx)) }
                Text("When notes are read", style = MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("As I write", "When I leave", "On request").forEachIndexed { i, l ->
                        androidx.compose.material3.FilterChip(readMode == i, { readMode = i; Cloud.setReadMode(ctx, i) }, { Text(l) })
                    }
                }
                Text(
                    "Reading as you write suits the model on the phone. The cloud assistant has a daily allowance, so it is kinder to read a note once, when you leave it, " +
                        "or only when you pick “Summarise and tag this note” in the note's menu.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(20.dp))
                var gOn by remember { mutableStateOf(Cloud.wanted(ctx)) }
                var gKey by remember { mutableStateOf(Cloud.key(ctx)) }
                var gModel by remember { mutableStateOf(Cloud.model) }
                Text("Cloud assistant (Gemini)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Faster and sharper than the model on the phone, and nothing to download. It uses your own Google AI key, and while it is on the text of the notes it reads " +
                        "is sent to Google. Off by default; when on it is used instead of the model on the phone.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
                )
                androidx.compose.material3.OutlinedTextField(
                    gKey, { gKey = it; Cloud.save(ctx, gOn, it, gModel) }, singleLine = true, label = { Text("Google AI API key") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(gModel == Cloud.FLASH_LITE, { gModel = Cloud.FLASH_LITE; Cloud.save(ctx, gOn, gKey, gModel) }, { Text("Flash-Lite") })
                    androidx.compose.material3.FilterChip(gModel == Cloud.FLASH, { gModel = Cloud.FLASH; Cloud.save(ctx, gOn, gKey, gModel) }, { Text("Flash") })
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Use the cloud assistant", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(gOn && gKey.isNotBlank(), { gOn = it; Cloud.save(ctx, it, gKey, gModel) }, enabled = gKey.isNotBlank())
                }
                if (Cloud.on) Text("Cloud requests today: ${Cloud.usedToday(ctx)} of ${Cloud.cap}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                val msg = Assistant.message.ifEmpty { note }
                if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
