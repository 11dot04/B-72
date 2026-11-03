package com.b72.keep_canvas.data

import android.app.KeyguardManager
import android.content.Context

/** An optional lock: the app asks for the phone's PIN, pattern or fingerprint on opening and after 30 seconds away, and hides itself from the recent-apps view. */
object AppLock {
    private fun p(ctx: Context) = ctx.getSharedPreferences("lock", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = p(ctx).getBoolean("on", false)
    fun available(ctx: Context) = ctx.getSystemService(KeyguardManager::class.java).isDeviceSecure

    /** False when the phone has no screen lock to use. */
    fun set(ctx: Context, on: Boolean): Boolean {
        if (on && !available(ctx)) return false
        p(ctx).edit().putBoolean("on", on).apply()
        return true
    }
}
