package com.b72.keep_canvas.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.b72.keep_canvas.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Sign in with Spotify (authorisation code with PKCE, so there is no secret in the app) and read what the person listens to.
 * The client id comes from the SPOTIFY_CLIENT_ID build secret or from the field in Connected apps; the redirect to register is [REDIRECT].
 */
object Spotify {
    const val REDIRECT = "b72://spotify"
    private const val SCOPES = "user-read-recently-played user-top-read user-library-read user-read-currently-playing"

    var connected by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set

    private fun p(ctx: Context) = ctx.getSharedPreferences("spotify", Context.MODE_PRIVATE)
    fun clientId(ctx: Context): String = p(ctx).getString("client", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.SPOTIFY_CLIENT_ID
    fun setClientId(ctx: Context, id: String) { p(ctx).edit().putString("client", id.trim()).apply() }
    fun refresh(ctx: Context) { connected = p(ctx).getString("refresh", null) != null }
    fun logout(ctx: Context) { p(ctx).edit().remove("access").remove("refresh").remove("exp").apply(); connected = false }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** Opens Spotify's own sign-in page in the browser; the answer comes back through [finish]. */
    fun begin(ctx: Context): Boolean {
        val id = clientId(ctx)
        if (id.isBlank()) { message = "Add your Spotify client id first"; return false }
        val verifier = b64(ByteArray(48).also { SecureRandom().nextBytes(it) })
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        p(ctx).edit().putString("verifier", verifier).apply()
        val url = "https://accounts.spotify.com/authorize?response_type=code&client_id=$id&scope=${URLEncoder.encode(SCOPES, "UTF-8")}" +
            "&redirect_uri=${URLEncoder.encode(REDIRECT, "UTF-8")}&code_challenge_method=S256&code_challenge=$challenge"
        message = ""
        return runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Called with the b72://spotify?code=… address Spotify sends the browser back to. */
    suspend fun finish(ctx: Context, uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code == null) { message = "Spotify sign-in was cancelled"; return }
        val verifier = p(ctx).getString("verifier", null) ?: return
        val ok = token(ctx, "grant_type=authorization_code&code=${URLEncoder.encode(code, "UTF-8")}&redirect_uri=${URLEncoder.encode(REDIRECT, "UTF-8")}&client_id=${clientId(ctx)}&code_verifier=$verifier")
        message = if (ok) "" else "Spotify did not accept the sign-in. Check the client id and that $REDIRECT is a redirect URI of the app."
        refresh(ctx)
    }

    private suspend fun token(ctx: Context, form: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 14000; readTimeout = 14000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            c.outputStream.use { it.write(form.toByteArray()) }
            if (c.responseCode != 200) return@runCatching false
            val j = JSONObject(c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8))
            p(ctx).edit().putString("access", j.optString("access_token"))
                .putLong("exp", System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000 - 60_000)
                .apply { if (j.has("refresh_token")) putString("refresh", j.optString("refresh_token")) }.apply()
            true
        }.getOrDefault(false)
    }

    private suspend fun bearer(ctx: Context): String? {
        val sp = p(ctx)
        if (System.currentTimeMillis() > sp.getLong("exp", 0)) {
            val r = sp.getString("refresh", null) ?: return null
            if (!token(ctx, "grant_type=refresh_token&refresh_token=${URLEncoder.encode(r, "UTF-8")}&client_id=${clientId(ctx)}")) return null
        }
        return sp.getString("access", null)
    }

    private suspend fun api(ctx: Context, path: String): JSONObject? {
        val t = bearer(ctx) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val c = (URL("https://api.spotify.com/v1$path").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 14000; readTimeout = 14000; setRequestProperty("Authorization", "Bearer $t")
                }
                if (c.responseCode == 401) { logout(ctx); return@runCatching null }
                if (c.responseCode != 200) return@runCatching null
                JSONObject(c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8))
            }.getOrNull()
        }
    }

    private fun JSONArray.objs() = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun cand(t: JSONObject?): Cand? {
        t ?: return null
        val name = t.optString("name").ifEmpty { return null }
        val artist = t.optJSONArray("artists")?.objs()?.joinToString(", ") { it.optString("name") } ?: ""
        val al = t.optJSONObject("album")
        val img = al?.optJSONArray("images")?.objs()?.maxByOrNull { it.optInt("width") }?.optString("url")
        val year = al?.optString("release_date")?.substringBefore('-') ?: ""
        val url = t.optJSONObject("external_urls")?.optString("spotify") ?: ""
        return Cand(name, "$artist · ${al?.optString("name") ?: ""}", img, mapOf("artist" to artist, "album" to (al?.optString("name") ?: ""), "year" to year, "genre" to "", "url" to url))
    }

    enum class Source(val label: String) { Now("Now playing"), Recent("Recently played"), Liked("Liked songs"), Top("Top tracks") }

    suspend fun load(ctx: Context, s: Source): List<Cand> = when (s) {
        Source.Now -> listOfNotNull(cand(api(ctx, "/me/player/currently-playing")?.optJSONObject("item")))
        Source.Recent -> api(ctx, "/me/player/recently-played?limit=30")?.optJSONArray("items")?.objs()?.mapNotNull { cand(it.optJSONObject("track")) }?.distinctBy { it.title + it.sub } ?: emptyList()
        Source.Liked -> api(ctx, "/me/tracks?limit=30")?.optJSONArray("items")?.objs()?.mapNotNull { cand(it.optJSONObject("track")) } ?: emptyList()
        Source.Top -> api(ctx, "/me/top/tracks?limit=30&time_range=short_term")?.optJSONArray("items")?.objs()?.mapNotNull { cand(it) } ?: emptyList()
    }
}
