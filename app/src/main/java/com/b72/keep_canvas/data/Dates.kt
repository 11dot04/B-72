package com.b72.keep_canvas.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A date found in a note's words: what it is about, when it starts, and whether a clock time was given. */
class Ev(val title: String, val start: Long, val timed: Boolean)

private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
private val DAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
private const val MON = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
private val DMY = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+$MON\\b\\.?(?:,?\\s+(\\d{4}))?", RegexOption.IGNORE_CASE)
private val MDY = Regex("\\b$MON\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:,?\\s+(\\d{4}))?", RegexOption.IGNORE_CASE)
private val ISO = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")
private val SLASH = Regex("\\b(\\d{1,2})[/.](\\d{1,2})(?:[/.](\\d{2,4}))?\\b")
private val REL = Regex("\\b(today|tomorrow|(?:next\\s+|this\\s+|on\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday))\\b", RegexOption.IGNORE_CASE)
private val TIME = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b|\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b", RegexOption.IGNORE_CASE)
private val LEDGER = Regex("\\b(cr|dr)\\b", RegexOption.IGNORE_CASE)

private fun monthOf(s: String) = MONTHS.indexOf(s.lowercase().take(3)) + 1

/**
 * Looks through a note's lines for things that have a date (and perhaps a time) and are still ahead: "Dentist 14 Nov 4pm",
 * "Dec 3 launch", "2026-12-01", "tomorrow 9:30", "on Friday". Day-first for numeric dates. A date with no year means its next
 * occurrence. Bank-statement style lines (Cr/Dr) are left alone. Pure text work, no model needed.
 */
fun findEvents(text: String, noteTitle: String, now: Long = System.currentTimeMillis()): List<Ev> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val out = ArrayList<Ev>()
    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.length < 4 || LEDGER.containsMatchIn(line)) continue
        var date: LocalDate? = null
        var span: IntRange? = null
        fun ahead(m: Int, d: Int, y: Int?): LocalDate? {
            if (m !in 1..12 || d !in 1..31) return null
            return runCatching {
                if (y != null) LocalDate.of(y, m, d)
                else LocalDate.of(today.year, m, d).let { if (it.isBefore(today)) it.plusYears(1) else it }
            }.getOrNull()
        }
        ISO.find(line)?.let { m -> date = ahead(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[1].toInt()); span = m.range }
        if (date == null) DMY.find(line)?.let { m -> date = ahead(monthOf(m.groupValues[2]), m.groupValues[1].toInt(), m.groupValues[3].toIntOrNull()); span = m.range }
        if (date == null) MDY.find(line)?.let { m -> date = ahead(monthOf(m.groupValues[1]), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull()); span = m.range }
        if (date == null) SLASH.find(line)?.let { m ->
            val y = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            date = ahead(m.groupValues[2].toInt(), m.groupValues[1].toInt(), y); span = m.range
        }
        if (date == null) REL.find(line)?.let { m ->
            val w = m.groupValues[1].lowercase()
            date = when {
                w == "today" -> today
                w == "tomorrow" -> today.plusDays(1)
                else -> {
                    val dow = DayOfWeek.of(DAYS.indexOf(m.groupValues[2].lowercase()) + 1)
                    today.with(TemporalAdjusters.next(dow))
                }
            }
            span = m.range
        }
        val d = date ?: continue
        val tm = TIME.find(line)
        var time: LocalTime? = null
        if (tm != null) {
            time = if (tm.groupValues[3].isNotEmpty()) {
                var h = tm.groupValues[1].toInt() % 12
                if (tm.groupValues[3].lowercase() == "pm") h += 12
                runCatching { LocalTime.of(h, tm.groupValues[2].toIntOrNull() ?: 0) }.getOrNull()
            } else runCatching { LocalTime.of(tm.groupValues[4].toInt(), tm.groupValues[5].toInt()) }.getOrNull()
        }
        val start = LocalDateTime.of(d, time ?: LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
        if (start < now - (if (time == null) 0 else 0)) continue
        val rest = line.removeRange(span!!.first, span!!.last + 1).let { l -> tm?.let { l.replace(it.value, "") } ?: l }
            .replace(Regex("\\b(at|on|by)\\b", RegexOption.IGNORE_CASE), " ").replace(Regex("[\\s,:;\\-–]+"), " ").trim()
        out.add(Ev(rest.ifBlank { noteTitle.ifBlank { "Event" } }.take(80), start, time != null))
    }
    return out.distinctBy { it.start / 60_000 to it.title }.sortedBy { it.start }.take(3)
}
