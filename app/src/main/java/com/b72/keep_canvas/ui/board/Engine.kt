package com.b72.keep_canvas.ui.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.seedOf
import com.b72.keep_canvas.ui.subjectIn
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** The canvas is this many times as wide as the screen. All geometry below is in fractions of the canvas WIDTH. */
const val WIDE = 1.2f
private const val GAP = .010f
private const val BAND_L = .083f
private const val BAND_R = .917f

/** One block of one note, placed on a tag's canvas. */
class Card(val noteId: String, val b: Block, val order: Int, val w: Float, val h: Float, x0: Float, y0: Float, val placed: Boolean) {
    val id: String get() = b.id
    // plain fields: the packer reads and writes these millions of times; the UI watches [rev] instead
    var x: Float = x0
    var y: Float = y0
    /** Dragged cards are lifted above the others of their kind. */
    var lift: Int = 0
    var rev by mutableIntStateOf(0)
        private set
    fun moved() { rev++ }
    val kind: Kind get() = b.kind
    val isPhoto get() = kind == Kind.Photo
    val bare get() = kind == Kind.Bare
    val rect: Rect get() = Rect(x, y, x + w, y + h)
    /** Tilt in radians, stable per card. */
    val angle: Float get() = if (isPhoto) (((seedOf(id) % 9) - 4) * .01f) else 0f

    /** The part of a photo that must stay visible, in the card's own coordinates. Text cards are visible as a whole (null). */
    val subject: Rect? by lazy {
        if (!isPhoto) null
        else subjectIn(b, w, h) ?: b.focal.takeIf { it.size >= 4 }?.let { Rect((it[0] * w).toFloat(), (it[1] * h).toFloat(), (it[2] * w).toFloat(), (it[3] * h).toFloat()) } ?: Rect(w * .3f, h * .3f, w * .7f, h * .7f)
    }
    val hasCut: Boolean get() = subjectIn(b, w, h) != null
}

private fun zKey(c: Card) = (if (c.isPhoto) 0 else 1_000_000) + c.lift * 1000 + c.order
private fun Rect.area() = max(0f, width) * max(0f, height)

/** Does the upper card's rectangle touch what the lower card needs to keep visible? Photos only protect their subject (an ellipse). */
private fun hitsProtected(up: Rect, low: Card): Boolean {
    val r = Rect(up.left - GAP, up.top - GAP, up.right + GAP, up.bottom + GAP)
    val s = low.subject ?: return r.overlaps(low.rect)
    val cx = low.x + s.center.x
    val cy = low.y + s.center.y
    val a = max(.001f, s.width / 2 + GAP)
    val b = max(.001f, s.height / 2 + GAP)
    val px = cx.coerceIn(r.left, r.right)
    val py = cy.coerceIn(r.top, r.bottom)
    val dx = (px - cx) / a
    val dy = (py - cy) / b
    return dx * dx + dy * dy < 1f
}

/** The rules of the canvas. Upper cards may ride over a lower photo's empty space, never over its subject or over text. */
fun valid(m: Card, others: List<Card>): Boolean {
    val r = m.rect
    if (r.left < 0f || r.top < 0f || r.right > 1f) return false
    if (!m.isPhoto && (r.left < BAND_L || r.right > BAND_R)) return false
    val mz = zKey(m)
    for (o in others) {
        if (o === m) continue
        val orect = o.rect
        // bare text lives on the plain canvas: nothing may touch it
        if (m.bare || o.bare) {
            if (Rect(r.left - GAP, r.top - GAP, r.right + GAP, r.bottom + GAP).overlaps(orect)) return false
            continue
        }
        val oUp = zKey(o) > mz
        if (hitsProtected(if (oUp) orect else r, if (oUp) m else o)) return false
        if (m.isPhoto && o.isPhoto) {
            val ov = Rect(max(r.left, orect.left), max(r.top, orect.top), min(r.right, orect.right), min(r.bottom, orect.bottom))
            if (ov.area() > .4f * min(r.area(), orect.area())) return false
        }
    }
    return true
}

/** Nearest valid spot around where the card is now (used after a drag and for stored positions that no longer fit). */
fun settle(m: Card, others: List<Card>) {
    if (valid(m, others)) return
    val ox = m.x
    val oy = m.y
    val step = .015f
    for (r in 1..70) {
        var best: Pair<Float, Float>? = null
        var bd = Float.MAX_VALUE
        for (dx in -r..r) for (dy in -r..r) {
            if (max(abs(dx), abs(dy)) != r) continue
            val d = hypot(dx.toFloat(), dy.toFloat())
            if (d >= bd) continue
            m.x = ox + dx * step
            m.y = oy + dy * step
            if (valid(m, others)) { bd = d; best = m.x to m.y }
        }
        if (best != null) { m.x = best.first; m.y = best.second; return }
    }
    m.x = ox
    m.y = oy
}

