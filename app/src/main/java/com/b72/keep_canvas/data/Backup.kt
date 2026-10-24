package com.b72.keep_canvas.data

import android.content.Context
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * One zip holds the whole second mind: library.json plus every photo and cut-out it points at.
 * Paths inside the zip are relative ("media/..."), so a backup restores on any phone.
 */
object Backup {
    private const val LIB = "library.json"
    private const val MEDIA = "media/"

    private fun mediaName(path: String) = "${path.hashCode().toUInt()}_${File(path).name}"

    fun export(lib: Library, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            val written = HashSet<String>()
            fun pack(path: String?): String? {
                if (path.isNullOrEmpty()) return path
                val f = File(path)
                if (!f.exists()) return path
                val name = MEDIA + mediaName(path)
                if (written.add(name)) {
                    zip.putNextEntry(ZipEntry(name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                return name
            }
            val notes = lib.notes.map { n ->
                n.copy(blocks = n.blocks.map { b ->
                    val cut = b.str("cut")
                    b.copy(
                        path = pack(b.path),
                        data = if (cut.isNullOrEmpty()) b.data else b.data + ("cut" to JsonPrimitive(pack(cut))),
                    )
                })
            }
            zip.putNextEntry(ZipEntry(LIB))
            zip.write(AppJson.encodeToString(Library.serializer(), lib.copy(notes = notes)).toByteArray())
            zip.closeEntry()
        }
    }

    /** Unpacks the media into our photo folder and returns the library with real paths, or null if this is not a B-72 backup. */
    fun read(ctx: Context, input: InputStream): Library? {
        val dir = imageDir(ctx)
        var json: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == LIB -> json = zip.readBytes().decodeToString()
                    e.name.startsWith(MEDIA) -> {
                        val target = File(dir, e.name.removePrefix(MEDIA).substringAfterLast('/'))
                        if (!target.exists()) target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val lib = json?.let { runCatching { AppJson.decodeFromString(Library.serializer(), it) }.getOrNull() } ?: return null
        fun real(p: String?) = if (p != null && p.startsWith(MEDIA)) File(dir, p.removePrefix(MEDIA)).absolutePath else p
        return lib.copy(notes = lib.notes.map { n ->
            n.copy(blocks = n.blocks.map { b ->
                val cut = b.str("cut")
                b.copy(
                    path = real(b.path),
                    data = if (cut != null && cut.startsWith(MEDIA)) b.data + ("cut" to JsonPrimitive(real(cut))) else b.data,
                )
            })
        })
    }
}
