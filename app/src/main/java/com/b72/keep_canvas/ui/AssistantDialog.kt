@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

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

/** Switch the optional on-device assistant on: download its model (about 1.6 GB), use a file you already have, or remove it. */
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
        title = { Text("On-device assistant") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "A small language model that runs on this phone, with nothing sent anywhere. It adds a one-line summary and suggested tags to your notes, " +
                        "picks out the figure that matters in smart layouts (any kind of data, not just the usual ones) and reads tickets more reliably. " +
                        "Without it everything still works.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                when {
                    Assistant.progress >= 0f -> {
                        LinearWavyProgressIndicator(progress = { Assistant.progress }, modifier = Modifier.fillMaxWidth())
                        Text("Downloading… ${(Assistant.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    }
                    importing -> Text("Copying the model…", style = MaterialTheme.typography.labelLarge)
                    Assistant.local -> {
                        Text(if (Assistant.legacy) "The old, smaller model is installed. Remove it to switch to Gemma 3n E2B, which reads notes and artifacts better." else "Ready. Notes are read in the background when you save them.", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = { Assistant.remove(ctx); note = "Assistant removed" }, modifier = Modifier.padding(top = 12.dp)) { Text("Remove the model") }
                    }
                    else -> {
                        var token by remember { mutableStateOf(Assistant.token(ctx)) }
                        Text("Gemma 3n E2B. Google asks you to accept its licence once, on the model's Hugging Face page (google/gemma-3n-E2B-it-litert-lm); then paste a read token from that account here.", style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.OutlinedTextField(token, { token = it; Assistant.setToken(ctx, it) }, singleLine = true, label = { Text("Hugging Face token") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        Button(onClick = { Assistant.download(ctx, token = token) }) { Text("Download (about 3.4 GB, best on Wi-Fi)") }
                        TextButton(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Use a model file I already have") }
                    }
                }
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
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(gModel == Cloud.FLASH_LITE, { gModel = Cloud.FLASH_LITE; Cloud.save(ctx, gOn, gKey, gModel) }, { Text("Flash-Lite") })
                    androidx.compose.material3.FilterChip(gModel == Cloud.FLASH, { gModel = Cloud.FLASH; Cloud.save(ctx, gOn, gKey, gModel) }, { Text("Flash") })
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Use the cloud assistant", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(gOn && gKey.isNotBlank(), { gOn = it; Cloud.save(ctx, it, gKey, gModel) }, enabled = gKey.isNotBlank())
                }
                val msg = Assistant.message.ifEmpty { note }
                if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
