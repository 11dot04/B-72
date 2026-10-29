package com.b72.keep_canvas.ui.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.RECORD_KINDS
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.seedOf
import com.b72.keep_canvas.ui.subjectIn
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** The canvas is this many times as wide as the screen. All geometry below is in fractions of the canvas WIDTH. */
const val WIDE = 2.0f
/** Card sizes are fractions of the canvas width; this keeps cards the size they were on a 1.2-wide canvas. */
const val K_WIDE = 1.2f / WIDE
private const val GAP = .010f * K_WIDE
private const val BAND_L = .05f
private const val BAND_R = .95f

/** One block of one note, placed on a tag's canvas. */
class Card(val noteId: String, val b: Block, val order: Int, val w: Float, val h: Float, x0: Float, y0: Float, val placed: Boolean, val label: String = "") {
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
    private val isPictureCard get() = kind in RECORD_KINDS && b.path != null
    /** Photos and picture cards sit beneath the text cards. */
    val low get() = isPhoto || isPictureCard
    val bare get() = kind == Kind.Bare
    val rect: Rect get() = Rect(x, y, x + w, y + h)
    /** Tilt in radians, stable per card. */
    val angle: Float get() = when {
        bare -> 0f
        isPhoto -> (((seedOf(id) % 11) - 5) * .019f)
        isPictureCard -> (((seedOf(id) % 9) - 4) * .017f)
        else -> (((seedOf(id) % 7) - 3) * .016f)
    }

    /** The part of a card that must stay visible, in its own coordinates: a photo's subject (an oval), a picture card's title area,
     *  the inside of a text card. Everything else is free for other cards to ride over. */
    val prot: Rect by lazy {
        when {
            isPhoto -> {
                val r = subjectIn(b, w, h) ?: b.focal.takeIf { it.size >= 4 }?.let { Rect((it[0] * w).toFloat(), (it[1] * h).toFloat(), (it[2] * w).toFloat(), (it[3] * h).toFloat()) } ?: Rect(w * .3f, h * .3f, w * .7f, h * .7f)
                // a little tighter than the box: the corners of a subject's bounding box are empty
                Rect(r.center.x - r.width * .43f, r.center.y - r.height * .43f, r.center.x + r.width * .43f, r.center.y + r.height * .43f)
            }
            isPictureCard -> Rect(w * .06f, h * .52f, w * .94f, h * .98f)
            bare -> Rect(0f, 0f, w, h)
            else -> Rect(w * .09f, h * .09f, w * .91f, h * .91f)
        }
    }
    val protIsOval: Boolean get() = isPhoto
    val hasCut: Boolean get() = subjectIn(b, w, h) != null
}

private fun zKey(c: Card) = (if (c.low) 0 else 1_000_000) + c.lift * 1000 + c.order
private fun Rect.area() = max(0f, width) * max(0f, height)

/** Does the upper card's rectangle touch what the lower card needs to keep visible? Photos only protect their subject (an ellipse). */
private fun hitsProtected(up: Rect, low: Card): Boolean {
    val r = Rect(up.left - GAP, up.top - GAP, up.right + GAP, up.bottom + GAP)
    val s = low.prot
    if (!low.protIsOval) return r.overlaps(Rect(low.x + s.left, low.y + s.top, low.x + s.right, low.y + s.bottom))
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
    return rules(m, others)
}

/** The overlap rules alone, without the canvas edges. */
private fun rules(m: Card, others: List<Card>): Boolean {
    val r = m.rect
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
        if (m.low && o.low) {
            val ov = Rect(max(r.left, orect.left), max(r.top, orect.top), min(r.right, orect.right), min(r.bottom, orect.bottom))
            if (ov.area() > .5f * min(r.area(), orect.area())) return false
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
        // no later row can beat the best spot found so far (the overlap reward is at most .3)
        if (bc < Float.MAX_VALUE && wy * y - .31f > bc) break
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
                // a card that overlaps its neighbours by about a sixth of itself reads as layered; none reads as gridded
                c -= .3f * (1f - min(abs(tuck - .16f), .16f) / .16f)
                if (c < bc) { bc = c; bx = x; by = y }
            }
            x += .02f
        }
        y += .02f
    }
    m.x = bx
    m.y = by
}

