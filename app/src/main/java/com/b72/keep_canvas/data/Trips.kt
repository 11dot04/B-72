package com.b72.keep_canvas.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** One journey read out of a ticket: enough to tell you where to be, when, and which status page to look at. */
@Serializable
data class Trip(
    val mode: String,               // train | flight | bus
    val number: String = "",
    val name: String = "",
    val pnr: String = "",
    val from: String = "",
    val fromCode: String = "",
    val to: String = "",
    val toCode: String = "",
    /** Epoch millis; 0 = unknown. */
    val dep: Long = 0,
    val arr: Long = 0,
    /** False when only the day of travel was found. */
    val timeKnown: Boolean = true,
    val cls: String = "",
    val seat: String = "",
    val pax: String = "",
    val fare: String = "",
)

/** Fills what this ticket reading lacks from another reading of the same journey. */
fun Trip.fill(o: Trip?): Trip = if (o == null) this else copy(
    number = number.ifBlank { o.number }, name = name.ifBlank { o.name }, pnr = pnr.ifBlank { o.pnr },
    from = from.ifBlank { o.from }, fromCode = fromCode.ifBlank { o.fromCode }, to = to.ifBlank { o.to }, toCode = toCode.ifBlank { o.toCode },
    arr = if (arr == 0L && o.arr > dep) o.arr else arr, cls = cls.ifBlank { o.cls },
)

/** Where a trip is in time, which decides what the app offers: the PNR early on, live tracking once you are on your way. */
enum class Phase { Early, Soon, Boarding, Running, Done }

private const val H = 3_600_000L

fun Trip.phase(now: Long = System.currentTimeMillis()): Phase {
    if (dep == 0L) return Phase.Early
    val end = if (arr > dep) arr else dep + 8 * H
    return when {
        now > end + 2 * H -> Phase.Done
        now >= dep -> Phase.Running
        dep - now <= 4 * H -> Phase.Boarding
        dep - now <= 48 * H -> Phase.Soon
        else -> Phase.Early
    }
}

/** A button on a trip card: open [url], or first copy [copy] to the clipboard (the official PNR page cannot be prefilled). */
class TripLink(val label: String, val url: String, val copy: String? = null)

fun Trip.pnrLink(): TripLink? = when {
    mode == "train" && pnr.isNotBlank() -> TripLink("PNR status", "https://www.railyatri.in/pnr-status/$pnr")
    else -> null
}

fun Trip.officialLink(): TripLink? =
    if (mode == "train" && pnr.isNotBlank()) TripLink("IRCTC page", "https://www.indianrail.gov.in/enquiry/PNR/PnrEnquiry.html", copy = pnr) else null

fun Trip.liveLink(): TripLink? = when {
    mode == "train" && number.isNotBlank() -> TripLink("Live status", "https://www.railyatri.in/live-train-status/$number")
    mode == "flight" && number.isNotBlank() -> TripLink("Flight status", "https://www.flightradar24.com/data/flights/${number.lowercase()}")
    else -> null
}

/** The buttons worth showing right now, most useful first. */
fun Trip.links(phase: Phase): List<TripLink> = when (phase) {
    Phase.Early, Phase.Soon -> listOfNotNull(pnrLink() ?: liveLink(), liveLink().takeIf { pnrLink() != null && phase == Phase.Soon }, officialLink())
    Phase.Boarding -> listOfNotNull(pnrLink(), liveLink(), officialLink())
    Phase.Running -> listOfNotNull(liveLink(), pnrLink())
    Phase.Done -> emptyList()
}

val Trip.route: String get() = listOf(fromCode.ifBlank { from }, toCode.ifBlank { to }).filter { it.isNotBlank() }.joinToString(" → ")

fun Block.trip(): Trip? = (data["trip"] as? JsonObject)?.let { runCatching { AppJson.decodeFromJsonElement<Trip>(it as JsonElement) }.getOrNull() }

private fun hashOf(b: Block) = (b.title + "|" + b.body + "|" + (b.data["ocr"] as? kotlinx.serialization.json.JsonPrimitive)?.content).hashCode()

/** True until the block's text has been looked at for a ticket (and again whenever the text changes). */
fun tripStale(b: Block): Boolean = b.dbl("tripH") != hashOf(b).toDouble() && (b.body.length + b.title.length + (b.str("ocr")?.length ?: 0)) >= 20

fun Block.tripText(): String = listOf(title, body, str("ocr").orEmpty()).filter { it.isNotBlank() }.joinToString("\n")

fun tripHash(b: Block) = hashOf(b)

