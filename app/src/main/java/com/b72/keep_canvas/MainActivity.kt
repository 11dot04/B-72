package com.b72.keep_canvas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.b72.keep_canvas.data.Launch
import com.b72.keep_canvas.data.Share
import com.b72.keep_canvas.data.toLaunch
import com.b72.keep_canvas.data.toShare
import com.b72.keep_canvas.ui.App
import com.b72.keep_canvas.ui.B72Theme

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Share?>(null)
    private var launch by mutableStateOf<Launch?>(null)
    private var locked by mutableStateOf(false)
    private var leftAt = 0L

    private val unlock = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) locked = false
    }

    /** The system's own PIN, pattern or fingerprint screen; there is nothing of ours to bypass. */
    private fun askUnlock() {
        val km = getSystemService(android.app.KeyguardManager::class.java)
        val i = km.createConfirmDeviceCredentialIntent("B-72", "Unlock your notes") ?: run { locked = false; return }
        unlock.launch(i)
    }

    override fun onStart() {
        super.onStart()
        if (com.b72.keep_canvas.data.AppLock.enabled(this)) {
            window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
            if (leftAt != 0L && System.currentTimeMillis() - leftAt > 30_000L) { locked = true; askUnlock() }
        } else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() { super.onStop(); leftAt = System.currentTimeMillis() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.b72.keep_canvas.data.Digest.schedule(applicationContext)
        // a recreated activity (rotation) must not replay the same share
        if (savedInstanceState == null) { incoming = intent.toShare(); launch = intent.toLaunch(); handleAuth(intent) }
        if (savedInstanceState == null && com.b72.keep_canvas.data.AppLock.enabled(this)) { locked = true; askUnlock() }
        setContent {
            B72Theme {
                if (locked) androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    androidx.compose.material3.FilledTonalButton({ askUnlock() }) { androidx.compose.material3.Text("Unlock B-72") }
                } else App(incoming, { incoming = null }, launch) { launch = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toShare()?.let { incoming = it }
        intent.toLaunch()?.let { launch = it }
        handleAuth(intent)
    }

    /** Spotify sends the browser back to b72://spotify?code=… */
    private fun handleAuth(i: Intent) {
        val u = i.data ?: return
        if (u.scheme == "b72" && u.host == "spotify") kotlinx.coroutines.MainScope().launch { com.b72.keep_canvas.data.Spotify.finish(applicationContext, u) }
    }
}
