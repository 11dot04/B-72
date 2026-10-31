@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.b72.keep_canvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.graphics.shapes.RoundedPolygon
import com.b72.keep_canvas.data.Block
import com.b72.keep_canvas.data.Images
import com.b72.keep_canvas.data.cutFrac
import com.b72.keep_canvas.data.cutPath
import com.b72.keep_canvas.data.nums
import com.b72.keep_canvas.data.dbl
import com.b72.keep_canvas.data.str
import kotlin.math.max
import kotlin.math.min

/**
 * A photo breaks out of a shape when its cut-out passed the quality gate (one whole, crisp, well-framed subject), unless the
 * person has overridden it: `pop` = 1 forces it on, 0 forces it off.
 */
fun canPopOut(b: Block, photos: Int = 1): Boolean {
    if (photos != 1 || b.cutPath() == null) return false
    return when (b.dbl("pop")?.toInt()) {
        1 -> true
        0 -> false
        else -> (b.dbl("cutOk") ?: 0.0) >= 1.0 && b.cutFrac() in .05..0.6
    }
}

/** Why the gate said no, in words; empty when it passed or there is no cut-out. */
fun popWhy(b: Block): String = if (b.cutPath() == null) "" else b.str("cutWhy").orEmpty()

/** Where the subject lands in an area drawn with "cover" fit, centred. */
fun subjectIn(b: Block, w: Float, h: Float): Rect? {
    val box = b.nums("cutBox")?.takeIf { it.size >= 4 } ?: return null
    val ar = b.dbl("cutAspect")?.toFloat() ?: return null
    val scale = max(w / ar, h)
    val dw = ar * scale
    val ox = (w - dw) / 2
    val oy = (h - scale) / 2
    return Rect(ox + box[0].toFloat() * dw, oy + box[1].toFloat() * scale, ox + box[2].toFloat() * dw, oy + box[3].toFloat() * scale)
}

/** The shapes tried here are the calm ones from MaterialShapes; [stretch] means the shape may be squashed to the frame. */
private class PopShape(val poly: RoundedPolygon, val stretch: Boolean)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val PopShapes by lazy {
    listOf(
        PopShape(MaterialShapes.Arch, true),
        PopShape(MaterialShapes.Cookie9Sided, false),
        PopShape(MaterialShapes.Clover4Leaf, false),
        PopShape(MaterialShapes.Cookie6Sided, false),
        PopShape(MaterialShapes.Oval, true),
        PopShape(MaterialShapes.Gem, false),
        PopShape(MaterialShapes.Bun, false),
        PopShape(MaterialShapes.Cookie12Sided, false),
        PopShape(MaterialShapes.Pentagon, false),
    )
}

/** Fits a RoundedPolygon into [dst]; written out by hand so it does not depend on how toShape scales. */
private fun RoundedPolygon.pathIn(dst: Rect, stretch: Boolean): Path {
    val b = calculateBounds()
    val bw = max(b[2] - b[0], 1e-4f)
    val bh = max(b[3] - b[1], 1e-4f)
    var sx = dst.width / bw
    var sy = dst.height / bh
    if (!stretch) { val s = min(sx, sy); sx = s; sy = s }
    val ox = dst.left + (dst.width - bw * sx) / 2 - b[0] * sx
    val oy = dst.top + (dst.height - bh * sy) / 2 - b[1] * sy
    val p = Path()
    var first = true
    for (c in cubics) {
        if (first) { p.moveTo(c.anchor0X * sx + ox, c.anchor0Y * sy + oy); first = false }
        p.cubicTo(
            c.control0X * sx + ox, c.control0Y * sy + oy,
            c.control1X * sx + ox, c.control1Y * sy + oy,
            c.anchor1X * sx + ox, c.anchor1Y * sy + oy,
        )
    }
    p.close()
    return p
}

private fun DrawScope.drawCover(img: ImageBitmap, filter: ColorFilter?, blend: BlendMode = BlendMode.SrcOver) {
    val ia = img.width.toFloat() / img.height
    val ta = size.width / size.height
    val sw: Float
    val sh: Float
    if (ia > ta) { sh = img.height.toFloat(); sw = sh * ta } else { sw = img.width.toFloat(); sh = sw / ta }
    drawImage(
        img,
        srcOffset = IntOffset(((img.width - sw) / 2).toInt(), ((img.height - sh) / 2).toInt()),
        srcSize = IntSize(sw.toInt().coerceAtLeast(1), sh.toInt().coerceAtLeast(1)),
        dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
        colorFilter = filter,
        blendMode = blend,
    )
}

/**
 * The frame the shape is drawn in, for a hero area [w] x [h]. It is sized from the subject, not from the page: about a fifth of
 * the subject stays above it and the frame is wider and deeper than the subject, so the subject sits inside a big shape
 * and only its head breaks out. Always a square, clamped to the area.
 */
fun popFrame(b: Block, w: Float, h: Float): Rect {
    val s = subjectIn(b, w, h) ?: Rect(w * .2f, h * .1f, w * .8f, h * .8f)
    val poke = s.height * (if (s.height / max(1f, s.width) > 1.5f) .16f else .22f)
    val top0 = s.top + poke
    val deep = s.bottom + s.height * .06f - top0
    val d = min(w, max(max(s.width * 1.3f, w * .84f), deep * 1.04f))
    val left = (s.center.x - d / 2).coerceIn(0f, w - d)
    val top = min(top0, h - d).coerceAtLeast(0f)
    return Rect(left, top, left + d, top + d)
}

/** Photo in a shape, with the cut-out of its subject drawn on top so the subject breaks out of the shape's upper edge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PopOut(block: Block, fill: Color, tint: Color?, shapeSeed: Int, modifier: Modifier = Modifier) {
    val photo by produceState<ImageBitmap?>(block.path?.let { Images.peek(it, 1100) }, block.path) { value = block.path?.let { Images.load(it, 1100) } }
    val cut by produceState<ImageBitmap?>(block.cutPath()?.let { Images.peek(it, 1100) }, block.id) { value = block.cutPath()?.let { Images.load(it, 1100) } }
    val shape = PopShapes[shapeSeed % PopShapes.size]
    val filter = tint?.let { ColorFilter.tint(it, BlendMode.Color) }
    // A subject that runs into the photo's bottom or side edges (a shoulder-up selfie) would hang out of the shape as a flat slab,
    // so only its top is allowed to break out; below that line the photo stays inside the shape.
    val topOnly = block.nums("cutBox")?.let { it.size >= 4 && (it[0] < .02 || it[2] > .98 || it[3] > .97) } == true
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val frame = popFrame(block, w, h)
        val region = shape.poly.pathIn(frame, true)
        drawPath(region, fill)
        photo?.let { clipPath(region) { drawCover(it, filter) } }
        // A Color-blend tint also paints transparent pixels, which showed up as a grey slab above the shape.
        // So the tinted subject is drawn in its own layer and then cut back to the subject's own alpha.
        cut?.let { c ->
            drawIntoCanvas { cv ->
                cv.saveLayer(Rect(0f, 0f, w, h), Paint())
                if (topOnly) clipRect(0f, 0f, w, frame.top + frame.height * .4f) {
                    drawCover(c, filter)
                    if (filter != null) drawCover(c, null, BlendMode.DstIn)
                } else {
                    drawCover(c, filter)
                    if (filter != null) drawCover(c, null, BlendMode.DstIn)
                }
                cv.restore()
            }
        }
    }
}
