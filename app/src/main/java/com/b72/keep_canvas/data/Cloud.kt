package com.b72.keep_canvas.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * The optional cloud assistant: Google's Gemini, called with the person's own API key. It answers the same prompts the on-device model does,
 * so everything that reads notes works with either. Off unless switched on; when on, the text of the notes it reads goes to Google.
 */
object Cloud {
    const val FLASH_LITE = "gemini-flash-lite-latest"
    const val FLASH = "gemini-flash-latest"

    var on by mutableStateOf(false)
        private set
    var model by mutableStateOf(FLASH_LITE)
        private set

    /** Free keys allow about 500 Flash-Lite and 20 Flash requests a day (and 15 and 5 a minute); the app stays a little under both. */
    private fun dailyCap() = if (model == FLASH) 18 else 450
    private var blockedUntil = 0L

    /** How many cloud requests were made today. */
    fun usedToday(ctx: Context): Int {
        val p = prefs(ctx)
        return if (p.getString("day", "") == java.time.LocalDate.now().toString()) p.getInt("used", 0) else 0
    }
    private fun count(ctx: Context) {
        val today = java.time.LocalDate.now().toString()
        val p = prefs(ctx)
        p.edit().putString("day", today).putInt("used", usedToday(ctx) + 1).apply()
    }
    val cap: Int get() = dailyCap()

    /** When notes are read: 0 as you write, 1 when you leave the note, 2 only when you ask. */
    fun readMode(ctx: Context): Int = prefs(ctx).getInt("readMode", 1)
    fun setReadMode(ctx: Context, m: Int) { prefs(ctx).edit().putInt("readMode", m).apply(); Assistant.refresh(ctx) }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("b72_ai", Context.MODE_PRIVATE)

    fun key(ctx: Context): String = prefs(ctx).getString("gkey", "").orEmpty()
    fun wanted(ctx: Context): Boolean = prefs(ctx).getBoolean("cloud", false)

    fun load(ctx: Context) {
        val p = prefs(ctx)
        on = p.getBoolean("cloud", false) && key(ctx).isNotBlank()
        model = p.getString("cloudModel", FLASH_LITE)?.takeIf { it == FLASH || it == FLASH_LITE } ?: FLASH_LITE
    }

    fun save(ctx: Context, enabled: Boolean, key: String, model: String) {
        prefs(ctx).edit().putBoolean("cloud", enabled).putString("gkey", key.trim()).putString("cloudModel", model).apply()
        load(ctx)
    }

    /** One answer from Gemini, or null when the call fails (no key, no network, quota). Blocking: call it off the main thread. */
    fun ask(ctx: Context, prompt: String): String? = try {
        val k = key(ctx)
        if (k.isBlank() || System.currentTimeMillis() < blockedUntil || usedToday(ctx) >= dailyCap()) null else {
            count(ctx)
            val c = (URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 60_000
                setRequestProperty("Content-Type", "application/json"); setRequestProperty("x-goog-api-key", k)
            }
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode == 429) {
                val why = runCatching { (c.errorStream ?: c.inputStream).bufferedReader().readText() }.getOrDefault("")
                // a day's allowance gone waits for the day; a minute's, for the minute
                blockedUntil = System.currentTimeMillis() + if (why.contains("PerDay", true)) 6 * 3_600_000L else 65_000L
                null
            } else if (c.responseCode !in 200..299) null
            else {
                val r = JSONObject(c.inputStream.bufferedReader().readText())
                r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim().ifEmpty { null }
            }
        }
    } catch (e: Throwable) { null }
}
