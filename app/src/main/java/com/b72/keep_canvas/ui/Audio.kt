@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Repo
import com.b72.keep_canvas.data.dbl
import com.b72.keep_canvas.data.imageDir
import com.b72.keep_canvas.data.newBlock
import com.b72.keep_canvas.data.seedOf
import com.b72.keep_canvas.data.str
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

fun clock(ms: Long): String { val s = (ms / 1000).toInt(); return "%d:%02d".format(s / 60, s % 60) }

/** The bars of a recording (0..100 each), kept in the block as "12,40,…"; made up from the id for a clip that has none. */
fun waveOf(b: Block, n: Int = 48): List<Int> {
    val w = b.str("wave")?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
    if (w.isNotEmpty()) return w
    val sd = seedOf(b.id)
    return List(n) { i -> 18 + ((sd * (i + 3) * 7919) % 60 + 60) % 60 }
}

/** Vertical bars, the played part in [on], the rest in [off]. */
@Composable
fun Wave(bars: List<Int>, progress: Float, on: Color, off: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (bars.isEmpty()) return@Canvas
        val step = size.width / bars.size
        val bw = step * .56f
        bars.forEachIndexed { i, v ->
            val h = (size.height * (v.coerceIn(4, 100) / 100f)).coerceAtLeast(3f)
            drawRoundRect(
                if ((i + .5f) / bars.size <= progress) on else off,
                Offset(i * step + (step - bw) / 2, (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2),
            )
        }
    }
}

/** Records to an .m4a in the photo folder (so backups and the tidy-up treat it like any picture); levels are sampled as it goes. */
class Recorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var t0 = 0L
    val levels = mutableStateListOf<Int>()
    var running by mutableStateOf(false)
        private set
    var ms by mutableLongStateOf(0L)
        private set

    @Suppress("DEPRECATION")
    fun start(): Boolean = runCatching {
        val f = File(imageDir(ctx), "a_${System.nanoTime()}.m4a")
        val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        rec = r; file = f; t0 = System.currentTimeMillis(); levels.clear(); ms = 0; running = true
        true
    }.getOrDefault(false)

    fun poll() {
        val r = rec ?: return
        val a = runCatching { r.maxAmplitude }.getOrDefault(0)
        levels.add((kotlin.math.sqrt(a / 32767.0) * 100).toInt().coerceIn(4, 100))
        ms = System.currentTimeMillis() - t0
    }

    /** Stops and hands back the finished file, its length and a 48-bar picture of it. */
    fun stop(): Triple<File, Long, List<Int>>? {
        val r = rec ?: return null
        val f = file ?: return null
        running = false
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        rec = null
        val len = System.currentTimeMillis() - t0
        if (!ok || len < 600) { runCatching { f.delete() }; return null }
        val src = levels.toList().ifEmpty { listOf(20) }
        val bars = List(48) { i ->
            val a = i * src.size / 48
            val z = ((i + 1) * src.size / 48).coerceAtLeast(a + 1).coerceAtMost(src.size)
            src.subList(a.coerceAtMost(src.size - 1), z).max()
        }
        return Triple(f, len, bars)
    }

    fun cancel() {
        val r = rec ?: return
        running = false
        runCatching { r.stop() }; runCatching { r.release() }
        rec = null
        file?.let { runCatching { it.delete() } }
    }
}

/** The recording page of the plus menu: one big button, a timer and the level as you speak. */
@Composable
fun RecordPane(repo: Repo, noteId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val rec = remember { Recorder(ctx) }
    var denied by remember { mutableStateOf(false) }
    fun begin() { if (!rec.start()) denied = true }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) begin() else denied = true }
    LaunchedEffect(rec.running) { while (rec.running) { delay(80); rec.poll() } }
    DisposableEffect(Unit) { onDispose { rec.cancel() } }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(clock(rec.ms), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 40.sp, color = if (rec.running) cs.primary else cs.onSurface))
        Wave(
            if (rec.running) rec.levels.takeLast(40).let { l -> List(40 - l.size) { 6 } + l } else List(40) { 6 },
            1f, cs.primary, cs.outlineVariant, Modifier.fillMaxWidth().height(72.dp).padding(vertical = 8.dp),
        )
        FilledIconButton(
            onClick = {
                if (rec.running) {
                    rec.stop()?.let { (f, len, bars) ->
                        repo.appendBlock(
                            noteId,
                            newBlock(Kind.Audio, "au${System.nanoTime()}", title = "Voice note", path = f.absolutePath)
                                .let { it.copy(data = it.data + mapOf("dur" to JsonPrimitive(len), "wave" to JsonPrimitive(bars.joinToString(",")))) },
                        )
                    }
                    onClose()
                } else if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                else ask.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.size(84.dp),
            shape = CircleShape,
        ) { Icon(if (rec.running) Icons.Rounded.Stop else Icons.Rounded.Mic, if (rec.running) "Stop and add" else "Start recording", Modifier.size(36.dp)) }
        Text(
            if (denied) "Microphone access is off. Allow it in the phone's app settings." else if (rec.running) "Tap to stop and add" else "Tap to record",
            style = MaterialTheme.typography.bodyMedium, color = if (denied) cs.error else cs.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
    }
}

/** A recording on the note page: play, the bars as a scrubber, the length, and a way to remove it. */
@Composable
fun AudioPlayer(b: Block, pal: NotePalette, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableFloatStateOf(0f) }
    val dur = (b.dbl("dur") ?: 0.0).toLong()
    val bars = remember(b.id, b.data) { waveOf(b) }
    DisposableEffect(b.path) { onDispose { runCatching { player?.release() }; player = null } }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player ?: break
            val d = p.duration.coerceAtLeast(1)
            pos = p.currentPosition.toFloat() / d
            delay(60)
        }
    }
    fun toggle() {
        val p = player ?: MediaPlayer().apply {
            setDataSource(b.path)
            prepare()
            setOnCompletionListener { playing = false; pos = 0f; runCatching { seekTo(0) } }
            player = this
        }
        if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(pal.primary.copy(alpha = .16f)).padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(onClick = { runCatching { toggle() } }, modifier = Modifier.size(52.dp), shape = CircleShape) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play")
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Wave(
                bars, pos, pal.primary, pal.fg.copy(alpha = .25f),
                Modifier.fillMaxWidth().height(40.dp).pointerInput(Unit) {
                    detectTapGestures { o ->
                        val f = (o.x / size.width).coerceIn(0f, 1f)
                        player?.let { it.seekTo((it.duration * f).toInt()); pos = f }
                    }
                },
            )
            Text(
                "${clock((pos * dur).toLong())} / ${clock(dur)}",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = pal.fg.copy(alpha = .65f)),
            )
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove recording", tint = pal.fg.copy(alpha = .6f)) }
    }
}
