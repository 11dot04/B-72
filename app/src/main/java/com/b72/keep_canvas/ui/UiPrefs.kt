package com.b72.keep_canvas.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.reflect.KProperty

/** A setting that is read like any Compose state and written straight through to the phone, so it is still there next launch. */
class IntPref(private val key: String, def: Int) {
    private var st by mutableIntStateOf(def)
    internal fun load(sp: SharedPreferences) { st = sp.getInt(key, st) }
    val value: Int get() = st
    operator fun getValue(t: Any?, p: KProperty<*>): Int = st
    operator fun setValue(t: Any?, p: KProperty<*>, v: Int) { st = v; UiPrefs.save(key, v) }
}

/**
 * What the app remembers about where you were: the tab, the shelf and the place on it, how the notes are laid out. These live here, not
 * in a screen, because a screen that is left (to open a note, say) forgets everything it held, and back must return to the same place.
 */
object UiPrefs {
    private var sp: SharedPreferences? = null
    val tab = IntPref("tab", 0)
    val shelf = IntPref("shelf", 0)
    val filmIdx = IntPref("filmIdx", 0)
    val bookIdx = IntPref("bookIdx", 0)
    val cube = IntPref("cube", 0)
    val hub = IntPref("hub", 0)
    val cols = IntPref("cols", 2)
    val sort = IntPref("sort", 0)
    val boardGrid = IntPref("boardGrid", 1)
    val artifactTab = IntPref("artifactTab", 0)
    val shotTab = IntPref("shotTab", 0)
    /** 1: a title reshapes (size and width axis) between its tile and its page; 0: it is only scaled. */
    val titleMorph = IntPref("titleMorph", 1)
    val saveShots = IntPref("saveShots", 1)
    /** 1: photos on a note page are tinted in the page's colour (duotone); 0: shown as taken. */
    val duotone = IntPref("duotone", 1)

    /** The last few searches that led to a note, newest first. */
    var recents by androidx.compose.runtime.mutableStateOf(listOf<String>())
        private set
    fun pushRecent(q: String) {
        val v = q.trim()
        if (v.length < 2) return
        recents = (listOf(v) + recents.filterNot { it.equals(v, true) }).take(6)
        sp?.edit()?.putString("recents", recents.joinToString("\u001f"))?.apply()
    }
    fun clearRecents() { recents = emptyList(); sp?.edit()?.remove("recents")?.apply() }

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("b72_ui", Context.MODE_PRIVATE)
        sp = p
        listOf(tab, shelf, filmIdx, bookIdx, cube, hub, cols, sort, boardGrid, artifactTab, shotTab, titleMorph, saveShots, duotone).forEach { it.load(p) }
        recents = p.getString("recents", "").orEmpty().split('\u001f').filter { it.isNotBlank() }
    }
    internal fun save(key: String, v: Int) { sp?.edit()?.putInt(key, v)?.apply() }
}
