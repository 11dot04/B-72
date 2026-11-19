package com.b72.keep_canvas.data

import android.content.Context
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One thing the assistant suggests: [text], the words of the note it comes from ([quote]) and, for a connection, the other note. */
class BsItem(val text: String, val quote: String? = null, val noteId: String? = null, val noteTitle: String? = null)

/** What a brainstorm gives back: a labelled list of suggestions, or a block of prose, or a short reason why there is nothing. */
class BsResult(val section: String, val items: List<BsItem> = emptyList(), val prose: String? = null, val note: String? = null)

enum class BsKind(val label: String, val section: String) {
    Next("Next steps", "NEXT STEPS"),
    Questions("Questions to explore", "QUESTIONS TO EXPLORE"),
    Connect("Connect the dots", "CONNECTIONS"),
    Shorter("Say it shorter", "SHORTER VERSION"),
}

/**
 * The thinking partner. Each task has its own tight prompt: it must stay with the note's own words, name the specific thing it is about,
 * refuse generic advice, and answer as JSON so the app can show suggestions one by one, each insertable on its own. "Connect the dots"
 * is shown the few notes that look closest to this one and may only link what it can quote on both sides.
 */
object Brainstorm {
    private const val ROLE = "You are a sharp, honest editor reading one person's private note. Stay with what the note actually says: every suggestion must point at a specific word, name, number, claim or plan in it. Never give generic advice that would fit any note. Write in plain language, in the note's own language. "

    private fun noteText(n: Note) = n.blocks.joinToString("\n") { it.tripText() }.trim()

    suspend fun run(ctx: Context, kind: BsKind, note: Note, all: List<Note>): BsResult? {
        val text = noteText(note).take(3500)
        if (text.length < 20) return BsResult(kind.section, note = "There is not enough in this note yet. Write a little more and ask again.")
        return when (kind) {
            BsKind.Next -> items(kind, ctx, ROLE + """Give 3 to 5 concrete next steps for the writer. Each step starts with a verb, names the specific thing in the note it is about (a person, place, number, date or claim) and can be started today. Skip anything vague such as "do more research" or "stay organised". If the note has nothing to act on, return an empty list.
Reply with ONLY JSON: {"items":[{"t":"the step, at most 22 words","q":"the exact words from the note it comes from, at most 12 words"}]}
Note:
${'"'}${'"'}${'"'}
$text
${'"'}${'"'}${'"'}""", text)
            BsKind.Questions -> items(kind, ctx, ROLE + """Ask 3 to 5 questions that this note raises but does not answer: a gap in the argument, an assumption that is not tested, a claim without a source, a decision without a reason. Each question must point at one specific thing in the note, be open (not yes or no) and not be answerable by a dictionary. Never ask "why is this important".
Reply with ONLY JSON: {"items":[{"t":"the question","q":"the exact words from the note it is about, at most 12 words"}]}
Note:
${'"'}${'"'}${'"'}
$text
${'"'}${'"'}${'"'}""", text)
            BsKind.Connect -> connect(ctx, note, all, text)
            BsKind.Shorter -> {
                val r = Assistant.generate(ctx, ROLE + "Rewrite this note in at most half the words. Keep every fact, name, number and date, the writer's voice and their person (I stays I). No preamble, no commentary, only the rewrite.\nNote:\n\"\"\"\n$text\n\"\"\"") ?: return null
                BsResult(kind.section, prose = r)
            }
        }
    }

    /** A free question about the note: answered from it first, and honest about what it does not say. */
    suspend fun ask(ctx: Context, note: Note, question: String): BsResult? {
        val text = noteText(note).take(3500)
        val r = Assistant.generate(ctx, ROLE + "Answer the question using the note. If the note does not say, say so in one sentence and then give your best general answer, marked \"Beyond the note:\". At most 120 words, no preamble.\nNote:\n\"\"\"\n$text\n\"\"\"\nQuestion: $question") ?: return null
        return BsResult("ASKED: " + question.trim().take(40).uppercase(), prose = r)
    }

    private suspend fun items(kind: BsKind, ctx: Context, prompt: String, noteText: String): BsResult? {
        val reply = Assistant.generate(ctx, prompt, json = true) ?: return null
        val list = parse(reply).map { (t, q, _) -> BsItem(t, q?.takeIf { grounded(it, noteText) }) }.filter { it.text.length in 6..260 }.take(5)
        return if (list.isEmpty()) BsResult(kind.section, note = "Nothing worth adding. This note either says it all or is too thin to push on yet.") else BsResult(kind.section, list)
    }

    /** The notes that look closest to this one, and what this one has in common with them: only links that can be quoted on both sides. */
    private suspend fun connect(ctx: Context, note: Note, all: List<Note>, text: String): BsResult? {
        val cands = Related.rank(all, note, k = 6, vector = null, floorOverride = .025f)
        if (cands.isEmpty()) return BsResult(BsKind.Connect.section, note = "No other note shares enough with this one yet to connect to.")
        val shown = cands.mapIndexed { i, c -> "[${i + 1}] ${c.title.ifBlank { "Untitled" }}: ${noteText(c).replace('\n', ' ').take(300)}" }.joinToString("\n")
        val prompt = ROLE + """Find real connections between THIS NOTE and the OTHER NOTES: the same person, place, project, source or number, or an idea in one that supports, contradicts, answers or extends an idea in the other. A connection must be stated by quoting both sides. Sharing a general topic is NOT a connection. Give at most 4, best first. If there is none, return an empty list.
Reply with ONLY JSON: {"items":[{"q":"the exact words from THIS NOTE, at most 12 words","n":the number of the other note,"t":"one sentence saying how they connect, naming the specific thing they share, at most 30 words"}]}
THIS NOTE:
${'"'}${'"'}${'"'}
$text
${'"'}${'"'}${'"'}
OTHER NOTES:
$shown"""
        val reply = Assistant.generate(ctx, prompt, json = true) ?: return null
        val list = parse(reply).mapNotNull { (t, q, n) ->
            val other = n?.toIntOrNull()?.let { cands.getOrNull(it - 1) } ?: return@mapNotNull null
            if (t.length < 10) null else BsItem(t, q?.takeIf { grounded(it, text) }, other.id, other.title.ifBlank { "Untitled" })
        }.take(4)
        return if (list.isEmpty()) BsResult(BsKind.Connect.section, note = "Nothing here truly connects to your other notes yet. It would rather say so than invent a link.")
        else BsResult(BsKind.Connect.section, list)
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun grounded(q: String, text: String): Boolean = q.length >= 4 && norm(text).contains(norm(q).take(60))

    /** (text, quote, other) from a JSON reply; a reply that is not JSON is read as lines. */
    private fun parse(reply: String): List<Triple<String, String?, String?>> {
        val a = reply.indexOf('{'); val z = reply.lastIndexOf('}')
        val el = runCatching { AppJson.parseToJsonElement(if (a >= 0 && z > a) reply.substring(a, z + 1) else reply.substring(reply.indexOf('[').coerceAtLeast(0), reply.lastIndexOf(']') + 1)) }.getOrNull()
        val arr = (el as? JsonObject)?.get("items") as? JsonArray ?: el as? JsonArray
        if (arr != null) return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            val t = s("t") ?: return@mapNotNull null
            Triple(t, s("q"), s("n"))
        }
        return reply.lines().map { it.trim().trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "") }.filter { it.length > 15 }.take(5).map { Triple(it, null, null) }
    }
}
