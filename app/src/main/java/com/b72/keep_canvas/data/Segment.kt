package com.b72.keep_canvas.data

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One subject ML Kit found: a confidence mask (0..1) for the rectangle x, y, w, h of the picture. */
class Subj(val mask: FloatArray, val x: Int, val y: Int, val w: Int, val h: Int)

class Upright(val bmp: Bitmap, val pix: IntArray, val subjects: List<Subj>) {
    val w get() = bmp.width
    val h get() = bmp.height
}

/** The result of cutting a subject out of a photo; the same keys the old app stored with the block. */
class Cut(val path: String, val box: List<Double>, val aspect: Double, val frac: Double)

object Seg {
    private val client by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects(SubjectSegmenterOptions.SubjectResultOptions.Builder().enableConfidenceMask().build())
                .build(),
        )
    }

    /** Null when the model could not run (for example it is still downloading); an empty list when no subject was found. */
    suspend fun run(src: String): Upright? {
        val loaded = Images.load(src, 1024)?.asAndroidBitmap() ?: return null
        val bmp = withContext(Dispatchers.Default) {
            val soft = loaded.copy(Bitmap.Config.ARGB_8888, false)
            if (soft.width > 1100) Bitmap.createScaledBitmap(soft, 1024, (soft.height * 1024f / soft.width).roundToInt().coerceAtLeast(1), true) else soft
        }
        val result = runCatching { client.process(InputImage.fromBitmap(bmp, 0)).await() }.getOrNull() ?: return null
        val subs = result.subjects.mapNotNull { s ->
            val m = s.confidenceMask ?: return@mapNotNull null
            if (m.remaining() < s.width * s.height) return@mapNotNull null
            val arr = FloatArray(s.width * s.height)
            m.duplicate().get(arr)
            Subj(arr, s.startX, s.startY, s.width, s.height)
        }
        val pix = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pix, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return Upright(bmp, pix, subs)
    }
}

private fun save(px: IntArray, w: Int, h: Int, file: File) {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/**
 * The subject of a photo on a transparent background, so titles can sit behind it and the note page can let it
 * break out of a shape. Failure (null) means "try again later"; success with null means "no clear subject".
 */
suspend fun makeCut(src: String, dir: File): Result<Cut?> = withContext(Dispatchers.Default) {
    val up = Seg.run(src) ?: return@withContext Result.failure(IllegalStateException("segmentation unavailable"))
    val w = up.w
    val h = up.h
    val a = FloatArray(w * h)
    for (s in up.subjects) for (y in 0 until s.h) {
        val gy = s.y + y
        if (gy < 0 || gy >= h) continue
        for (x in 0 until s.w) {
            val gx = s.x + x
            if (gx < 0 || gx >= w) continue
            val v = s.mask[y * s.w + x]
            val i = gy * w + gx
            if (v > a[i]) a[i] = v
        }
    }
    var n = 0
    var l = w
    var t = h
    var r = 0
    var bt = 0
    for (y in 0 until h) for (x in 0 until w) if (a[y * w + x] > .5f) { n++; l = min(l, x); r = max(r, x); t = min(t, y); bt = max(bt, y) }
    val frac = n.toDouble() / (w * h)
    if (frac < .05 || frac > .8) return@withContext Result.success(null)

    // pull the mask in by a pixel (drops the background-coloured fringe), then feather it
    val e = FloatArray(w * h)
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val i = y * w + x
        e[i] = min(min(a[i], a[i - 1]), min(min(a[i + 1], a[i - w]), a[i + w]))
    }
    val blur = FloatArray(w * h)
    for (y in 0 until h) for (x in 1 until w - 1) { val i = y * w + x; blur[i] = (e[i - 1] + 2 * e[i] + e[i + 1]) / 4f }
    for (y in 1 until h - 1) for (x in 0 until w) { val i = y * w + x; a[i] = (blur[i - w] + 2 * blur[i] + blur[i + w]) / 4f }

    val px = IntArray(w * h)
    for (i in 0 until w * h) {
        val al = (((a[i] - .25f) / .6f).coerceIn(0f, 1f) * 255f).roundToInt()
        px[i] = (al shl 24) or (up.pix[i] and 0xFFFFFF)
    }
    val f = File(dir, "cut_${System.nanoTime()}.png")
    save(px, w, h, f)
    Result.success(Cut(f.absolutePath, listOf(l.toDouble() / w, t.toDouble() / h, (r + 1).toDouble() / w, (bt + 1).toDouble() / h), w.toDouble() / h, frac))
}

/** Approximate distance from every pixel to the nearest foreground pixel (two-pass chamfer). */
private fun distance(fg: ByteArray, w: Int, h: Int): FloatArray {
    val d = FloatArray(w * h) { if (fg[it].toInt() == 1) 0f else 1e9f }
    for (y in 0 until h) for (x in 0 until w) {
        val i = y * w + x
        var v = d[i]
        if (x > 0) v = min(v, d[i - 1] + 1)
        if (y > 0) {
            v = min(v, d[i - w] + 1)
            if (x > 0) v = min(v, d[i - w - 1] + 1.414f)
            if (x < w - 1) v = min(v, d[i - w + 1] + 1.414f)
        }
        d[i] = v
    }
    for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
        val i = y * w + x
        var v = d[i]
        if (x < w - 1) v = min(v, d[i + 1] + 1)
        if (y < h - 1) {
            v = min(v, d[i + w] + 1)
            if (x < w - 1) v = min(v, d[i + w + 1] + 1.414f)
            if (x > 0) v = min(v, d[i + w - 1] + 1.414f)
        }
        d[i] = v
    }
    return d
}

/** One die-cut sticker (the subject plus a white outline) per subject found in the photo; null if the model could not run. */
suspend fun makeStickers(src: String, dir: File): List<String>? = withContext(Dispatchers.Default) {
    val up = Seg.run(src) ?: return@withContext null
    val out = mutableListOf<String>()
    for (s in up.subjects) {
        if (s.w < 24 || s.h < 24) continue
        val r = max(5, (min(s.w, s.h) * .045f).roundToInt())
        val pad = r + 2
        val sw = s.w + 2 * pad
        val sh = s.h + 2 * pad
        val fg = ByteArray(sw * sh)
        for (y in 0 until s.h) for (x in 0 until s.w) if (s.mask[y * s.w + x] > .5f) fg[(y + pad) * sw + x + pad] = 1
        val dist = distance(fg, sw, sh)
        val px = IntArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            val i = y * sw + x
            val d = dist[i]
            if (d > r + .5f) continue
            if (fg[i].toInt() == 1) {
                val sx = x - pad
                val sy = y - pad
                val gx = s.x + sx
                val gy = s.y + sy
                if (gx < 0 || gy < 0 || gx >= up.w || gy >= up.h) continue
                val mv = ((s.mask[sy * s.w + sx] - .3f) / .4f).coerceIn(0f, 1f)
                val c = up.pix[gy * up.w + gx]
                fun ch(shift: Int) = (255 + (((c shr shift) and 0xFF) - 255) * mv).roundToInt().coerceIn(0, 255)
                px[i] = (255 shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            } else {
                val al = ((r + .5f - d).coerceIn(0f, 1f) * 255f).roundToInt()
                px[i] = (al shl 24) or 0xFFFFFF
            }
        }
        val f = File(dir, "s_${System.nanoTime()}_${out.size}.png")
        save(px, sw, sh, f)
        out.add(f.absolutePath)
    }
    out
}