private fun tuckOf(m: Card, placed: List<Card>): Float {
    val r = m.rect
    var t = 0f
    for (o in placed) {
        val q = o.rect
        if (r.overlaps(q)) t += Rect(max(r.left, q.left), max(r.top, q.top), min(r.right, q.right), min(r.bottom, q.bottom)).area() / r.area()
    }
    return t
}

/** One note's cards arranged among themselves; positions are stored on the cards relative to the cluster's top-left corner. */
private class Cluster(val cards: List<Card>, val w: Float, val h: Float)

private val STAGGER = floatArrayOf(-.3f, -.15f, 0f, .15f, .3f, .5f)
private val EDGE_OVERLAPS = floatArrayOf(0f, .1f, .2f)
private val CORNER = floatArrayOf(.12f, .22f)

/**
 * A note's cards as a small collage: the photo (or the biggest card) is the anchor, and every other card is tried against the
 * sides and the corners of the cards already there, staggered and overlapping their free space by a little. A wide, compact,
 * un-gridded result wins: a multi-card note aims for about .7 canvas widths across, and edges that line up exactly cost a little.
 */
private fun buildCluster(cs: List<Card>): Cluster {
    val anchor = cs.firstOrNull { it.isPhoto } ?: cs.maxByOrNull { it.w * it.h }!!
    val rest = cs.filter { it !== anchor }.sortedWith(compareBy({ if (it.low) 0 else 1 }, { -(it.w * it.h) }))
    anchor.x = 0f; anchor.y = 0f
    val placed = mutableListOf(anchor)
    val multi = cs.size > 1
    fun bounds(extra: Card): Rect {
        var l = extra.x; var t = extra.y; var r = extra.x + extra.w; var b = extra.y + extra.h
        for (c in placed) { l = min(l, c.x); t = min(t, c.y); r = max(r, c.x + c.w); b = max(b, c.y + c.h) }
        return Rect(l, t, r, b)
    }
    for (m in rest) {
        var bx = Float.NaN
        var by = 0f
        var bc = Float.MAX_VALUE
        fun tryAt(x: Float, y: Float) {
            m.x = x; m.y = y
            if (!rules(m, placed)) return
            val bb = bounds(m)
            var c = bb.width * bb.height + (if (multi) 5f * abs(bb.width - .7f) else 0f) + .25f * max(0f, bb.height - 1.1f)
            c -= .08f * tuckOf(m, placed)
            val mr = m.rect
            for (q in placed) if (abs(q.x - mr.left) < .006f || abs(q.x + q.w - mr.right) < .006f) c += .012f
            c += ((seedOf(m.id) + (x * 997).toInt() + (y * 463).toInt()).mod(100)) / 100f * .004f
            if (!m.isPhoto && anchor.isPhoto) c += .25f * hypot(mr.center.x - anchor.rect.center.x, mr.center.y - anchor.rect.center.y)
            if (c < bc) { bc = c; bx = x; by = y }
        }
        for (o in placed) {
            val ro = o.rect
            for (ov in EDGE_OVERLAPS) for (st in STAGGER) {
                val yy = ro.top + o.h * (.5f + st * .9f) - m.h / 2
                val xx = ro.left + o.w * (.5f + st * .9f) - m.w / 2
                tryAt(ro.right - ov * m.w, yy)
                tryAt(ro.left - m.w + ov * m.w, yy)
                tryAt(xx, ro.bottom - ov * m.h)
                tryAt(xx, ro.top - m.h + ov * m.h)
            }
            for (ox in CORNER) for (oy in CORNER) {
                tryAt(ro.right - ox * m.w, ro.bottom - oy * m.h)
                tryAt(ro.left - m.w + ox * m.w, ro.bottom - oy * m.h)
                tryAt(ro.right - ox * m.w, ro.top - m.h + oy * m.h)
                tryAt(ro.left - m.w + ox * m.w, ro.top - m.h + oy * m.h)
            }
        }
        if (bx.isNaN()) { val bb = bounds(m); bx = bb.left; by = bb.bottom }
        m.x = bx; m.y = by
        placed.add(m)
    }
    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
    for (c in placed) { l = min(l, c.x); t = min(t, c.y); r = max(r, c.x + c.w); b = max(b, c.y + c.h) }
    for (c in placed) { c.x -= l; c.y -= t }
    return Cluster(placed, r - l, b - t)
}

