package com.b72.keep_canvas.data

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The person's Google Drive "app data" folder: a private place only this app can see, where the encrypted backup lives. */
object Drive {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val NAME = "b72-backup.b72e"
    private const val API = "https://www.googleapis.com/drive/v3/files"

    class Remote(val id: String, val modified: String, val size: Long)

    fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Asks Google for a short-lived access token; if consent is needed the caller launches [onResolve]. */
    fun authorize(ctx: Context, onResolve: (android.content.IntentSender) -> Unit, onToken: (String) -> Unit, onError: (String) -> Unit) {
        Identity.getAuthorizationClient(ctx).authorize(request())
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) onResolve(pi.intentSender)
                else r.accessToken?.let(onToken) ?: onError("Google did not give access")
            }
            .addOnFailureListener { onError(it.message ?: "Google sign-in failed") }
    }

    fun tokenFrom(ctx: Context, data: android.content.Intent?): String? = runCatching { Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data).accessToken }.getOrNull()

    private fun open(url: String, token: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 20_000; readTimeout = 60_000
        }

    private fun check(c: HttpURLConnection): String {
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code !in 200..299) error("Drive said $code ${body.take(160)}")
        return body
    }

    fun list(token: String): List<Remote> {
        val q = URLEncoder.encode("name='$NAME'", "UTF-8")
        val c = open("$API?spaces=appDataFolder&q=$q&orderBy=modifiedTime%20desc&fields=files(id,modifiedTime,size)", token)
        val arr = JSONObject(check(c)).optJSONArray("files") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Remote(o.getString("id"), o.optString("modifiedTime"), o.optString("size").toLongOrNull() ?: 0) } }
    }

    /** Saves the file as the newest backup, then removes the older copies. */
    fun upload(token: String, file: File) {
        val old = list(token)
        val b = "b72boundary7f3a"
        val meta = JSONObject().put("name", NAME).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
        val pre = "--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$b\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray()
        val post = "\r\n--$b--".toByteArray()
        val c = open("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", token, "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/related; boundary=$b")
        c.setFixedLengthStreamingMode(pre.size + file.length() + post.size)
        c.outputStream.use { o -> o.write(pre); file.inputStream().use { it.copyTo(o) }; o.write(post) }
        check(c)
        for (r in old) runCatching { check(open("$API/${r.id}", token, "DELETE")) }
    }

    fun download(token: String, id: String, to: File) {
        val c = open("$API/$id?alt=media", token)
        if (c.responseCode !in 200..299) error("Drive said ${c.responseCode}")
        c.inputStream.use { i -> to.outputStream().use { i.copyTo(it) } }
    }
}
