package com.b72.keep_canvas.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.b72.keep_canvas.BuildConfig

/**
 * What a debug build can tell you about itself: how smooth the frames are, how often the main screens redraw, how long a note takes to open,
 * and what the AI has used. Only debug builds show it (Settings > Developer insights); a release build carries no overlay.
 */
object Insights {
    var shown by mutableStateOf(false)

    private val hits = HashMap<String, Int>()
    fun hit(name: String) { hits[name] = (hits[name] ?: 0) + 1 }
    internal fun hitsNow() = hits.toMap()

    private var tapAt = 0L
    var firstFrameMs by mutableStateOf(-1L); private set
    var bodyMs by mutableStateOf(-1L); private set
    fun tapped() { tapAt = SystemClock.elapsedRealtime(); firstFrameMs = -1; bodyMs = -1 }
    fun pageFrame() { if (tapAt != 0L && firstFrameMs < 0) firstFrameMs = SystemClock.elapsedRealtime() - tapAt }
    fun pageBody() { if (tapAt != 0L && bodyMs < 0) { bodyMs = SystemClock.elapsedRealtime() - tapAt; Log.d("B72Insights", "note open: first frame ${firstFrameMs}ms, body ${bodyMs}ms") } }

    var fps by mutableStateOf(0); private set
    var worst by mutableStateOf(0); private set
    var slow by mutableStateOf(0); private set
    internal fun frames(dtMs: Float) { frameCount++; if (dtMs > 24f) { slowCount++; Log.d("B72Insights", "slow frame ${dtMs.toInt()}ms") }; if (dtMs > worstNow) worstNow = dtMs }
    private var frameCount = 0; private var slowCount = 0; private var worstNow = 0f
    internal fun roll(seconds: Float) { fps = (frameCount / seconds).toInt(); slow = slowCount; worst = worstNow.toInt(); frameCount = 0; worstNow = 0f }
    fun resetSlow() { slowCount = 0 }
}

/** Call from a screen's body: counts how many times it was redrawn. */
@Composable
fun Redraws(name: String) { if (BuildConfig.DEBUG) SideEffect { Insights.hit(name) } }

@Composable
fun InsightsOverlay() {
    if (!BuildConfig.DEBUG || !Insights.shown) return
    var tick by androidx.compose.runtime.remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        var last = 0L; var windowStart = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (last != 0L) Insights.frames((t - last) / 1_000_000f)
                last = t
                if (windowStart == 0L) windowStart = t
                if (t - windowStart >= 1_000_000_000L) { Insights.roll((t - windowStart) / 1e9f); windowStart = t; tick++ }
            }
        }
    }
    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this) }
    val h = Insights.hitsNow().also { tick }
    val rt = Runtime.getRuntime()
    val mem = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
    Popup(alignment = Alignment.TopStart, offset = IntOffset(0, top), properties = PopupProperties(focusable = false, dismissOnClickOutside = false)) {
        Column(Modifier.padding(4.dp).background(Color(0xCC000000), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
            val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 11.sp, color = Color(0xFFB8F7C6))
            Text("${Insights.fps} fps · worst ${Insights.worst} ms · slow ${Insights.slow}", style = mono)
            Text("open: frame ${Insights.firstFrameMs} ms · body ${Insights.bodyMs} ms", style = mono)
            Text("redraws app ${h["app"] ?: 0} · home ${h["home"] ?: 0} · page ${h["page"] ?: 0} · text ${h["text"] ?: 0}", style = mono)
            Text("heap $mem MB · debug build", style = mono)
        }
    }
}
