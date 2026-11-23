package com.b72.keep_canvas.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One person-sized corner of the app: its own notes, tags, library, canvases, archive and trash. [hue] picks its accent (0..5). */
data class Profile(val id: String, val name: String, val hue: Int = 0)

/**
 * The profiles on this phone. The first one, "main", is the library the app always had, still in files/b72/notes.json, so nothing is migrated
 * or moved: a phone that has never made a second profile behaves as before. Every other profile keeps its library in files/b72/profiles/<id>/.
 * Pictures stay in the one shared photo folder (files are named uniquely), so a profile's file only lists the ones it uses.
 */
object Profiles {
    const val MAIN = "main"
    val all = mutableStateListOf(Profile(MAIN, "Personal"))
    var activeId by mutableStateOf(MAIN)
        private set
    private var loaded = false
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("b72_profiles", Context.MODE_PRIVATE)

    val active: Profile get() = all.firstOrNull { it.id == activeId } ?: all.first()

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = sp(ctx)
        runCatching {
            val a = JSONArray(p.getString("list", null) ?: return@runCatching)
            val l = (0 until a.length()).map { a.getJSONObject(it) }.map { Profile(it.getString("id"), it.getString("name"), it.optInt("hue", 0)) }
            if (l.isNotEmpty()) { all.clear(); all.addAll(if (l.any { it.id == MAIN }) l else listOf(Profile(MAIN, "Personal")) + l) }
        }
        activeId = p.getString("active", MAIN)?.takeIf { id -> all.any { it.id == id } } ?: MAIN
    }

    private fun save(ctx: Context) {
        val a = JSONArray(); all.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("hue", it.hue)) }
        sp(ctx).edit().putString("list", a.toString()).putString("active", activeId).apply()
    }

    /** The notes file of one profile. */
    fun file(ctx: Context, id: String): File =
        if (id == MAIN) File(ctx.filesDir, "b72/notes.json") else File(ctx.filesDir, "b72/profiles/$id/notes.json")

    fun add(ctx: Context, name: String, hue: Int = all.size % 6): Profile {
        val p = Profile("p" + java.lang.Long.toString(System.currentTimeMillis(), 36), name.trim().ifEmpty { "Profile" }.take(24), hue.coerceIn(0, 5))
        all.add(p); save(ctx); return p
    }

    fun rename(ctx: Context, id: String, name: String, hue: Int) {
        val i = all.indexOfFirst { it.id == id }; if (i < 0) return
        all[i] = all[i].copy(name = name.trim().ifEmpty { all[i].name }.take(24), hue = hue.coerceIn(0, 5)); save(ctx)
    }

    fun setActive(ctx: Context, id: String) { if (all.any { it.id == id }) { activeId = id; save(ctx) } }

    /** Forgets a profile and deletes its library file. The main profile cannot be removed. */
    fun remove(ctx: Context, id: String) {
        if (id == MAIN) return
        all.removeAll { it.id == id }
        if (activeId == id) activeId = MAIN
        runCatching { file(ctx, id).parentFile?.deleteRecursively() }
        save(ctx)
    }

    /** Every profile's library file that exists, for work that must see them all (picture clean-up, alarms). */
    fun files(ctx: Context): List<File> { load(ctx); return all.map { file(ctx, it.id) }.filter { it.exists() } }
}
