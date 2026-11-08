package com.b72.keep_canvas.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.field
import com.b72.keep_canvas.data.linkKind

// A saved link as a bookmark: where it lives, what it is, who wrote it and when, how long it takes to read, what it costs.

fun linkUrl(b: Block): String = b.body.trim().let { if (it.startsWith("http")) it else "https://$it" }

fun linkHost(b: Block): String = runCatching { java.net.URL(linkUrl(b)).host.removePrefix("www.").removePrefix("m.") }.getOrDefault("")

/** The address without its scheme and host, the part that says which page. */
fun linkPath(b: Block): String = runCatching {
    val u = java.net.URL(linkUrl(b))
    (u.path.trimEnd('/') + (u.query?.let { "?$it" } ?: "")).take(60)
}.getOrDefault("")

fun linkType(b: Block): String = b.field("type").ifBlank { linkKind(linkHost(b)) }

/** Author, date and reading time, whichever the page gave. */
fun linkFacts(b: Block): List<String> = listOf(b.field("author"), b.field("date"), b.field("read")).filter { it.isNotBlank() }

private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = .6.sp)

/** The site's own icon in a circle, or its first letter when it gave none. */
@Composable
fun SiteBadge(b: Block, tint: Color, ink: Color, size: Dp, modifier: Modifier = Modifier) {
    val icon by produceState<ImageBitmap?>(null, b.field("icon")) { value = b.field("icon").takeIf { it.isNotEmpty() }?.let { Images.load(it, 96) } }
    Box(modifier.size(size).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
        val i = icon
        if (i != null) Image(i, null, Modifier.fillMaxSize().padding(size * .16f), contentScale = ContentScale.Fit)
        else Text(linkHost(b).firstOrNull()?.uppercase() ?: "•", style = TextStyle(fontSize = (size.value * .5f).sp, fontWeight = FontWeight.Bold), color = ink)
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Mono, color = fg, maxLines = 1, modifier = modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 5.dp))
}

private fun copyLink(ctx: Context, url: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", url))
    Toast.makeText(ctx, "Link copied", Toast.LENGTH_SHORT).show()
}

private fun shareLink(ctx: Context, url: String, title: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, title), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The bookmark on a note's page: picture, site, kind, title, the facts in a line, the description, the address, and what you can do with it. */
@Composable
fun LinkCard(b: Block, fg: Color, accent: Color, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val url = linkUrl(b)
    val site = b.field("site").ifBlank { linkHost(b) }
    val facts = linkFacts(b)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(fg.copy(alpha = .09f))) {
        Box(Modifier.fillMaxWidth()) {
            if (b.path != null) PhotoImage(b.path!!, 1000, false, accent, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            else Box(Modifier.fillMaxWidth().height(96.dp).background(fg.copy(alpha = .07f)), contentAlignment = Alignment.Center) { SiteBadge(b, fg.copy(alpha = .14f), fg, 52.dp) }
            Row(Modifier.align(Alignment.BottomStart).padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)).padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                SiteBadge(b, Color.White.copy(alpha = .9f), Color.Black, 22.dp)
                Text(site, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
            Pill(linkType(b), Color.Black.copy(alpha = .55f), Color.White, Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        Column(Modifier.padding(18.dp)) {
            if (b.title.isNotEmpty()) Text(b.title, style = flexStyle(24.sp, wght = 720, rond = 30f, lh = 1.1f), color = fg)
            if (facts.isNotEmpty()) Text(facts.joinToString("  ·  ").uppercase(), style = Mono, color = fg.copy(alpha = .7f), modifier = Modifier.padding(top = 8.dp))
            if (b.field("price").isNotEmpty()) Text(b.field("price"), style = flexStyle(26.sp, wght = 760, rond = 30f, lh = 1f), color = fg, modifier = Modifier.padding(top = 10.dp))
            if (b.field("stats").isNotEmpty()) StatGrid(b.field("stats"), b.field("src"), fg)
            if (b.field("desc").isNotEmpty() && b.field("stats").isEmpty()) Text(b.field("desc"), maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp), color = fg.copy(alpha = .82f), modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(linkHost(b), style = Mono.copy(fontSize = 11.sp), color = fg.copy(alpha = .85f), maxLines = 1)
                Text(linkPath(b), style = Mono.copy(fontSize = 11.sp), color = fg.copy(alpha = .5f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { runCatching { uri.openUri(url) } }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("OPEN")
                }
                Spacer(Modifier.weight(1f))
                IconButton({ copyLink(ctx, url) }) { Icon(Icons.Rounded.ContentCopy, "Copy the link", tint = fg) }
                IconButton({ shareLink(ctx, url, b.title) }) { Icon(Icons.Rounded.Share, "Share the link", tint = fg) }
            }
            if (b.field("note").isNotEmpty()) Text(b.field("note"), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp), color = fg, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** One bookmark in the list on the Links shelf: its picture, the site, the title, a line of what it says, and the facts. */
@Composable
fun LinkRow(note: Note, b: Block, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val site = b.field("site").ifBlank { linkHost(b) }
    val facts = (listOf(linkType(b)) + linkFacts(b) + listOfNotNull(b.field("price").takeIf { it.isNotEmpty() })).filter { it.isNotBlank() }
    Row(
        modifier.fillMaxWidth().noteContainer(note.id, 26.dp).clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainerHigh).clickable(onClickLabel = "Open the note", onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(100.dp).clip(RoundedCornerShape(18.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
            if (b.path != null) PhotoImage(b.path!!, 400, false, cs.primary, Modifier.fillMaxSize())
            else SiteBadge(b, cs.surface, cs.onSurface, 44.dp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SiteBadge(b, cs.surface, cs.onSurface, 16.dp)
                Text(site.uppercase(), style = Mono, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp).weight(1f))
            }
            Text(b.title.ifBlank { linkHost(b) + linkPath(b) }, style = flexStyle(19.sp, wght = 560, lh = 1.1f), color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            val about = b.field("desc").ifBlank { note.snippet(120) }
            if (about.isNotBlank()) Text(about, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Text(facts.joinToString("  ·  ").uppercase(), style = Mono, color = cs.onSurfaceVariant.copy(alpha = .8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
        IconButton({ runCatching { uri.openUri(linkUrl(b)) } }, Modifier.size(36.dp)) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open in the browser", Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
    }
}
