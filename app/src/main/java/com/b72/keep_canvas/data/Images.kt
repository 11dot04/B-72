package com.b72.keep_canvas.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Small decoded-bitmap cache. Photos are rotated by their EXIF flag, as Flutter's Image.file did. */
object Images {
    private fun lru(bytes: Int) = object : LruCache<String, ImageBitmap>(bytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    // grid thumbnails live in their own cache so big note-page photos can never evict them
    private val thumbs = lru(24 * 1024 * 1024)
    private val big = lru((Runtime.getRuntime().maxMemory() / 6).toInt().coerceAtLeast(48 * 1024 * 1024))
    private fun cacheFor(w: Int) = if (w <= 700) thumbs else big

    /** Already-decoded bitmap, or null. Lets a composable start with the picture instead of a blank frame. */
    fun peek(path: String, targetWidth: Int): ImageBitmap? = cacheFor(targetWidth).get("$path@$targetWidth")

    suspend fun load(path: String, targetWidth: Int): ImageBitmap? {
        val key = "$path@$targetWidth"
        val cache = cacheFor(targetWidth)
        cache.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) { decode(path, targetWidth) } ?: return null
        cache.put(key, bmp)
        return bmp
    }

    private fun decode(path: String, target: Int): ImageBitmap? = runCatching {
        if (!File(path).exists()) return null
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, b)
        var s = 1
        while (b.outWidth / (s * 2) >= target) s *= 2
        var bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val deg = when (runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
            6 -> 90f
            3 -> 180f
            8 -> 270f
            else -> 0f
        }
        if (deg != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
        bmp.asImageBitmap()
    }.getOrNull()
}
