@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import com.b72.keep_canvas.data.CoverOption
import com.b72.keep_canvas.data.bookCovers
import com.b72.keep_canvas.data.imageDir
import com.b72.keep_canvas.data.saveImage
import kotlinx.coroutines.launch

/** Every English edition of a book that has a jacket, with its publisher underneath; tapping one makes it the book's cover. */
@Composable
fun CoverPicker(title: String, author: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val options by produceState<List<CoverOption>?>(null, title, author) { value = bookCovers(title, author) }
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Choose a cover", style = flexStyle(26.sp, wght = 700, lh = 1.1f), modifier = Modifier.padding(bottom = 4.dp))
            Text("Editions of ${title.ifBlank { "this book" }}, by publisher.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            val list = options
            when {
                list == null || saving -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
                list.isEmpty() -> Text("No other jackets turned up for this one.", Modifier.padding(vertical = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyVerticalGrid(GridCells.Fixed(3), Modifier.padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(list, key = { it.url }) { c ->
                        Column(Modifier.clickable {
                            scope.launch { saving = true; saveImage(c.url, imageDir(ctx))?.let(onPick); saving = false; onDismiss() }
                        }) {
                            RemoteCover(c.url)
                            Text(c.label.ifBlank { "Unknown publisher" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A remote picture, loaded by hand because the app carries no image-loading library. */
@Composable
private fun RemoteCover(url: String) {
    val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { java.net.URL(url).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        img?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentScale = ContentScale.Crop) }
    }
}
