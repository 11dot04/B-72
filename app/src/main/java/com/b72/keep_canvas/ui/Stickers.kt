@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.Loaded
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.data.imageDir
import com.b72.keep_canvas.data.makeStickers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/** Photos in, die-cut stickers out: every subject the on-device model finds becomes a sticker with a white outline. */
@Composable
fun StickersScreen(repo: Repo, loaded: Loaded?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<String?>(null) }
    val paths = remember(loaded?.library?.stickers) {
        loaded?.library?.stickers.orEmpty().mapNotNull { ((it as? JsonObject)?.get("p") as? JsonPrimitive)?.content }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            var made = 0
            var unavailable = false
            var stamps = 0
            for (u in uris) {
                val tmp = repo.importPhoto(u, "stk${System.nanoTime()}") ?: continue
                val r = makeStickers(tmp.path!!, imageDir(ctx))
                if (r == null) unavailable = true
                else if (r.isEmpty()) com.b72.keep_canvas.data.makeStamp(tmp.path!!, imageDir(ctx))?.let { repo.addStickers(listOf(it)); stamps++ }
                else { repo.addStickers(r); made += r.size }
                runCatching { File(tmp.path).delete() }
            }
            busy = false
            snack.showSnackbar(
                when {
                    made > 0 || stamps > 0 -> listOfNotNull(if (made > 0) "$made sticker${if (made == 1) "" else "s"}" else null, if (stamps > 0) "$stamps stamp${if (stamps == 1) "" else "s"}" else null).joinToString(" and ").let { "Made $it" }
                    unavailable -> "The cut-out model is not ready yet. If this is the first run it may still be downloading: try again in a minute."
                    else -> "No clear subject found."
                },
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
                Text("Artifacts", style = flexStyle(36.sp, wght = 720, wdth = 108f, rond = 40f), modifier = Modifier.padding(start = 12.dp))
            }
            Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) LoadingIndicator(Modifier.size(28.dp)) else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Making artifacts…" else "Make artifacts from photos")
            }
            if (paths.isEmpty() && !busy) {
                Text(
                    "Pick some photos. A clear subject becomes a die-cut sticker; a photo without one becomes a postage stamp.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3), modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
            ) {
                items(paths, key = { it }) { p ->
                    val img by produceState<ImageBitmap?>(null, p) { value = Images.load(p, 400) }
                    Box(Modifier.aspectRatio(1f).clickable(onClickLabel = "View artifact") { view = p }, contentAlignment = Alignment.Center) {
                        img?.let { Image(it, "Sticker", Modifier.fillMaxSize().rotate(((p.hashCode() % 7) - 3) * 1.7f), contentScale = ContentScale.Fit) }
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    view?.let { p ->
        val img by produceState<ImageBitmap?>(null, p) { value = Images.load(p, 900) }
        AlertDialog(
            onDismissRequest = { view = null },
            text = { Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) { img?.let { Image(it, "Sticker", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } } },
            confirmButton = { TextButton(onClick = { view = null }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { repo.removeSticker(p); view = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        )
    }
}