/** Slides a whole cluster down the board to the lowest spot that fits, filling the gap beside earlier ones (columns form on their own). */
private fun placeCluster(cl: Cluster, placed: List<Card>, index: Int) {
    val bottom = placed.maxOfOrNull { it.rect.bottom } ?: 0f
    val hasPhoto = cl.cards.any { it.isPhoto }
    val xMin = if (hasPhoto) 0f else BAND_L
    val xMax = (if (hasPhoto) 1f else BAND_R) - cl.w
    val bx0 = cl.cards.map { it.x }
    val by0 = cl.cards.map { it.y }
    val area = cl.cards.sumOf { (it.w * it.h).toDouble() }.toFloat()
    var bestX = xMin
    var bestY = bottom + .03f
    var bc = Float.MAX_VALUE
    var y = 0f
    while (y <= bottom + .1f) {
        if (bc < Float.MAX_VALUE && (y + cl.h) - .3f > bc) break
        var x = xMin
        while (x <= xMax + 1e-4f) {
            cl.cards.forEachIndexed { i, c -> c.x = bx0[i] + x; c.y = by0[i] + y }
            if (cl.cards.all { valid(it, placed) }) {
                var t = 0f
                for (c in cl.cards) for (o in placed) {
                    val r = c.rect; val q = o.rect
                    if (r.overlaps(q)) t += Rect(max(r.left, q.left), max(r.top, q.top), min(r.right, q.right), min(r.bottom, q.bottom)).area()
                }
                t /= area
                val pref = if (index % 2 == 0) xMin else xMax
                var cost = (y + cl.h) + .35f * abs(x - pref) + ((seedOf(cl.cards[0].id) + (x * 997).toInt() + (y * 463).toInt()).mod(100) / 100f) * .02f
                cost -= .25f * (1f - min(abs(t - .07f), .07f) / .07f)
                if (cost < bc) { bc = cost; bestX = x; bestY = y }
            }
            x += .02f
        }
        y += .02f
    }
    cl.cards.forEachIndexed { i, c -> c.x = bx0[i] + bestX; c.y = by0[i] + bestY }
}

/**
 * Lays the cards out. Cards that already have a stored place keep it (moved to the nearest valid spot if it no longer fits).
 * New cards are laid out note by note: each note becomes a small collage that is then packed into the board like a tile.
 * A card added to a note that is already on the board is tucked in beside it instead.
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
    val noteOrder = cards.map { it.noteId }.distinct()
    var rows = done.map { it.noteId }.distinct().size
    for (nid in noteOrder) {
        val mine = rest.filter { it.noteId == nid }
        if (mine.isEmpty()) continue
        val placedMates = done.filter { it.noteId == nid }
        if (placedMates.isNotEmpty()) {
            for (c in mine.sortedBy { it.order }) {
                place(c, done, placedMates.firstOrNull { it.isPhoto } ?: placedMates.first())
                done.add(c)
            }
        } else {
            val cl = buildCluster(mine)
            placeCluster(cl, done, rows++)
            done.addAll(cl.cards)
        }
        changed.addAll(mine)
    }
    return changed
}

/** An arrow pointing at something: a cubic curve, in canvas widths. */
class Arrow(val p0: Offset, val c1: Offset, val c2: Offset, val p3: Offset)

private fun bez(a: Arrow, t: Float): Offset {
    val u = 1f - t
    return a.p0 * (u * u * u) + a.c1 * (3 * u * u * t) + a.c2 * (3 * u * t * t) + a.p3 * (t * t * t)
}

/**
 * Arrows are annotations, not wiring: a note gets at most one, from its caption (the nearest text card) to the photo's subject,
 * only when there is a real gap to cross and the swoop does not run over another card. Few per canvas, spread apart.
 */
