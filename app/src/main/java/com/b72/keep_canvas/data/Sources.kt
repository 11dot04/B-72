package com.b72.keep_canvas.data

/**
 * Recognises links shared from other apps and pulls the facts out of their previews, so a Strava run
 * becomes a card with distance, time and pace, and a Spotify track a card with artist and year.
 * Extra facts are returned as plain fields: `src` (the service) and `stats` ("Label|value" per line).
 */
object Sources {
    private val HOSTS = listOf(
        "strava" to listOf("strava.com", "strava.app.link"),
        "Spotify" to listOf("open.spotify.com", "spotify.link"),
        "Garmin" to listOf("connect.garmin.com", "garmin.com"),
        "Komoot" to listOf("komoot.com", "komoot.de"),
        "AllTrails" to listOf("alltrails.com"),
        "Google Maps" to listOf("maps.app.goo.gl", "goo.gl", "google.com/maps"),
        "YouTube" to listOf("youtube.com", "youtu.be", "music.youtube.com"),
        "Goodreads" to listOf("goodreads.com"),
        "Letterboxd" to listOf("letterboxd.com", "boxd.it"),
        "Wikipedia" to listOf("wikipedia.org"),
    ).map { (n, h) -> (if (n == "strava") "Strava" else n) to h }

    fun of(url: String): String? {
        val u = url.lowercase()
        if (Regex("""//[a-z-]+\.(m\.)?wikipedia\.org""").containsMatchIn(u)) return "Wikipedia"
        return HOSTS.firstOrNull { (_, hs) -> hs.any { h -> u.contains("//$h") || u.contains("//www.$h") || u.contains("//m.$h") || (h.contains('/') && u.contains(h)) } }?.first
    }

    fun enrich(url: String, p: Map<String, String>): Map<String, String> {
        val src = of(url) ?: return emptyMap()
        val stats = when (src) {
            "Strava", "Garmin", "Komoot", "AllTrails" -> activity(p)
            "Spotify" -> spotify(url, p)
            else -> emptyList()
        }
        return buildMap {
            put("src", src)
            if (stats.isNotEmpty()) put("stats", stats.joinToString("\n") { "${it.first}|${it.second}" })
        }
    }

    // ---- activities (runs, rides, hikes)

    private val KV = Regex("""(Distance|Elevation(?: Gain)?|Moving Time|Elapsed Time|Time|Duration|Pace|Avg(?:\.|erage)? Pace|Avg(?:\.|erage)? Speed|Calories|Avg(?:\.|erage)? Heart Rate|Ascent)\s*:\s*([^,;|\n]+)""", RegexOption.IGNORE_CASE)
    private val DIST = Regex("""(\d+(?:[.,]\d+)?)\s?(km|mi|miles|kilometers|kilometres)\b""", RegexOption.IGNORE_CASE)
    private val TIME = Regex("""\b(?:(\d+)\s?h(?:ours?)?\s?)?(\d+)\s?m(?:in(?:utes?)?)?(?:\s?(\d+)\s?s)?\b""")
    private val SPORT = Regex("""\b(run|ran|ride|rode|cycled|walk|walked|hike|hiked|swim|swam|trail run|workout|yoga|row)\b""", RegexOption.IGNORE_CASE)

    private fun activity(p: Map<String, String>): List<Pair<String, String>> {
        val text = listOf(p["title"], p["desc"]).joinToString(" . ")
        val out = LinkedHashMap<String, String>()
        for (m in KV.findAll(text)) {
            val k = m.groupValues[1].lowercase()
            val v = m.groupValues[2].trim().trimEnd('.')
            val label = when {
                k.startsWith("dist") -> "Distance"
                k.startsWith("elev") || k == "ascent" -> "Elevation"
                k.contains("moving") || k == "time" || k == "duration" -> "Time"
                k.contains("elapsed") -> "Elapsed"
                k.contains("pace") -> "Pace"
                k.contains("speed") -> "Speed"
                k.contains("heart") -> "Heart rate"
                k.startsWith("cal") -> "Calories"
                else -> m.groupValues[1]
            }
            out.putIfAbsent(label, v)
        }
        if ("Distance" !in out) DIST.find(text)?.let { out["Distance"] = it.groupValues[1].replace(',', '.') + " " + (if (it.groupValues[2].startsWith("mi")) "mi" else "km") }
        if ("Time" !in out) TIME.find(text)?.let { out["Time"] = it.value.trim() }
        if ("Pace" !in out) {
            val km = out["Distance"]?.let { Regex("""[\d.]+""").find(it)?.value?.toDoubleOrNull() }
            val secs = out["Time"]?.let { seconds(it) }
            if (km != null && km > 0 && secs != null && secs > 0) {
                val unit = if (out["Distance"]!!.contains("mi")) "mi" else "km"
                val per = (secs / km).toInt()
                out["Pace"] = "%d:%02d /%s".format(per / 60, per % 60, unit)
            }
        }
        SPORT.find(text)?.let { out["Sport"] = sportWord(it.value) }
        // the sport leads; the rest follow in a steady order
        val order = listOf("Sport", "Distance", "Time", "Pace", "Speed", "Elevation", "Heart rate", "Calories", "Elapsed")
        return order.mapNotNull { k -> out[k]?.let { k to it } }
    }

    private fun sportWord(w: String) = when (w.lowercase()) {
        "run", "ran", "trail run" -> "Run"; "ride", "rode", "cycled" -> "Ride"; "walk", "walked" -> "Walk"
        "hike", "hiked" -> "Hike"; "swim", "swam" -> "Swim"; else -> w.replaceFirstChar { it.uppercase() }
    }

    private fun seconds(t: String): Int? {
        val colon = Regex("""^(?:(\d+):)?(\d+):(\d+)$""").find(t.trim())
        if (colon != null) return (colon.groupValues[1].toIntOrNull() ?: 0) * 3600 + colon.groupValues[2].toInt() * 60 + colon.groupValues[3].toInt()
        val m = TIME.find(t) ?: return null
        return (m.groupValues[1].toIntOrNull() ?: 0) * 3600 + m.groupValues[2].toInt() * 60 + (m.groupValues[3].toIntOrNull() ?: 0)
    }

    // ---- Spotify

    private fun spotify(url: String, p: Map<String, String>): List<Pair<String, String>> {
        val kind = Regex("""open\.spotify\.com/(?:intl-[a-z]+/)?(track|album|playlist|episode|show|artist)/""").find(url)?.groupValues?.get(1)
        val out = ArrayList<Pair<String, String>>()
        out += "Type" to (kind ?: "link").replaceFirstChar { it.uppercase() }
        // "Artist · Album · Song · 2019" style description
        val parts = (p["desc"] ?: "").split("·").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isNotEmpty() && kind == "track") {
            out += "Artist" to parts.first()
            parts.firstOrNull { Regex("""^(19|20)\d\d$""").matches(it) }?.let { out += "Year" to it }
        } else if (parts.isNotEmpty()) out += "By" to parts.first().removePrefix("Playlist").trim()
        return out
    }
}
