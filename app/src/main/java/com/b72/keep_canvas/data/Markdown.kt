package com.b72.keep_canvas.data

/** A markdown file turned into what a note holds: a title, the words, their formatting (as the note stores it) and any task-list rows. */
class MdResult(val title: String, val text: String, val spans: String, val tasks: List<String>)

/**
 * Reads markdown into the note's own formatting: headings, bold, italic, strike, highlight, inline code, fenced code, quotes, bullet and numbered
 * lists (nested ones indented), rules and links. `- [ ]` task lines become checklist rows. Anything it does not know is kept as plain words.
 */
object Markdown {
    private val inline = listOf(
        "cd" to Regex("`([^`\n]+)`"),
        "b" to Regex("\\*\\*([^*\n]+)\\*\\*"), "b" to Regex("__([^_\n]+)__"),
        "s" to Regex("~~([^~\n]+)~~"), "hl" to Regex("==([^=\n]+)=="),
        "i" to Regex("(?<![*\\w])\\*([^*\\s][^*\n]*?)\\*(?![*\\w])"), "i" to Regex("(?<![_\\w])_([^_\\s][^_\n]*?)_(?![_\\w])"),
        "ln" to Regex("!?\\[([^\\]\n]*)\\]\\(([^)\\s]+)[^)]*\\)"),
    )

    fun parse(src: String, fallbackTitle: String): MdResult {
        var lines = src.replace("\r\n", "\n").replace('\r', '\n').lines()
        // front matter between two --- lines at the very top
        if (lines.firstOrNull()?.trim() == "---") {
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (end >= 0) lines = lines.drop(end + 2)
        }
        val out = StringBuilder()
        val spans = ArrayList<String>()
        val tasks = ArrayList<String>()
        var title = ""
        var fenced = false
        var fenceIn = 0
        var blank = true   // nothing yet, or the last line was blank

        fun span(s: Int, e: Int, c: String) { if (e > s) spans += "$s,$e,$c" }

        /** Writes one line's words with their inline formats; returns where it started. */
        fun words(line: String): Int {
            val start = out.length
            var rest = line
            while (rest.isNotEmpty()) {
                var best: Triple<String, MatchResult, Int>? = null
                for ((code, re) in inline) {
                    val m = re.find(rest) ?: continue
                    if (best == null || m.range.first < best.second.range.first) best = Triple(code, m, 0)
                }
                if (best == null) { out.append(rest); break }
                val (code, m, _) = best
                out.append(rest, 0, m.range.first)
                if (code == "ln") {
                    val label = m.groupValues[1].ifBlank { m.groupValues[2] }
                    out.append(label)
                    if (m.groupValues[1].isNotBlank() && !m.value.startsWith("!")) out.append(" (").append(m.groupValues[2]).append(")")
                } else {
                    val s = out.length
                    out.append(m.groupValues[1])
                    span(s, out.length, code)
                }
                rest = rest.substring(m.range.last + 1)
            }
            return start
        }

        fun endLine() { out.append('\n') }

        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                fenced = !fenced
                if (fenced) fenceIn = raw.length - raw.trimStart().length   // a block inside a list item is indented as far as its fence is
                continue
            }
            if (fenced) {
                // the fence's own indentation is not part of the code (CommonMark); a blank line stays a line of the block, so the panel has no gap
                var cut = 0
                while (cut < fenceIn && cut < raw.length && raw[cut] == ' ') cut++
                val body = raw.substring(cut).ifEmpty { " " }
                val s = out.length; out.append(body.trimEnd().ifEmpty { " " }); span(s, out.length, "pk"); endLine(); blank = false
                continue
            }
            if (line.isBlank()) { if (!blank) { endLine(); blank = true }; continue }
            val t = line.trimStart()
            val lead = line.length - t.length
            val level = (lead / 2).coerceIn(0, 4)
            fun nest(s: Int) { if (level > 0) span(s, out.length, "pi$level") }
            when {
                Regex("^#{1,6} +").containsMatchIn(t) -> {
                    val h = Regex("^(#{1,6}) +(.*)$").find(t)!!
                    val depth = h.groupValues[1].length
                    if (title.isEmpty() && depth == 1 && out.isBlank()) { title = h.groupValues[2].trim() }
                    else { val s = words(h.groupValues[2].trim()); span(s, out.length, if (depth == 1) "h1" else "h2"); endLine(); blank = false }
                }
                Regex("^([-*_] *){3,}$").matches(t) -> { val s = out.length; out.append("* * *"); span(s, out.length, "pc"); endLine(); blank = false }
                t.startsWith("> ") || t == ">" -> { val s = words(t.removePrefix(">").trim()); span(s, out.length, "pq"); endLine(); blank = false }
                Regex("^[-*+] +\\[[ xX]\\] +").containsMatchIn(t) -> {
                    val m = Regex("^[-*+] +\\[([ xX])\\] +(.*)$").find(t)!!
                    tasks += (if (m.groupValues[1] != " ") "x " else "") + (if (level > 0) "\t" else "") + m.groupValues[2].trim()
                }
                Regex("^[-*+] +").containsMatchIn(t) -> { val s = out.length; out.append("• "); words(t.replace(Regex("^[-*+] +"), "")); nest(s); endLine(); blank = false }
                Regex("^\\d{1,3}[.)] +").containsMatchIn(t) -> {
                    val m = Regex("^(\\d{1,3})[.)] +(.*)$").find(t)!!
                    val s = out.length; out.append(m.groupValues[1]).append(". "); words(m.groupValues[2]); nest(s); endLine(); blank = false
                }
                else -> { words(t); endLine(); blank = false }
            }
        }
        val text = out.toString().trimEnd()
        val kept = spans.filter { it.split(',')[0].toInt() < text.length }
            .map { p -> val a = p.split(','); "${a[0]},${minOf(a[1].toInt(), text.length)},${a[2]}" }
        return MdResult(title.ifBlank { fallbackTitle }, text, kept.joinToString(";"), tasks)
    }
}