fun arrowsFor(cards: List<Card>): List<Arrow> {
    val notes = cards.map { it.noteId }.distinct()
    val cap = ((notes.size + 2) / 3).coerceAtLeast(1)
    val out = ArrayList<Arrow>()
    val mids = ArrayList<Offset>()
    for (nid in notes) {
        if (out.size >= cap) break
        val mine = cards.filter { it.noteId == nid }
        val photo = mine.firstOrNull { it.isPhoto } ?: continue
        val pr = photo.rect
        val sub = photo.prot
        val sc = Offset(photo.x + sub.center.x, photo.y + sub.center.y)
        val texts = mine.filter { !it.low && !it.bare }
        var chosen: Arrow? = null
        for (t in texts.sortedBy { hypot(it.rect.center.x - sc.x, it.rect.center.y - sc.y) }) {
            val tr = t.rect
            val gx = max(0f, max(tr.left - pr.right, pr.left - tr.right))
            val gy = max(0f, max(tr.top - pr.bottom, pr.top - tr.bottom))
            val gap = hypot(gx, gy)
            if (gap < .05f || gap > .32f || tr.overlaps(pr)) continue
            // leave the caption from the edge that faces the subject, arrive just outside the subject's outline
            val from = Offset(sc.x.coerceIn(tr.left, tr.right), sc.y.coerceIn(tr.top, tr.bottom))
            val start = Offset((tr.center.x * .5f + from.x * .5f).coerceIn(tr.left, tr.right), (tr.center.y * .5f + from.y * .5f).coerceIn(tr.top, tr.bottom))
            val d = sc - start
            val len = d.getDistance()
            if (len < .08f) continue
            val dir = d / len
            val rx = sub.width / 2f + .018f
            val ry = sub.height / 2f + .018f
            // distance from the subject's centre to its outline along -dir
            val k = 1f / kotlin.math.sqrt((dir.x / rx) * (dir.x / rx) + (dir.y / ry) * (dir.y / ry))
            val end = sc - dir * k
            val span = (end - start).getDistance()
            if (span < .06f) continue
            val nrm = Offset(-dir.y, dir.x)
            for (sign in floatArrayOf(1f, -1f)) {
                val bulge = nrm * (sign * span * .32f)
                val a = Arrow(start, start + (end - start) * .3f + bulge, start + (end - start) * .72f + bulge * .9f, end)
                var clear = true
                for (i in 3..21) {
                    val q = bez(a, i / 24f)
                    for (o in cards) {
                        if (o === t || o === photo) continue
                        val r = o.rect
                        if (q.x > r.left - .01f && q.x < r.right + .01f && q.y > r.top - .01f && q.y < r.bottom + .01f) { clear = false; break }
                    }
                    if (!clear) break
                }
                if (clear) { chosen = a; break }
            }
            if (chosen != null) break
        }
        val a = chosen ?: continue
        val mid = bez(a, .5f)
        if (mids.any { (it - mid).getDistance() < .3f }) continue
        out.add(a); mids.add(mid)
    }
    return out
}

/** Bottom edge of everything, in canvas widths. */
fun bottomOf(cards: List<Card>) = cards.maxOfOrNull { it.rect.bottom } ?: 0f

fun placeKey(tag: String, b: Block) = "$tag|${b.id}"

/** Bumped whenever the layout rules change: positions stored by an older version are laid out again once. */
const val LAYOUT_VERSION = 4.0
fun versionKey(tag: String) = "$tag|#v"

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
            val what = when (b.kind) { Kind.Photo -> "Photo"; Kind.Checklist -> "Checklist"; Kind.Quote -> "Quote"; Kind.Film -> "Film"; Kind.Track -> "Track"; Kind.Book -> "Book"; Kind.Recipe -> "Recipe"; Kind.Link -> "Link"; else -> "Note" }
            val label = "$what: ${b.title.ifBlank { b.body.take(50) }.ifBlank { n.title }}. Opens the note."
            val current = n.places[versionKey(tag)]?.getOrNull(0) == LAYOUT_VERSION
            out.add(Card(n.id, b, i, w, h, p?.getOrNull(0)?.toFloat() ?: .3f, p?.getOrNull(1)?.toFloat() ?: 0f, p != null && current, label))
        }
    }
    return out
}
