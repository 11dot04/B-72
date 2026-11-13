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
        if (k.isBlank()) null else {
            val c = (URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 60_000
                setRequestProperty("Content-Type", "application/json"); setRequestProperty("x-goog-api-key", k)
            }
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) null
            else {
                val r = JSONObject(c.inputStream.bufferedReader().readText())
                r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim().ifEmpty { null }
            }
        }
    } catch (e: Throwable) { null }
}
