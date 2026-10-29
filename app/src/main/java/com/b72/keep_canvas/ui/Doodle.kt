package com.b72.keep_canvas.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Note
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hand-drawn marks, in the bold felt-tip look of the Keep concept: every mark is a few strokes in a unit box,
 * drawn with a fat round pen and a little wobble that is fixed by a seed (so a mark never shimmers).
 *
 * Two families: [Mark]s are the annotations a person places on a canvas (arrows, circles, crosses…),
 * [Pic]s are little pictures that stand for what a note is about (a cup for coffee, a plane for a flight).
 */

enum class Mark(val label: String, val aspect: Float) {
    Arrow("Arrow", 1f), Swirl("Loop arrow", 1f), Circle("Circle", 1f), Cross("Cross", 1f), Check("Tick", 1f),
    Underline("Underline", .32f), Squiggle("Squiggle", .32f), Box("Box", .75f), Scribble("Scribble", .55f),
    Star("Star", 1f), Heart("Heart", 1f), Sparkle("Sparkle", 1f), Bang("Bang", 1f),
}

/** The pen colours, in the order a mark cycles through them. */
val MarkColors = listOf(Color(0xFFFFC928), Color(0xFFFF6B6B), Color(0xFF8E4DFF), Color(0xFF2B1B34), Color(0xFF3DBE7E))

private class Line(val pts: FloatArray, val smooth: Boolean = true, val closed: Boolean = false, val bold: Float = 1f)

private fun ln(smooth: Boolean, vararg v: Float) = Line(v, smooth)
private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, n: Int = 12): Line {
    val a = FloatArray(n * 2)
    for (i in 0 until n) { val t = i * 2 * PI.toFloat() / n; a[i * 2] = cx + rx * cos(t); a[i * 2 + 1] = cy + ry * sin(t) }
    return Line(a, true, true)
}

private fun noise(seed: Int, k: Int): Float {
    var h = seed * 31 + k * 17 + 7
    h = h xor (h shl 13); h = h xor (h ushr 17); h = h xor (h shl 5)
    return ((h and 0xffff) / 32767.5f) - 1f
}

