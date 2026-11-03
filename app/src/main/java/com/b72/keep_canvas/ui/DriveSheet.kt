@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Backup
import com.b72.keep_canvas.data.Drive
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.data.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Back up to, and restore from, the person's own Google Drive. The backup is encrypted on the phone with a passphrase first,
 * and lives in the app's private Drive folder (it does not show up among their files). Everything, photos included.
 */
@Composable
fun DriveSheet(repo: Repo, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var last by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }

    fun work(action: String, token: String) {
        busy = true; msg = if (action == "backup") "Packing and encrypting…" else "Downloading…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(ctx.cacheDir, "drive").apply { mkdirs() }
                    if (action == "backup") {
                        val lib = repo.loaded?.library ?: error("Nothing loaded yet")
                        val zip = File(tmp, "plain.zip"); val enc = File(tmp, "backup.b72e")
                        zip.outputStream().use { Backup.export(lib, it) }
                        zip.inputStream().use { i -> enc.outputStream().buffered().use { o -> Vault.encrypt(pass, i, o) } }
                        zip.delete()
                        withContext(Dispatchers.Main) { msg = "Uploading…" }
                        Drive.upload(token, enc)
                        enc.delete()
                        "Backed up to your Drive."
                    } else {
                        val r = Drive.list(token).firstOrNull() ?: return@runCatching "There is no backup in your Drive yet."
                        val enc = File(tmp, "down.b72e"); val zip = File(tmp, "down.zip")
                        Drive.download(token, r.id, enc)
                        withContext(Dispatchers.Main) { msg = "Decrypting…" }
                        val ok = enc.inputStream().buffered().use { i -> zip.outputStream().buffered().use { o -> Vault.decrypt(pass, i, o) } }
                        enc.delete()
                        if (!ok) { zip.delete(); return@runCatching "Wrong passphrase, or the backup is damaged." }
                        val lib = zip.inputStream().use { Backup.read(ctx, it) }
                        zip.delete()
                        if (lib == null) "That backup could not be read." else withContext(Dispatchers.Main) { "Restored ${repo.merge(lib)} new notes." }
                    }
                }.getOrElse { "Something went wrong: ${it.message}" }
            }
            msg = result; busy = false
            if (action == "backup" && result.startsWith("Backed")) last = "just now"
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val t = Drive.tokenFrom(ctx, r.data)
        val a = pending
        if (t != null && a != null) work(a, t) else { msg = "Google did not give access."; busy = false }
    }
    fun run(action: String) {
        if (pass.length < 6) { msg = "Choose a passphrase of at least 6 characters. You will need the same one to restore."; return }
        pending = action; busy = true; msg = "Signing in to Google…"
        Drive.authorize(ctx, { consent.launch(IntentSenderRequest.Builder(it).build()) }, { work(action, it) }, { msg = it; busy = false })
    }
    LaunchedEffect(Unit) {
        Drive.authorize(ctx, {}, { t -> scope.launch(Dispatchers.IO) { runCatching { Drive.list(t).firstOrNull()?.modified?.take(10) }.getOrNull()?.let { last = it } } }, {})
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onClose() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Drive backup", style = flexStyle(32.sp, wght = 720, wdth = 108f, rond = 40f), color = cs.onSurface)
            Text(
                "Your notes and photos, encrypted on this phone with a passphrase before they go to a private folder in your Google Drive. " +
                    "Google never sees the passphrase, and neither do we. If you forget it, the backup cannot be opened.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
            last?.let { Text("Latest backup: $it", style = MaterialTheme.typography.labelLarge, color = cs.primary) }
            OutlinedTextField(
                pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("Passphrase") }, singleLine = true, enabled = !busy,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { run("backup") }, enabled = !busy) { Text("Back up now") }
                FilledTonalButton(onClick = { run("restore") }, enabled = !busy) { Text("Restore") }
                if (busy) LoadingIndicator(Modifier.size(40.dp))
            }
            if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
        }
    }
}