object Trips {
    private val IC = RegexOption.IGNORE_CASE
    private const val MON = "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*"
    private val DATE = Regex("""\b(\d{1,2})[-/ .]($MON|\d{1,2})[-/ .,]*(\d{2,4})\b""", IC)
    private val TIME = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)\s*(am|pm|hrs)?\b""", IC)
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** Cheap test for "is this text about a journey", used to decide whether the language model is worth asking. */
    fun looksLikeTicket(t: String): Boolean =
        Regex("""\bPNR\b|\bIRCTC\b|train\s*(no|number)|\bflight\b|boarding\s*pass|e-?ticket|\bTRN\b|booking\s*(ref|id)""", IC).containsMatchIn(t)

    private fun toDate(m: MatchResult): LocalDate? {
        val (d, mo, y) = m.destructured
        val month = mo.toIntOrNull() ?: (MONTHS.indexOf(mo.lowercase().take(3)) + 1).takeIf { it > 0 } ?: return null
        val year = y.toInt().let { if (it < 100) it + 2000 else it }
        return runCatching { LocalDate.of(year, month, d.toInt()) }.getOrNull()
    }

    private fun toTime(m: MatchResult): Pair<Int, Int> {
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val suffix = m.groupValues[3].lowercase()
        if (suffix == "pm" && h < 12) h += 12
        if (suffix == "am" && h == 12) h = 0
        return h to min
    }

    private fun millis(d: LocalDate, hm: Pair<Int, Int>?): Long =
        LocalDateTime.of(d, java.time.LocalTime.of(hm?.first ?: 0, hm?.second ?: 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** First date + time found within 90 characters after one of the labels. */
    private fun after(t: String, labels: List<String>): Pair<LocalDate?, Pair<Int, Int>?>? {
        for (lab in labels) for (m in Regex(lab, IC).findAll(t)) {
            val seg = t.substring(m.range.last + 1, minOf(t.length, m.range.last + 91))
            val dm = DATE.find(seg)?.let { toDate(it) }
            val tm = TIME.find(seg)?.let { toTime(it) }
            if (dm != null || tm != null) return dm to tm
        }
        return null
    }

    private fun journeyDate(t: String): LocalDate? {
        Regex("""(?:Date of Journey|Journey Date|Travel Date|Start Date|DOJ)\*?\s*[:\-]?\s*""", IC).find(t)?.let { m ->
            DATE.find(t.substring(m.range.last + 1, minOf(t.length, m.range.last + 31)))?.let { d -> toDate(d)?.let { return it } }
        }
        return null
    }

    /** Reads a ticket or confirmation (an IRCTC slip or SMS, an airline email) out of text. Null when it is not a journey. */
    fun parse(text: String): Trip? {
        val t = text.replace(Regex("""[ \t]+"""), " ")
        var mode: String? = null
        var pnr = ""
        Regex("""PNR\s*(?:No\.?|Number|#)?\s*[:\-]?\s*(\d{10})\b""", IC).find(t)?.let { pnr = it.groupValues[1]; mode = "train" }
        if (pnr.isEmpty()) {
            Regex("""(?:PNR|booking\s*(?:ref(?:erence)?|code)|confirmation\s*(?:code|number)|record\s*locator)[^A-Z0-9\n]{0,12}\b([A-Z0-9]{6})\b""", IC).find(t)?.let {
                if (it.groupValues[1].any { c -> c.isLetter() }) { pnr = it.groupValues[1].uppercase(); mode = "flight" }
            }
        }
        var number = ""
        var name = ""
        val tm = Regex("""Train\s*(?:No\.?)?\s*(?:/\s*Name)?\s*[:\-]?\s*(\d{5})\s*(?:/|-|–)?\s*([A-Z][A-Z0-9 .&\-]{2,34})?""", IC).find(t)
            ?: Regex("""\bTRN\s*[:\-]?\s*(\d{5})\b""", IC).find(t)
        if (tm != null) {
            number = tm.groupValues[1]
            name = tm.groupValues.getOrNull(2).orEmpty().trim()
            mode = "train"
        }
        if (number.isEmpty()) {
            val fm = Regex("""\b(?:flight|flt)\s*(?:no\.?|number|#)?\s*[:\-]?\s*([A-Z][A-Z0-9]|[0-9][A-Z])\s?(\d{2,4})\b""", IC).find(t)
                ?: Regex("""\b([A-Z][A-Z0-9]|[0-9][A-Z])\s?(\d{3,4})\b(?=[^\n]{0,60}(?:→|->|\([A-Z]{3}\)))""").find(t)
            if (fm != null) { number = (fm.groupValues[1] + fm.groupValues[2]).uppercase(); mode = "flight" }
        }
        val kind = mode ?: return null

        var from = ""; var fromCode = ""; var to = ""; var toCode = ""
        val stop = """(?=\s+(?:To|Reservation|Boarding|Scheduled|Date|Class|Quota|Departure|Arrival|Passenger|Distance)\b|\n|$|\s{2,})"""
        val name1 = """([A-Za-z][A-Za-z .&'\-]{2,38}?)\s*(?:\(([A-Z]{2,5})\))?"""
        Regex("""\b(?:From|Boarding(?:\s*At)?|Departure\s*Station)\s*[:\-]?\s*$name1$stop""", IC).find(t)?.let { from = it.groupValues[1].trim(); fromCode = it.groupValues[2] }
        Regex("""\b(?:To|Reservation\s*Upto|Destination|Arrival\s*Station)\s*[:\-]?\s*$name1$stop""", IC).find(t)?.let { to = it.groupValues[1].trim(); toCode = it.groupValues[2] }
        if (from.isEmpty()) {
            Regex("""\(([A-Z]{3})\)[^\n(]{0,30}?(?:→|->|to|–|-)[^\n(]{0,30}?\(([A-Z]{3})\)""").find(t)?.let { from = it.groupValues[1]; fromCode = from; to = it.groupValues[2]; toCode = to }
        }
        if (from.isEmpty()) {
            Regex("""\b([A-Z]{2,5})\s*(?:-|–|→|->|to)\s*([A-Z]{2,5})\b""").find(t)?.let { from = it.groupValues[1]; fromCode = from; to = it.groupValues[2]; toCode = to }
        }

        var dep = 0L
        var timeKnown = true
        val depLabels = listOf("""Scheduled\s*Departure\*?""", """\bDeparture\*?""", """\bDeparts?\b""", """\bDep(?:art)?\.?\b""", """Boarding\s*Time""")
        val got = after(t, depLabels)
        val day = got?.first ?: journeyDate(t) ?: DATE.find(t)?.let { toDate(it) }
        if (got?.second != null && day != null) dep = millis(day, got.second)
        if (dep == 0L) {
            val dp = Regex("""\b(?:DP|Dep)\s*[:\-]?\s*(\d{1,2}):(\d{2})""", IC).find(t)
            if (day != null && dp != null) dep = millis(day, dp.groupValues[1].toInt() to dp.groupValues[2].toInt())
        }
        if (dep == 0L && day != null) { dep = millis(day, null); timeKnown = false }
        if (dep == 0L) return null

        var arr = 0L
        after(t, listOf("""Scheduled\s*Arrival\*?""", """\bArrival\*?""", """\bArrives?\b""", """\bArr\.?\b"""))?.let { (d, hm) ->
            if (d != null && hm != null) arr = millis(d, hm)
        }
        if (arr in 1 until dep) arr = 0

        val cls = Regex("""Class\s*[:\-]?\s*(?:[A-Z ]*?\()?(1A|2A|3A|3E|SL|CC|EC|2S|FC|EA)\)?""", IC).find(t)?.groupValues?.get(1)?.uppercase().orEmpty()
        val seat = Regex("""\b(CNF|RAC|RLWL|PQWL|RSWL|GNWL|WL)\s*/?\s*([A-Z]?\d{1,3})?\s*/?\s*(\d{1,3})?\s*/?\s*(LOWER|MIDDLE|UPPER|SIDE LOWER|SIDE UPPER)?""").find(t)?.value?.trim().orEmpty()
        val fare = Regex("""(?:Ticket\s*Fare|Total\s*Fare|Fare|Amount)\s*[:\-]?\s*(?:Rs\.?|INR|₹)?\s*(\d[\d,]*(?:\.\d+)?)""", IC).find(t)?.groupValues?.get(1).orEmpty()
        val pax = Regex("""(?:Passenger(?:\s*Name)?|Name)\s*[:\-]\s*([A-Z][A-Za-z .]{2,40})""").find(t)?.groupValues?.get(1)?.trim().orEmpty()
        if (pnr.isEmpty() && number.isEmpty()) return null
        return Trip(kind, number, name, pnr, from, fromCode, to, toCode, dep, arr, timeKnown, cls, seat, pax, fare)
    }

    /** Every journey in the notes, soonest first, with the note that holds it. */
    fun all(notes: List<Note>): List<Pair<Note, Trip>> =
        notes.flatMap { n -> n.blocks.mapNotNull { b -> b.trip()?.let { n to it } } }.sortedBy { it.second.dep }

    /** The journey to show on the home screen: running, or coming up within ten days. */
    fun next(notes: List<Note>, now: Long = System.currentTimeMillis()): Pair<Note, Trip>? =
        all(notes).firstOrNull { (_, t) -> t.phase(now) != Phase.Done && t.dep - now < 10 * 24 * H }
}