/**
 * Finds a home for a new card: as high and central as possible, close to its own note's photo when it has one,
 * and with a small reward for tucking over a neighbour's empty space so the board feels layered, not gridded.
 */
fun place(m: Card, placed: List<Card>, anchor: Card?) {
    val bottom = placed.maxOfOrNull { it.rect.bottom } ?: 0f
    val xMin = if (m.isPhoto) 0f else BAND_L
    val xMax = (if (m.isPhoto) 1f else BAND_R) - m.w
    var bx = xMin.coerceAtMost(max(xMin, xMax))
    var by = bottom + .05f
    var bc = Float.MAX_VALUE
    val wy = if (anchor != null) .25f else 1f
    var y = 0f
    val yMax = bottom + .2f
    while (y <= yMax) {
        // no later row can beat the best spot found so far (the tuck reward is at most .06)
        if (bc < Float.MAX_VALUE && wy * y - .07f > bc) break
        var x = xMin
        while (x <= xMax + 1e-4f) {
            m.x = x
            m.y = y
            if (valid(m, placed)) {
                val cx = x + m.w / 2
                val cy = y + m.h / 2
                var c = wy * y + .25f * abs(cx - .5f) + (((seedOf(m.id) + (x * 997).toInt() + (y * 463).toInt()) % 100) / 100f) * .03f
                if (anchor != null) c += .9f * hypot(cx - anchor.rect.center.x, cy - anchor.rect.center.y)
                var tuck = 0f
                val r = m.rect
                for (o in placed) {
                    val orect = o.rect
                    val ov = Rect(max(r.left, orect.left), max(r.top, orect.top), min(r.right, orect.right), min(r.bottom, orect.bottom)).area()
                    if (ov > 0f) tuck += ov / r.area()
                }
                c -= .12f * min(tuck, .5f)
                if (c < bc) { bc = c; bx = x; by = y }
            }
            x += .02f
        }
        y += .02f
    }
    m.x = bx
    m.y = by
}

/**
 * Lays the cards out. Cards that already have a stored place keep it (moved to the nearest valid spot if it no longer fits);
 * the others are placed note by note, each note's photo first so its text can gather around it.
 * Returns the cards whose position changed or is new, so the caller can store them.
 */
fun arrange(cards: List<Card>, retidy: Boolean): List<Card> {
    val done = mutableListOf<Card>()
    val changed = mutableListOf<Card>()
    val keep = cards.filter { it.placed && !retidy }.sortedBy { zKey(it) }
    for (c in keep) {
        val x = c.x
        val y = c.y
        settle(c, done)
        if (c.x != x || c.y != y) changed.add(c)
        done.add(c)
    }
    val rest = cards.filter { !it.placed || retidy }
        .sortedWith(compareBy<Card>({ cards.indexOfFirst { o -> o.noteId == it.noteId } }, { if (it.isPhoto) 0 else 1 }, { it.order }))
    for (c in rest) {
        val anchor = if (c.isPhoto && done.none { it.noteId == c.noteId && it.isPhoto }) null
        else done.firstOrNull { it.noteId == c.noteId && it.isPhoto }
        place(c, done, anchor)
        done.add(c)
        changed.add(c)
    }
    return changed
}

/** Bottom edge of everything, in canvas widths. */
fun bottomOf(cards: List<Card>) = cards.maxOfOrNull { it.rect.bottom } ?: 0f

fun placeKey(tag: String, b: Block) = "$tag|${b.id}"

/** One card per block of every note with this tag. [size] returns (w, h) in canvas widths for a block. */
fun cardsFor(notes: List<Note>, tag: String, size: (Block, Boolean) -> Pair<Float, Float>): List<Card> {
    val out = mutableListOf<Card>()
    for (n in notes) {
        if (tag !in n.tags) continue
        var cover = true
        n.blocks.forEachIndexed { i, b ->
            val p = n.places[placeKey(tag, b)]
            val big = b.kind == Kind.Photo && cover
            if (b.kind == Kind.Photo) cover = false
            val (w, h) = size(b, big)
            out.add(Card(n.id, b, i, w, h, p?.getOrNull(0)?.toFloat() ?: .3f, p?.getOrNull(1)?.toFloat() ?: 0f, p != null))
        }
    }
    return out
}
