package com.b72.keep_canvas.data

class Fact(val label: String, val value: String, val star: Boolean = false)
class Poster(val head: String, val big: Fact?, val facts: List<Fact>, val rest: String, val rows: List<Pair<String, String>>)

private val IC = RegexOption.IGNORE_CASE
private val KV = Regex("""^\s*([A-Za-z][A-Za-z &/]{1,22}?)\s*[:=]\s*(.+?)\s*$""")
private val URL = Regex("""(?:https?://|www\.)\S+""")
private val MAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
private const val MON = """(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?"""
private val DATE = Regex(
    """\b\d{1,2}\s?[–-]\s?\d{1,2}\s$MON(?:\s\d{4})?\b|""" +
        """\b\d{1,2}(?:st|nd|rd|th)?\s(?:of\s)?$MON(?:\s\d{4})?\b|""" +
        """\b$MON\s\d{1,2}(?:st|nd|rd|th)?(?:,?\s\d{4})?\b|""" +
        """\b\d{4}[-/]\d{1,2}[-/]\d{1,2}\b|\b\d{1,2}/\d{1,2}/\d{2,4}\b|""" +
        """\b\d{1,2}(?:st|nd|rd|th)\b""", IC)
private const val DAY_P = """\b(?:Mon|Tues?|Wed(?:nes)?|Thu(?:rs)?|Fri|Sat(?:ur)?|Sun)(?:day)?\b"""
private val DAY = Regex(DAY_P, IC)
private val TIME = Regex("""\b\d{1,2}(?::\d{2})?\s?(?:[–-]\s?\d{1,2}(?::\d{2})?\s?)?(?:am|pm)\b|\b\d{1,2}:\d{2}\b""", IC)
private val MONEY = Regex("""[$€£₹]\s?\d[\d,]*(?:\.\d+)?|\b\d[\d,]*(?:\.\d+)?\s?(?:USD|EUR|INR|GBP)\b""")
private val PCT = Regex("""\b\d+(?:\.\d+)?\s?%""")
private val UNIT = Regex("""\b\d[\d,.]*\s?(?:km|kg|mins?|hrs?|hours?|ms|MB|GB|cm|kcal|reps|pages?|miles?|°C|°F)\b""", IC)
private val PHONE = Regex("""\+?\d[\d\s().-]{8,}\d""")
private val PLACE = Regex("""(?:\b(?:at|in|to|near|from)|@)\s+((?:the\s+)?[A-Z][\w']+(?:\s+[A-Z][\w']+){0,3})""")
private val NOT_PLACE = setOf("i", "the", "a", "an", "it", "this", "that", "my", "we", "you", "he", "she", "they", "me", "us", "our", "your", "will", "would", "can", "could", "shall", "should", "may", "might", "must", "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does", "did", "going", "then", "also", "and", "but", "so", "if", "when", "where", "there", "here")

private fun cap(s: String) = if (s.isEmpty()) s else s[0].uppercase() + s.substring(1)

private fun unitLabel(s: String): String {
    val u = s.lowercase().replace(Regex("""[\d,.\s]"""), "")
    return mapOf("km" to "Distance", "mile" to "Distance", "miles" to "Distance", "kg" to "Weight", "ms" to "Speed", "mb" to "Size", "gb" to "Size", "cm" to "Length", "kcal" to "Energy", "reps" to "Reps", "page" to "Pages", "pages" to "Pages", "°c" to "Temp", "°f" to "Temp")[u] ?: "Duration"
}

private fun findPlace(t: String): String? {
    for (m in PLACE.findAll(t)) {
        val words = m.groupValues[1].trim().split(Regex("""\s+""")).toMutableList()
        while (words.size > 1 && words.last().lowercase() in NOT_PLACE) words.removeAt(words.lastIndex)
        val first = words.first().lowercase()
        if (first in NOT_PLACE) continue
        if (Regex("^$MON$", IC).matches(first) || Regex("^$DAY_P$", IC).matches(first)) continue
        return words.joinToString(" ")
    }
    return null
}

private fun tidyHead(s: String): String {
    var h = s.replace(Regex("""\s+"""), " ").replace(Regex("""^[\s,.:;@–—-]+|[\s,.:;@–—-]+$"""), "")
    val tail = Regex("""\s+(?:at|on|from|by|the|a|an|to|in|of|for|and|with|near)$""", IC)
    while (tail.containsMatchIn(h)) h = h.replaceFirst(tail, "")
    return cap(h.replace(Regex("""[\s,.:;–—-]+$"""), "").trim())
}

