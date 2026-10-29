@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
            Column {
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
                    Assistant.installed -> {
                        Text("Ready. Notes are read in the background when you save them.", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = { Assistant.remove(ctx); note = "Assistant removed" }, modifier = Modifier.padding(top = 12.dp)) { Text("Remove the model") }
                    }
                    else -> {
                        Button(onClick = { Assistant.download(ctx) }) { Text("Download (about 1.6 GB, best on Wi-Fi)") }
                        TextButton(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Use a model file I already have") }
                    }
                }
                val msg = Assistant.message.ifEmpty { note }
                if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
