package com.b72.keep_canvas.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The notes the UI shows. Every change is written straight back to files/b72/notes.json. */
class Repo(private val ctx: Context, private val scope: CoroutineScope) {
    var loaded by mutableStateOf<Loaded?>(null)
        private set

    /** Progress of the background picture work (words read, subjects cut): how many are done of how many were found. */
    var prepDone by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var prepTotal by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    private val brain by lazy { Brain(ctx) }

    /** The notes most related to one note; meaning-aware when the packaged language model is there. */
    suspend fun related(id: String, k: Int = 6): List<Note> = withContext(Dispatchers.Default) {
        val all = loaded?.library?.notes ?: return@withContext emptyList()
        val me = all.firstOrNull { it.id == id } ?: return@withContext emptyList()
        Related.rank(all, me, k) { brain.vector(it) }
    }

    suspend fun init() { loaded = Store.load(ctx); autoClean() }

    /** Deletes picture files that no note, cut-out, sticker or placed item refers to any more (and are over a day old, so a note being written is safe). Returns the bytes freed. */
    suspend fun cleanOrphans(): Long = withContext(Dispatchers.IO) {
        val lib = loaded?.library ?: return@withContext 0L
        val text = AppJson.encodeToString(Library.serializer(), lib)
        val cutoff = System.currentTimeMillis() - 24 * 3_600_000L
        var freed = 0L
        imageDir(ctx).listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < cutoff && !text.contains(f.name)) { val n = f.length(); if (f.delete()) freed += n }
        }
        freed
    }

    /** Once a week, quietly. */
    private suspend fun autoClean() {
        val sp = ctx.getSharedPreferences("b72", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - sp.getLong("cleaned", 0) < 7 * 86_400_000L) return
        cleanOrphans()
        sp.edit().putLong("cleaned", System.currentTimeMillis()).apply()
    }

    fun note(id: String): Note? = loaded?.library?.notes?.firstOrNull { it.id == id }

    fun upsert(n0: Note) {
        val l = loaded?.library ?: return
        val old = l.notes.firstOrNull { it.id == n0.id }
        val n = if (old != null && old.contentSig() != n0.contentSig()) n0.copy(edited = System.currentTimeMillis()) else n0
        val notes = if (old != null) l.notes.map { if (it.id == n.id) n else it } else l.notes + n
        commit(l.copy(notes = notes))
    }

    /**
     * Background work on pictures, once each: the words in a photo (for search) and the subject's cut-out (for the pop-out
     * and for canvases that overlap cards). Each result is stored with its block, one note write per picture.
     */
    suspend fun prepare() {
        val todo = loaded?.library?.notes.orEmpty().flatMap { n -> n.blocks.filter { needsWork(it) }.map { n.id to it.id } }
        prepTotal = todo.size
        prepDone = 0
        var session: Assistant.Session? = null
        var tried = false
        for ((nid, bid) in todo) {
            prepDone++
            val b = note(nid)?.blocks?.firstOrNull { it.id == bid } ?: continue
            val path = b.path
            var d = b.data
            if (path != null) {
                if (b.kind == Kind.Photo && "ocr" !in d) Ocr.read(ctx, path)?.let { d = d + ("ocr" to JsonPrimitive(it)) }
                if (b.kind in CUT_KINDS && cutStale(b)) {
                    makeCut(path, imageDir(ctx)).onSuccess { c ->
                        b.cutPath()?.let { old -> runCatching { File(old).delete() } }
                        d = if (c == null) d + mapOf("cut" to JsonPrimitive(""), "cutOk" to JsonPrimitive(0), "cutWhy" to JsonPrimitive("no clear subject"), "cutV" to JsonPrimitive(CUT_VERSION))
                        else d + mapOf(
                            "cut" to JsonPrimitive(c.path), "cutBox" to JsonArray(c.box.map { JsonPrimitive(it) }),
                            "cutAspect" to JsonPrimitive(c.aspect), "cutFrac" to JsonPrimitive(c.frac),
                            "cutOk" to JsonPrimitive(if (c.ok) 1 else 0), "cutWhy" to JsonPrimitive(c.why), "cutV" to JsonPrimitive(CUT_VERSION),
                        )
                    }
                }
            }
            // the assistant (if installed) reads the text: summary, tags, facts, and a ticket the plain readers missed
            var cur = b.copy(data = d)
            var ai: Analysis? = null
            if (aiStale(cur)) {
                if (!tried) { tried = true; session = withContext(Dispatchers.Default) { Assistant.open(ctx) } }
                session?.let { s ->
                    val text = cur.tripText()
                    ai = withContext(Dispatchers.Default) { Assistant.analyse(s, text) }
                }
                ai?.let { d = d + it.toData(cur); cur = cur.copy(data = d) }
            }
            var trip: Trip? = cur.trip()
            var tripChanged = false
            if (tripStale(cur) || ai != null) {
                trip = Trips.parse(cur.tripText())?.fill(ai?.trip) ?: ai?.trip
                d = if (trip != null) d + ("trip" to AppJson.encodeToJsonElement(Trip.serializer(), trip)) else d - "trip"
                d = d + ("tripH" to JsonPrimitive(tripHash(cur)))
                tripChanged = true
            }
            if (d == b.data) continue
            val n = note(nid) ?: continue
            upsert(n.copy(blocks = n.blocks.map { if (it.id == bid) it.copy(data = d) else it }))
            if (tripChanged) {
                if (trip != null) TripAlerts.schedule(ctx, n.id, n.title, trip) else TripAlerts.cancel(ctx, n.id)
            }
        }
        withContext(Dispatchers.Default) { session?.close() }
        prepTotal = 0
    }

    /** Stickers are kept in the library as {i, p} objects, newest first, exactly as the old app wrote them. */
    fun addStickers(paths: List<String>) {
        val l = loaded?.library ?: return
        val made = paths.map { kotlinx.serialization.json.buildJsonObject { put("i", JsonPrimitive(it.substringAfterLast('/'))); put("p", JsonPrimitive(it)) } }
        commit(l.copy(stickers = made + l.stickers))
    }

    fun removeSticker(path: String) {
        val l = loaded?.library ?: return
        commit(l.copy(placed = l.placed.mapValues { (_, v) -> v.filter { it.k != "s:$path" } }, stickers = l.stickers.filter { (it as? kotlinx.serialization.json.JsonObject)?.get("p")?.let { p -> (p as? JsonPrimitive)?.content } != path }))
        runCatching { File(path).delete() }
    }

    /** Sets one loose value on one block (for example the pop-out override). */
    fun setData(noteId: String, blockId: String, key: String, value: kotlinx.serialization.json.JsonElement) {
        val n = note(noteId) ?: return
        upsert(n.copy(blocks = n.blocks.map { if (it.id == blockId) it.copy(data = it.data + (key to value)) else it }))
    }

    /** Switches a note's text between plain and smart (poster) layout; the assistant re-reads it so the facts are fresh. */
    fun setSmart(noteId: String, on: Boolean) {
        val n = note(noteId) ?: return
        val from = if (on) Kind.Text else Kind.Poster
        val to = if (on) Kind.Poster else Kind.Text
        upsert(n.copy(blocks = n.blocks.map { if (it.kind == from) it.copy(k = to.ordinal, data = it.data - "aiH") else it }))
    }

    /** Adds a block of plain text to the end of a note (what the brainstorm wrote). */
    fun appendText(noteId: String, text: String) {
        val n = note(noteId) ?: return
        upsert(n.copy(blocks = n.blocks + newBlock(Kind.Text, "ai${System.nanoTime()}", body = text.trim())))
    }

    fun setPlaced(tag: String, items: List<Stuck>) {
        val l = loaded?.library ?: return
        if (l.placed[tag].orEmpty() == items) return
        commit(l.copy(placed = if (items.isEmpty()) l.placed - tag else l.placed + (tag to items)))
    }

    /** A track from a service (Spotify) becomes a note with a track card: cover, artist, album, year and a link back. */
    suspend fun addTrack(c: Cand, tag: String? = null): String {
        val id = (System.currentTimeMillis() * 1000).toString()
        val path = saveImage(c.image, imageDir(ctx))
        val data = c.data.filterValues { it.isNotEmpty() }.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) as kotlinx.serialization.json.JsonElement } +
            ("src" to kotlinx.serialization.json.JsonPrimitive("Spotify"))
        val b = newBlock(Kind.Track, "tr$id", title = c.title, path = path).copy(data = data)
        upsert(Note(id = id, blocks = listOf(b), tags = listOfNotNull(tag)))
        return id
    }

    // ---- inline editing: the note page edits in place, so each field saves on its own

    /** A new note with one empty block of [kind] (text or checklist), ready to type into. Returns its id. */
    fun newDraft(kind: Kind, tag: String? = null): String {
        val id = (System.currentTimeMillis() * 1000).toString()
        val b = if (kind == Kind.Checklist) newBlock(Kind.Checklist, "in_$id", title = "List", items = listOf(""))
        else newBlock(Kind.Text, "in_$id")
        upsert(Note(id = id, blocks = listOf(b), tags = listOfNotNull(tag)))
        return id
    }

    /** Throws away a note that was started and left blank. */
    fun discardIfEmpty(id: String) {
        val n = note(id) ?: return
        val blank = n.blocks.all { b ->
            when (b.kind) {
                Kind.Text, Kind.Bare -> b.body.isBlank() && b.title.isBlank()
                Kind.Checklist -> b.checkItems.all { it.isBlank() }
                else -> false
            }
        }
        if (blank) delete(id)
    }

    /** Sets the note's own title, kept in a title-only block (older notes kept it on their text block). */
    fun setTitle(noteId: String, title: String) {
        val n = note(noteId) ?: return
        val t = title.trim()
        val has = n.blocks.indexOfFirst { it.kind != Kind.Checklist && it.title.isNotBlank() }
        val blocks = when {
            has >= 0 -> n.blocks.mapIndexed { i, b -> if (i == has) b.copy(title = t) else b }
                .filterNot { it.kind == Kind.Bare && it.title.isBlank() }
            t.isNotEmpty() -> listOf(newBlock(Kind.Bare, "t${System.nanoTime()}", title = t)) + n.blocks
            else -> n.blocks
        }
        upsert(n.copy(blocks = blocks))
    }

    /** Sets the words of a text block; a block that does not exist yet (id [blockId]) is created at the end of the words. */
    fun setBody(noteId: String, blockId: String, text: String) {
        val n = note(noteId) ?: return
        val blocks = if (n.blocks.any { it.id == blockId }) n.blocks.map { if (it.id == blockId) it.copy(body = text, data = it.data - "aiH") else it }
        else if (text.isBlank()) n.blocks
        else n.blocks + newBlock(Kind.Text, blockId, body = text)
        upsert(n.copy(blocks = blocks))
    }

    /** Sets the rows of a checklist; blank rows are left out. */
    fun setItems(noteId: String, blockId: String, items: List<String>) {
        val n = note(noteId) ?: return
        val keep = items.filter { it.isNotBlank() }
        upsert(n.copy(blocks = n.blocks.map { if (it.id == blockId) it.copy(items = keep, body = keep.joinToString("\n")) else it }))
    }

    fun toggleStar(noteId: String) {
        val n = note(noteId) ?: return
        upsert(n.copy(starred = !n.starred))
    }

    /** Crosses an item of a note's checklist off, or back on. Items are marked "x " as in the old app. */
    fun toggleItem(noteId: String, blockId: String, index: Int) {
        val n = note(noteId) ?: return
        fun flip(s: String) = if (s.startsWith("x ")) s.substring(2) else "x $s"
        upsert(n.copy(blocks = n.blocks.map { b ->
            when {
                b.id != blockId -> b
                b.items.isNotEmpty() -> b.copy(items = b.items.mapIndexed { i, s -> if (i == index) flip(s) else s })
                else -> b.copy(body = b.body.lines().filter { it.isNotBlank() }.mapIndexed { i, s -> if (i == index) flip(s) else s }.joinToString("\n"))
            }
        }))
    }

    /** Adds the notes of a restored backup that are not here yet; existing notes are never overwritten. Returns how many were added. */
    fun merge(lib: Library): Int {
        val cur = loaded?.library ?: return 0
        val have = cur.notes.map { it.id }.toSet()
        val fresh = lib.notes.filter { it.id !in have }
        if (fresh.isNotEmpty()) commit(cur.copy(notes = cur.notes + fresh))
        return fresh.size
    }

    /** Several notes in one write (the canvas stores every card it had to move at once). */
    fun upsertAll(ns: List<Note>) {
        val l = loaded?.library ?: return
        val by = ns.associateBy { it.id }
        commit(l.copy(notes = l.notes.map { by[it.id] ?: it }))
    }

    fun delete(id: String) {
        val l = loaded?.library ?: return
        TripAlerts.cancel(ctx, id)
        commit(l.copy(notes = l.notes.filter { it.id != id }))
    }

    private fun commit(lib: Library) {
        loaded = Loaded(lib, Source.Own)
        scope.launch { Store.save(ctx, lib); com.b72.keep_canvas.widget.TripWidget.refresh(ctx) }
    }

    /** Copies a picked photo into our own folder and measures it (height / width, clamped like the old cards). */
    suspend fun importPhoto(uri: Uri, id: String): Block? {
        if (ctx.contentResolver.getType(uri) == "application/pdf") return importPdf(uri, id)
        return importImage(uri, id)
    }

    /** A shared PDF (a ticket, a bill): its first page becomes the picture and the words of up to four pages are read for search. */
    private suspend fun importPdf(uri: Uri, id: String): Block? = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(ctx.cacheDir, "in_${System.nanoTime()}.pdf")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            val text = StringBuilder()
            var block: Block? = null
            android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                android.graphics.pdf.PdfRenderer(fd).use { r ->
                    for (i in 0 until minOf(r.pageCount, 4)) r.openPage(i).use { pg ->
                        val w = 1600
                        val h = (pg.height * w.toDouble() / pg.width).toInt().coerceAtLeast(1)
                        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        pg.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        Ocr.readBitmap(bmp)?.takeIf { it.isNotBlank() }?.let { text.append(it).append('\n') }
                        if (i == 0) {
                            val f = File(imageDir(ctx), "${System.currentTimeMillis()}_${id.takeLast(4)}.jpg")
                            f.outputStream().use { o -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, o) }
                            block = newBlock(Kind.Photo, id, path = f.path, aspect = (h.toDouble() / w).coerceIn(.6, 1.4))
                        }
                        bmp.recycle()
                    }
                }
            }
            tmp.delete()
            block?.copy(data = mapOf("ocr" to JsonPrimitive(text.toString().trim())))
        }.getOrNull()
    }

    private suspend fun importImage(uri: Uri, id: String): Block? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.filesDir, "b72/photos").apply { mkdirs() }
            val f = File(dir, "${System.currentTimeMillis()}_${id.takeLast(4)}.jpg")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            val turned = runCatching { ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1) in setOf(5, 6, 7, 8)
            val w = (if (turned) o.outHeight else o.outWidth).coerceAtLeast(1)
            val h = (if (turned) o.outWidth else o.outHeight).coerceAtLeast(1)
            newBlock(Kind.Photo, id, path = f.path, aspect = (h.toDouble() / w).coerceIn(.6, 1.4))
        }.getOrNull()
    }
}

/** Where downloaded posters, covers and link images are kept. */
fun imageDir(ctx: Context) = File(ctx.filesDir, "b72/photos").apply { mkdirs() }

private val CUT_KINDS = setOf(Kind.Photo, Kind.Recipe)

/** True when a picture still has its text read or its subject cut. */
fun needsWork(b: Block) = (b.path != null && ((b.kind == Kind.Photo && "ocr" !in b.data) || (b.kind in CUT_KINDS && cutStale(b)))) || tripStale(b) || aiStale(b)

/** True until the photo has been cut with the current recipe. */
fun cutStale(b: Block) = (b.dbl("cutV") ?: 0.0) < CUT_VERSION
