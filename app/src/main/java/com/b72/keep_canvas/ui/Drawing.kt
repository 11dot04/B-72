package com.b72.keep_canvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.data.imageDir
import com.b72.keep_canvas.data.newBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

private class Line(val pts: List<Offset>, val color: Color, val width: Float)

private fun smooth(pts: List<Offset>): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) { lineTo(pts[0].x + .1f, pts[0].y); return@apply }
    for (i in 1 until pts.size) {
        val a = pts[i - 1]; val b = pts[i]
        quadraticTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }
    lineTo(pts.last().x, pts.last().y)
}

/**
 * A small sheet to draw on with a finger. Keeping it saves the drawing as a picture and adds it to the note like any photo, so it sits in the
 * same carousel, can be opened full screen and goes into a backup with the rest.
 */
@Composable
fun DrawPane(repo: Repo, noteId: String, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dens = LocalDensity.current
    val lines = remember { mutableStateListOf<Line>() }
    val undone = remember { mutableStateListOf<Line>() }
    var cur by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val inks = listOf(cs.onSurface, cs.primary, cs.error, cs.tertiary, cs.secondary)
    var ink by remember { mutableStateOf(0) }
    var thick by remember { mutableStateOf(1) }
    val widthsDp = listOf(3f, 7f, 16f)
    var box by remember { mutableStateOf(IntSize.Zero) }
    val paper = cs.surfaceContainerLowest
    val inkNow by rememberUpdatedState(inks[ink])
    val wNow by rememberUpdatedState(with(dens) { widthsDp[thick].dp.toPx() })
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(24.dp)).background(paper).border(1.dp, cs.outlineVariant, RoundedCornerShape(24.dp))
                .onSizeChanged { box = it }
                .pointerInput(Unit) { detectTapGestures { p -> lines.add(Line(listOf(p), inkNow, wNow)); undone.clear() } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { cur = listOf(it) },
                        onDrag = { ch, _ -> cur = cur + ch.position; ch.consume() },
                        onDragEnd = { if (cur.isNotEmpty()) { lines.add(Line(cur, inkNow, wNow)); undone.clear() }; cur = emptyList() },
                        onDragCancel = { cur = emptyList() },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(320.dp)) {
                for (l in lines) drawPath(smooth(l.pts), l.color, style = Stroke(l.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (cur.isNotEmpty()) drawPath(smooth(cur), inkNow, style = Stroke(wNow, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (lines.isEmpty() && cur.isEmpty()) Text("Draw here", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            inks.forEachIndexed { i, c ->
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(c).border(if (i == ink) 3.dp else 0.dp, cs.secondaryContainer, CircleShape).clickable(onClickLabel = "Ink") { ink = i },
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton({ lines.removeLastOrNull()?.let { undone.add(it) } }, enabled = lines.isNotEmpty()) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
            IconButton({ undone.removeLastOrNull()?.let { lines.add(it) } }, enabled = undone.isNotEmpty()) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            widthsDp.forEachIndexed { i, w ->
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (i == thick) cs.secondaryContainer else Color.Transparent).clickable(onClickLabel = "Pen size") { thick = i },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size((w + 4f).dp).clip(CircleShape).background(cs.onSurface)) }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (lines.isEmpty() || saving || box.width == 0) return@Button
                    saving = true
                    val snapshot = lines.toList(); val w = box.width; val h = box.height; val bg = paper.toArgb()
                    scope.launch {
                        val block = withContext(Dispatchers.IO) {
                            runCatching {
                                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                                val cv = android.graphics.Canvas(bmp)
                                cv.drawColor(bg)
                                for (l in snapshot) {
                                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                        style = android.graphics.Paint.Style.STROKE; strokeWidth = l.width; color = l.color.toArgb()
                                        strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
                                    }
                                    cv.drawPath(smooth(l.pts).asAndroidPath(), p)
                                }
                                val f = java.io.File(imageDir(ctx), "${System.currentTimeMillis()}_draw.png")
                                f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                newBlock(Kind.Photo, "d${System.nanoTime()}", path = f.path, aspect = (h.toDouble() / w).coerceIn(.6, 1.4))
                                    .let { b -> b.copy(data = b.data + mapOf("ocr" to JsonPrimitive(""), "ocrV" to JsonPrimitive(com.b72.keep_canvas.data.OCR_V), "lbl" to JsonPrimitive("drawing"), "cutV" to JsonPrimitive(99.0))) }
                            }.getOrNull()
                        }
                        block?.let { repo.appendBlock(noteId, it) }
                        onClose()
                    }
                },
                enabled = lines.isNotEmpty() && !saving,
            ) { Text("Keep") }
        }
    }
}
