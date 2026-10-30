package com.b72.keep_canvas.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class Team(val id: String, val name: String, val sport: String, val league: String, val badge: String)

/** A finished match of a followed team. */
class Match(val eventId: String, val team: Team, val title: String, val score: String, val league: String, val daysAgo: Int)

/**
 * Teams the person follows. Scores and fixtures come from TheSportsDB's free public API (football, cricket, basketball, F1 and more);
 * when a followed team has played in the last few days the Notes tab gently asks for the person's thoughts on it.
 */
object Follow {
    private const val BASE = "https://www.thesportsdb.com/api/v1/json/3"
    val teams = mutableStateListOf<Team>()
    private var matchCache: Pair<Long, List<Match>>? = null

    private fun p(ctx: Context) = ctx.getSharedPreferences("follow", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (teams.isNotEmpty()) return
        val raw = p(ctx).getString("teams", null) ?: return
        runCatching {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) a.getJSONObject(i).let { teams.add(Team(it.getString("id"), it.getString("name"), it.optString("sport"), it.optString("league"), it.optString("badge"))) }
        }
    }

    private fun save(ctx: Context) {
        val a = JSONArray()
        for (t in teams) a.put(JSONObject().put("id", t.id).put("name", t.name).put("sport", t.sport).put("league", t.league).put("badge", t.badge))
        p(ctx).edit().putString("teams", a.toString()).apply()
        matchCache = null
    }

    fun follow(ctx: Context, t: Team) { if (teams.none { it.id == t.id }) { teams.add(t); save(ctx) } }
    fun unfollow(ctx: Context, t: Team) { teams.removeAll { it.id == t.id }; save(ctx) }

    fun handled(ctx: Context, eventId: String) = p(ctx).getStringSet("done", emptySet())!!.contains(eventId)
    fun markHandled(ctx: Context, eventId: String) {
        val s = p(ctx).getStringSet("done", emptySet())!!.toMutableSet().apply { add(eventId) }
        p(ctx).edit().putStringSet("done", s).apply()
    }

    suspend fun search(q: String): List<Team> {
        val j = json("$BASE/searchteams.php?t=${java.net.URLEncoder.encode(q, "UTF-8")}") as? JSONObject ?: return emptyList()
        val a = j.optJSONArray("teams") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
            Team(it.optString("idTeam"), it.optString("strTeam"), it.optString("strSport"), it.optString("strLeague"), it.optString("strBadge").ifEmpty { it.optString("strTeamBadge") })
        }.filter { it.id.isNotEmpty() && it.name.isNotEmpty() }
    }

    private suspend fun json(url: String): Any? = withContext(Dispatchers.IO) { fetchText(url)?.let { runCatching { org.json.JSONTokener(it).nextValue() }.getOrNull() } }

    /** Matches followed teams finished within the last [days] days that the person has not been asked about yet. */
    suspend fun recent(ctx: Context, days: Int = 3): List<Match> {
        val now = System.currentTimeMillis()
        matchCache?.let { if (now - it.first < 3 * 3_600_000L) return it.second.filter { m -> !handled(ctx, m.eventId) } }
        val out = ArrayList<Match>()
        val today = LocalDate.now()
        for (t in teams.toList()) {
            val a = (json("$BASE/eventslast.php?id=${t.id}") as? JSONObject)?.optJSONArray("results") ?: continue
            for (i in 0 until a.length()) {
                val e = a.optJSONObject(i) ?: continue
                val d = runCatching { LocalDate.parse(e.optString("dateEvent")) }.getOrNull() ?: continue
                val ago = ChronoUnit.DAYS.between(d, today).toInt()
                val hs = e.optString("intHomeScore"); val aws = e.optString("intAwayScore")
                if (ago in 0..days && hs.isNotEmpty() && hs != "null" && aws.isNotEmpty() && aws != "null") {
                    out += Match(e.optString("idEvent"), t, "${e.optString("strHomeTeam")} v ${e.optString("strAwayTeam")}", "$hs–$aws", e.optString("strLeague"), ago)
                }
            }
        }
        matchCache = now to out
        return out.filter { !handled(ctx, it.eventId) }.sortedBy { it.daysAgo }
    }
}