private fun pathOf(l: Line, x: Float, y: Float, w: Float, h: Float, seed: Int, wob: Float): Path {
    val n = l.pts.size / 2
    val p = ArrayList<Offset>(n)
    for (i in 0 until n) p.add(Offset(x + (l.pts[i * 2] + noise(seed, i * 2) * wob) * w, y + (l.pts[i * 2 + 1] + noise(seed, i * 2 + 1) * wob) * h))
    val path = Path()
    if (n == 0) return path
    path.moveTo(p[0].x, p[0].y)
    if (!l.smooth || n < 3) {
        for (i in 1 until n) path.lineTo(p[i].x, p[i].y)
        if (l.closed) path.close()
        return path
    }
    fun at(i: Int): Offset = if (l.closed) p[(i % n + n) % n] else p[i.coerceIn(0, n - 1)]
    val last = if (l.closed) n else n - 1
    for (i in 0 until last) {
        val p0 = at(i - 1); val p1 = at(i); val p2 = at(i + 1); val p3 = at(i + 2)
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    if (l.closed) path.close()
    return path
}

private fun DrawScope.strokes(ls: List<Line>, x: Float, y: Float, w: Float, h: Float, color: Color, seed: Int, pen: Float, wob: Float = .012f) {
    ls.forEachIndexed { i, l ->
        drawPath(pathOf(l, x, y, w, h, seed + i * 101, wob), color, style = Stroke(width = pen * l.bold, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A bold arrow whose shaft swells in the middle and thins at both ends, with an open two-stroke head. */
private fun DrawScope.taperedArrow(c: List<Offset>, color: Color, pen: Float, headLen: Float) {
    fun at(t: Float): Offset {
        val v = 1f - t
        return c[0] * (v * v * v) + c[1] * (3 * v * v * t) + c[2] * (3 * v * t * t) + c[3] * (t * t * t)
    }
    var prev = at(0f)
    val n = 40
    for (i in 1..n) {
        val t = i / n.toFloat() * .97f
        val cur = at(t)
        val k = sin(PI.toFloat() * (t / .97f * .8f + .1f))
        drawLine(color, prev, cur, pen * (.5f + .5f * k), StrokeCap.Round)
        prev = cur
    }
    val tip = c[3]
    val back = at(.9f)
    val h = atan2(tip.y - back.y, tip.x - back.x)
    for (da in floatArrayOf(.55f, -.55f)) {
        val e = tip - Offset(cos(h + da), sin(h + da)) * headLen
        drawLine(color, tip, e, pen * .95f, StrokeCap.Round)
    }
}

/** Draws [m] with its top-left corner at ([x], [y]) and the given [w]idth; its height follows the mark's aspect. */
fun DrawScope.drawMark(m: Mark, x: Float, y: Float, w: Float, color: Color, seed: Int) {
    val h = w * m.aspect
    val pen = w * .085f
    when (m) {
        Mark.Arrow -> taperedArrow(
            listOf(Offset(x + w * .06f, y + h * .9f), Offset(x + w * .12f, y + h * .25f), Offset(x + w * .6f, y + h * .06f), Offset(x + w * .93f, y + h * .3f)),
            color, pen * 1.15f, w * .26f,
        )
        Mark.Swirl -> {
            strokes(listOf(ln(true, .08f, .88f, .2f, .5f, .45f, .22f, .72f, .3f, .6f, .52f, .4f, .36f, .55f, .1f, .92f, .16f)), x, y, w, h, color, seed, pen)
            val tip = Offset(x + w * .92f, y + h * .16f)
            val dir = atan2(.06f, .37f)
            for (da in floatArrayOf(.6f, -.6f)) drawLine(color, tip, tip - Offset(cos(dir + da), sin(dir + da)) * w * .22f, pen, StrokeCap.Round)
        }
        Mark.Circle -> {
            val pts = FloatArray(30 * 2)
            for (i in 0 until 30) {
                val t = -2.3f + i / 29f * (2 * PI.toFloat() * 1.12f)
                val r = .4f + .06f * (i / 29f)
                pts[i * 2] = .5f + r * cos(t); pts[i * 2 + 1] = .5f + r * sin(t)
            }
            strokes(listOf(Line(pts, true)), x, y, w, h, color, seed, pen, .008f)
        }
        Mark.Cross -> strokes(
            listOf(ln(true, .18f, .2f, .5f, .47f, .82f, .82f), ln(true, .8f, .18f, .5f, .53f, .2f, .84f)),
            x, y, w, h, color, seed, pen * 1.15f,
        )
        Mark.Check -> strokes(listOf(ln(true, .1f, .56f, .38f, .84f, .92f, .16f)), x, y, w, h, color, seed, pen * 1.3f)
        Mark.Underline -> strokes(listOf(ln(true, .02f, .55f, .3f, .4f, .6f, .6f, .98f, .38f), ln(true, .12f, .9f, .5f, .78f, .9f, .86f)), x, y, w, h, color, seed, pen * .9f, .02f)
        Mark.Squiggle -> {
            val pts = FloatArray(17 * 2)
            for (i in 0 until 17) { pts[i * 2] = .02f + i / 16f * .96f; pts[i * 2 + 1] = .5f + .34f * sin(i / 16f * 2 * PI.toFloat() * 3f) }
            strokes(listOf(Line(pts, true)), x, y, w, h, color, seed, pen * .9f, .004f)
        }
        Mark.Box -> strokes(listOf(Line(floatArrayOf(.05f, .1f, .97f, .06f, .94f, .93f, .04f, .9f, .08f, .04f), false)), x, y, w, h, color, seed, pen, .015f)
        Mark.Scribble -> strokes(
            listOf(Line(floatArrayOf(.04f, .75f, .9f, .22f, .1f, .55f, .94f, .42f, .08f, .34f, .88f, .66f, .2f, .18f, .96f, .85f), false, false, 1.5f)),
            x, y, w, h, color, seed, pen, .01f,
        )
        Mark.Star -> {
            val pts = FloatArray(20)
            for (i in 0 until 10) {
                val t = -PI.toFloat() / 2 + i * PI.toFloat() / 5
                val r = if (i % 2 == 0) .46f else .2f
                pts[i * 2] = .5f + r * cos(t); pts[i * 2 + 1] = .54f + r * sin(t)
            }
            strokes(listOf(Line(pts, false, true)), x, y, w, h, color, seed, pen, .01f)
        }
        Mark.Heart -> {
            val pts = FloatArray(28 * 2)
            for (i in 0 until 28) {
                val t = i / 28f * 2 * PI.toFloat()
                val hx = 16 * sin(t) * sin(t) * sin(t)
                val hy = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
                pts[i * 2] = .5f + hx / 36f; pts[i * 2 + 1] = .46f - hy / 34f
            }
            strokes(listOf(Line(pts, true, true)), x, y, w, h, color, seed, pen, .006f)
        }
        Mark.Sparkle -> strokes(
            listOf(Line(floatArrayOf(.5f, .04f, .58f, .42f, .96f, .5f, .58f, .58f, .5f, .96f, .42f, .58f, .04f, .5f, .42f, .42f), false, true)),
            x, y, w, h, color, seed, pen * .9f, .01f,
        )
        Mark.Bang -> strokes(listOf(ln(true, .5f, .06f, .52f, .4f, .5f, .62f), ln(false, .5f, .86f, .5f, .875f)), x, y, w, h, color, seed, pen * 1.5f, .008f)
    }
}

// ------------------------------------------------------------------ pictures for tiles

enum class Pic { Coffee, Plane, Train, Book, Music, Camera, Leaf, Bulb, Flask, Pin, Check, Home, Clock, Pencil, Mountain, Dumbbell, Coin, Fork, Heart, Star }

private fun picLines(p: Pic): List<Line> = when (p) {
    Pic.Coffee -> listOf(
        ln(true, .2f, .45f, .22f, .78f, .4f, .9f, .6f, .9f, .78f, .78f, .8f, .45f), ln(true, .14f, .45f, .86f, .45f),
        ln(true, .8f, .52f, .95f, .55f, .93f, .72f, .78f, .74f), ln(true, .38f, .32f, .32f, .2f, .4f, .08f), ln(true, .6f, .32f, .54f, .2f, .62f, .08f),
    )
    Pic.Plane -> listOf(Line(floatArrayOf(.05f, .5f, .95f, .1f, .62f, .92f, .5f, .6f, .05f, .5f), false), ln(false, .5f, .6f, .95f, .1f))
    Pic.Train -> listOf(
        Line(floatArrayOf(.2f, .14f, .8f, .14f, .8f, .7f, .2f, .7f, .2f, .14f), false), Line(floatArrayOf(.28f, .24f, .72f, .24f, .72f, .44f, .28f, .44f, .28f, .24f), false),
        ellipse(.33f, .8f, .06f, .06f, 8), ellipse(.67f, .8f, .06f, .06f, 8), ln(false, .1f, .94f, .9f, .94f),
    )
    Pic.Book -> listOf(
        ln(true, .5f, .2f, .1f, .14f, .1f, .78f, .5f, .84f), ln(true, .5f, .2f, .9f, .14f, .9f, .78f, .5f, .84f), ln(false, .5f, .2f, .5f, .84f),
        ln(false, .18f, .35f, .4f, .38f), ln(false, .18f, .5f, .4f, .53f), ln(false, .6f, .38f, .82f, .35f),
    )
    Pic.Music -> listOf(
        ellipse(.28f, .78f, .12f, .09f, 8), ellipse(.7f, .68f, .12f, .09f, 8), ln(false, .4f, .76f, .4f, .2f), ln(false, .82f, .66f, .82f, .12f),
        Line(floatArrayOf(.4f, .2f, .82f, .12f), false, false, 1.5f),
    )
    Pic.Camera -> listOf(
        Line(floatArrayOf(.08f, .3f, .3f, .3f, .36f, .18f, .64f, .18f, .7f, .3f, .92f, .3f, .92f, .82f, .08f, .82f), false, true), ellipse(.5f, .56f, .2f, .2f), ln(false, .8f, .4f, .82f, .4f),
    )
    Pic.Leaf -> listOf(ln(true, .2f, .95f, .35f, .6f, .52f, .24f), Line(floatArrayOf(.52f, .1f, .88f, .3f, .82f, .66f, .46f, .64f, .3f, .4f), true, true), ln(true, .4f, .56f, .6f, .36f, .72f, .3f))
    Pic.Bulb -> listOf(
        ln(true, .34f, .66f, .22f, .5f, .22f, .3f, .36f, .14f, .5f, .1f, .64f, .14f, .78f, .3f, .78f, .5f, .66f, .66f), ln(false, .4f, .74f, .6f, .74f), ln(false, .42f, .84f, .58f, .84f), ln(false, .46f, .93f, .54f, .93f),
        ln(false, .1f, .18f, .18f, .26f), ln(false, .9f, .18f, .82f, .26f),
    )
    Pic.Flask -> listOf(
        Line(floatArrayOf(.4f, .1f, .4f, .4f, .12f, .85f, .88f, .85f, .6f, .4f, .6f, .1f), false), ln(false, .32f, .1f, .68f, .1f), ln(true, .24f, .66f, .4f, .6f, .6f, .7f, .76f, .64f), ellipse(.46f, .76f, .035f, .035f, 6),
    )
    Pic.Pin -> listOf(Line(floatArrayOf(.5f, .95f, .2f, .55f, .22f, .3f, .5f, .08f, .78f, .3f, .8f, .55f), true, true), ellipse(.5f, .36f, .1f, .1f, 8))
    Pic.Check -> listOf(Line(floatArrayOf(.12f, .12f, .88f, .1f, .9f, .88f, .1f, .9f), false, true), ln(true, .28f, .52f, .45f, .72f, .76f, .3f))
    Pic.Home -> listOf(ln(false, .08f, .5f, .5f, .12f, .92f, .5f), ln(false, .2f, .45f, .2f, .88f, .8f, .88f, .8f, .45f), ln(false, .42f, .88f, .42f, .62f, .58f, .62f, .58f, .88f))
    Pic.Clock -> listOf(ellipse(.5f, .5f, .4f, .4f, 14), ln(false, .5f, .5f, .5f, .24f), ln(false, .5f, .5f, .68f, .6f))
    Pic.Pencil -> listOf(Line(floatArrayOf(.15f, .85f, .2f, .65f, .7f, .15f, .88f, .3f, .38f, .8f), false, true), ln(false, .62f, .22f, .8f, .38f))
    Pic.Mountain -> listOf(Line(floatArrayOf(.05f, .85f, .35f, .3f, .5f, .55f, .68f, .2f, .95f, .85f), false, true), ellipse(.82f, .16f, .07f, .07f, 8))
    Pic.Dumbbell -> listOf(ln(false, .2f, .5f, .8f, .5f), ln(false, .2f, .28f, .2f, .72f), ln(false, .1f, .38f, .1f, .62f), ln(false, .8f, .28f, .8f, .72f), ln(false, .9f, .38f, .9f, .62f))
    Pic.Coin -> listOf(ellipse(.5f, .5f, .4f, .4f, 14), ellipse(.5f, .5f, .26f, .26f, 12), ln(true, .5f, .34f, .5f, .66f))
    Pic.Fork -> listOf(ln(false, .24f, .1f, .24f, .4f), ln(false, .38f, .1f, .38f, .4f), ln(true, .24f, .4f, .31f, .46f, .38f, .4f), ln(false, .31f, .46f, .31f, .9f), ln(true, .72f, .1f, .84f, .28f, .72f, .5f), ln(false, .72f, .1f, .72f, .9f))
    Pic.Heart -> emptyList()
    Pic.Star -> emptyList()
}

fun DrawScope.drawPic(p: Pic, x: Float, y: Float, size: Float, color: Color, seed: Int) {
    when (p) {
        Pic.Heart -> drawMark(Mark.Heart, x, y, size, color, seed)
        Pic.Star -> drawMark(Mark.Star, x, y, size, color, seed)
        else -> strokes(picLines(p), x, y, size, size, color, seed, size * .07f)
    }
}

private val WORDS: List<Pair<Pic, List<String>>> = listOf(
    Pic.Coffee to listOf("coffee", "latte", "tea", "cafe", "espresso", "brew", "chai", "mocha"),
    Pic.Plane to listOf("flight", "airport", "plane", "passport", "visa", "boarding", "airline", "layover"),
    Pic.Train to listOf("train", "rail", "pnr", "irctc", "metro", "bus", "platform", "railway"),
    Pic.Book to listOf("book", "novel", "chapter", "library", "author", "reading", "read", "pages", "textbook"),
    Pic.Music to listOf("song", "music", "playlist", "album", "lyrics", "track", "spotify", "band", "concert"),
    Pic.Camera to listOf("photo", "camera", "shoot", "lens", "photography", "portrait"),
    Pic.Leaf to listOf("plant", "garden", "tree", "leaf", "nature", "flower", "soil", "planter"),
    Pic.Bulb to listOf("idea", "brainstorm", "thought", "startup", "concept", "invent"),
    Pic.Flask to listOf("chemistry", "reaction", "lab", "experiment", "mechanism", "science", "physics", "organic", "molecule"),
    Pic.Pin to listOf("place", "map", "address", "location", "hotel", "visit", "city", "route"),
    Pic.Check to listOf("todo", "tasks", "checklist", "groceries", "errands"),
    Pic.Home to listOf("home", "house", "rent", "flat", "apartment", "furniture", "room"),
    Pic.Clock to listOf("deadline", "reminder", "schedule", "meeting", "alarm", "exam", "appointment"),
    Pic.Mountain to listOf("run", "hike", "trek", "trail", "strava", "cycling", "ride", "activity", "marathon", "walk"),
    Pic.Dumbbell to listOf("gym", "workout", "fitness", "exercise", "weights", "training"),
    Pic.Coin to listOf("money", "budget", "expense", "invoice", "salary", "price", "bill", "savings", "pay"),
    Pic.Fork to listOf("recipe", "food", "cook", "dinner", "lunch", "meal", "ingredients", "breakfast", "restaurant"),
    Pic.Heart to listOf("love", "birthday", "gift", "wedding", "anniversary"),
    Pic.Star to listOf("favourite", "favorite", "best", "top"),
    Pic.Pencil to listOf("draft", "essay", "write", "writing", "journal", "diary"),
)

/** The picture that fits a note best: by the words in its tags, title and text, else by what kind of note it is. */
fun picFor(n: Note, seed: Int): Pic? {
    val text = (n.tags + n.title + n.blocks.joinToString(" ") { it.title + " " + it.body + " " + it.checkItems.joinToString(" ") }).lowercase()
    val words = Regex("""[a-z]+""").findAll(text).map { it.value }.toList()
    var best: Pic? = null
    var bestScore = 0
    for ((p, keys) in WORDS) {
        val s = words.count { w -> keys.any { k -> w == k || (k.length > 4 && w.startsWith(k)) } } + (if (n.tags.any { t -> keys.any { it == t.lowercase() } }) 2 else 0)
        if (s > bestScore) { bestScore = s; best = p }
    }
    if (best != null) return best
    return when {
        n.blocks.any { it.kind == Kind.Checklist } -> Pic.Check
        n.blocks.any { it.kind == Kind.Poster } -> Pic.Bulb
        n.blocks.any { it.kind == Kind.Text } -> if (sqrt(seed.toDouble().let { it * it }).toInt() % 3 == 0) Pic.Pencil else null
        else -> null
    }
}
