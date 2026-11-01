@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Idea

private fun iconFor(kind: String) = when (kind) {
    "shop" -> Icons.Rounded.ShoppingBag
    "style" -> Icons.Rounded.Checkroom
    "recipe" -> Icons.Rounded.Restaurant
    "care" -> Icons.Rounded.Eco
    "review" -> Icons.Rounded.Search
    "similar" -> Icons.Rounded.Palette
    "tag" -> Icons.Rounded.Tag
    else -> Icons.Rounded.AutoAwesome
}

/**
 * What the assistant noticed in this note's photos, as cards that each do one thing. It works from labels worked out on the
 * phone; a search opens only when you tap Open, and photos of people, documents or IDs are never given suggestions.
 */
@Composable
fun IdeasStrip(ideas: List<Idea>, fg: Color, onAdd: (Idea) -> Unit, onDismiss: (Idea) -> Unit, modifier: Modifier = Modifier) {
    if (ideas.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    Column(modifier.fillMaxWidth()) {
        Text("IDEAS", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = .8.sp), color = fg.copy(alpha = .65f))
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((n, i) in ideas.withIndex()) {
                val shape = listOf(MaterialShapes.Cookie6Sided, MaterialShapes.Flower, MaterialShapes.Gem, MaterialShapes.Clover4Leaf, MaterialShapes.Bun)[n % 5].toShape()
                Column(Modifier.width(250.dp).clip(RoundedCornerShape(28.dp)).background(if (n % 2 == 0) cs.secondaryContainer else cs.tertiaryContainer).padding(16.dp)) {
                    val ink = if (n % 2 == 0) cs.onSecondaryContainer else cs.onTertiaryContainer
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(40.dp).clip(shape).background(ink.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                            Icon(iconFor(i.kind), null, Modifier.size(20.dp), tint = ink)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton({ onDismiss(i) }, Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Not interested", Modifier.size(18.dp), tint = ink.copy(alpha = .7f)) }
                    }
                    Text(i.title, style = MaterialTheme.typography.titleMedium, color = ink, modifier = Modifier.padding(top = 10.dp), maxLines = 2)
                    if (i.detail.isNotBlank()) Text(i.detail, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = .8f), modifier = Modifier.padding(top = 2.dp), maxLines = 2)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (i.url.isNotBlank()) FilledTonalButton({ runCatching { uri.openUri(i.url) } }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Open")
                        }
                        TextButton({ onAdd(i) }) { Text(if (i.kind == "tag") "Add tag" else "Add to note") }
                    }
                }
            }
        }
        Text("Worked out on your phone. Nothing is searched until you tap Open.", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = .5f), modifier = Modifier.padding(top = 8.dp))
    }
}
