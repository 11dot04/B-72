package com.b72.keep_canvas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.b72.keep_canvas.data.Share
import com.b72.keep_canvas.data.toShare
import com.b72.keep_canvas.ui.App
import com.b72.keep_canvas.ui.B72Theme

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Share?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // a recreated activity (rotation) must not replay the same share
        if (savedInstanceState == null) incoming = intent.toShare()
        setContent { B72Theme { App(incoming) { incoming = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toShare()?.let { incoming = it }
    }
}