/** Reads free text and pulls out what a poster shows: headline, one big figure, labelled facts, ledger rows, leftover text. */
fun parsePoster(title: String, text: String): Poster {
    val facts = mutableListOf<Fact>()
    val keep = mutableListOf<String>()
    fun add(l: String, v: String) { if (v.isNotBlank() && facts.none { it.label == l }) facts.add(Fact(l, v.trim())) }

    for (raw in text.split('\n')) {
        val line = raw.replaceFirst(Regex("""^\s*(?:[-•*>]+|\d+[.)])\s+"""), "")
        val m = KV.find(line)
        if (m != null && line.length < 90 && !m.groupValues[2].startsWith("//")) add(cap(m.groupValues[1].trim()), m.groupValues[2]) else keep.add(line)
    }

    val rows = mutableListOf<Pair<String, String>>()
    val other = mutableListOf<String>()
    for (l in keep) {
        val ms = MONEY.findAll(l).toList()
        if (ms.size == 1 && l.trim().length < 60) {
            val lab = l.replaceFirst(ms[0].value, "").replace(Regex("""\s+"""), " ").trim()
            if (lab.length >= 2) { rows.add(lab to ms[0].value.trim()); continue }
        }
        other.add(l)
    }
    var t = (if (rows.size >= 2) other else keep).joinToString("\n")
    if (rows.size < 2) rows.clear()

    fun take(r: Regex): String? {
        val m = r.find(t) ?: return null
        t = t.replaceFirst(m.value, " ")
        return m.value.trim()
    }
    // every date in the text (an itinerary has several), not just the first
    fun takeAll(r: Regex, max: Int): List<String> {
        val out = mutableListOf<String>()
        while (out.size < max) out.add(take(r) ?: break)
        return out
    }

    val url = take(URL); val mail = take(MAIL)
    if (url != null) add("Link", url)
    if (mail != null) add("Email", mail)
    val dates = takeAll(DATE, 3); val day = take(DAY); val time = take(TIME)
    add("When", listOfNotNull(day, dates.takeIf { it.isNotEmpty() }?.joinToString(" & "), time).joinToString(" · "))
    val amounts = MONEY.findAll(t).toList()
    if (amounts.isNotEmpty()) {
        fun v(m: MatchResult) = m.value.replace(Regex("""[^\d.]"""), "").toDoubleOrNull() ?: 0.0
        val top = amounts.maxByOrNull { v(it) }!!
        t = t.replaceFirst(top.value, " ")
        add("Amount", top.value.trim())
    }
    val pct = take(PCT); val unit = take(UNIT)
    if (pct != null) add("Percent", pct)
    if (unit != null) add(unitLabel(unit), unit)
    val ph = take(PHONE)
    if (ph != null && ph.count { it.isDigit() } >= 9) add("Phone", ph)

    val lines = t.split('\n').map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }.toMutableList()
    var head = title.trim()
    if (head.isEmpty() && lines.isNotEmpty()) {
        val first = lines[0]
        val clause = Regex("""^(.{6,56}?)(?:[.!?:;—–]|,)(?:\s|$)""").find(first)
        if (first.length <= 56) head = lines.removeAt(0)
        else if (clause != null) {
            head = clause.groupValues[1]
            lines[0] = first.substring(clause.value.length).trim()
            if (lines[0].isEmpty()) lines.removeAt(0)
        }
    }
    head = tidyHead(head)

    val place = findPlace(head + "\n" + lines.joinToString("\n"))
    if (place != null && !head.lowercase().contains(place.lowercase())) add("Where", place)

    var big: Fact? = null
    for (l in listOf("Amount", "Percent", "Distance", "Weight", "Speed", "Size", "Energy", "Reps", "Pages", "Temp", "Duration")) {
        val i = facts.indexOfFirst { it.label == l }
        if (i >= 0) { big = facts.removeAt(i); break }
    }
    val order = listOf("Where", "When", "Link", "Email", "Phone")
    facts.sortBy { order.indexOf(it.label).let { i -> if (i < 0) 99 else i } }
    return Poster(head, big, facts.take(6), lines.joinToString("\n"), rows.take(8))
}

/**
 * The poster for a block: the plain readers' result, upgraded with what the assistant found (any labels at all,
 * not only the ones the readers know). The fact it marked as the point of the note becomes the big figure.
 */
fun posterFor(title: String, b: Block): Poster {
    val p = parsePoster(title, b.body)
    val ai = b.aiFacts().map { Fact(it.label, it.value, it.star) }
    if (ai.isEmpty()) return p
    val starred = ai.firstOrNull { it.star }
    val others = ai.filter { it !== starred }
    val big = starred ?: p.big
    val old = p.facts + listOfNotNull(p.big.takeIf { starred != null })
    val extra = old.filter { f -> ai.none { it.label.equals(f.label, true) || it.value.equals(f.value, true) } }
    val rest = p.rest.lines().filter { line -> ai.none { line.contains(it.value) } }.joinToString("\n")
    return Poster(p.head, big, (others + extra).take(8), rest, p.rows)
}

fun pairs(body: String): List<Pair<String, String>> = body.lines().filter { it.isNotBlank() }.map {
    val i = it.indexOf(':')
    if (i >= 0) it.substring(0, i).trim() to it.substring(i + 1).trim() else "" to it.trim()
}

/** One line about a template block for tiles; the title is already shown so it is not repeated. */
fun tplSummary(k: Kind, title: String, body: String): String = when (k) {
    Kind.Quote -> "“$body”"
    Kind.Stat, Kind.Link -> body
    Kind.Poster -> {
        val p = parsePoster(title, body)
        buildList {
            p.big?.let { add("${it.label}: ${it.value}") }
            p.facts.take(4).forEach { add("${it.label}: ${it.value}") }
            if (p.facts.isEmpty() && p.big == null && p.rest.isNotEmpty()) add(p.rest.replace('\n', ' '))
        }.joinToString(" · ")
    }
    else -> body.replace("\n", " · ")
}

fun tplHead(k: Kind, title: String, body: String): String = if (k == Kind.Poster) parsePoster(title, body).head else title
