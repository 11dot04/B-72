package com.b72.keep_canvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stamps for the canvas, drawn here (no pictures) so they stay sharp at any size: a few words of internet lingo, a few pieces of
 * brutalist label-and-barcode furniture, a few postmodern shapes. Each takes the pen colour you pick as its accent.
 */
enum class Stamp(val label: String, val aspect: Float, val group: String) {
    Yes("Yes", .6f, "Lingo"), NoCap("No cap", .42f, "Lingo"), Omg("OMG", .95f, "Lingo"), Ok("OK!", 1f, "Lingo"),
    GoodVibes("Good vibes", .62f, "Lingo"), Slaps("Slaps", .4f, "Lingo"), VibeCheck("Vibe check", 1f, "Lingo"), Goat("GOAT", .5f, "Lingo"),
    Flower("Flower", 1f, "Lingo"), HeartFace("Heart", 1f, "Lingo"), Thanks("Thank you", .7f, "Lingo"), New("New", .5f, "Lingo"),
    Barcode("Barcode", .6f, "Brutalist"), Label("Label", .8f, "Brutalist"), Globe("Globe", 1f, "Brutalist"), Spark("Asterisk", 1f, "Brutalist"),
    BigArrow("Arrow", .7f, "Brutalist"), Tape("Tape", .18f, "Brutalist"),
    Squig("Squiggle", .45f, "Postmodern"), Dots("Dots", 1f, "Postmodern"), Zig("Zigzag", .35f, "Postmodern"), Half("Half moon", .55f, "Postmodern"),
}

fun stampOf(k: String): Stamp? = if (k.startsWith("x:")) runCatching { Stamp.valueOf(k.substring(2)) }.getOrNull() else null

private val Ink = Color(0xFF15121B)
private val Paper = Color(0xFFFFFBF2)

private fun burst(w: Float, h: Float, n: Int, inner: Float): Path = Path().apply {
    val cx = w / 2; val cy = h / 2; val r = minOf(w, h) / 2
    for (i in 0 until n * 2) {
        val a = -PI.toFloat() / 2 + i * PI.toFloat() / n
        val rr = if (i % 2 == 0) r else r * inner
        val x = cx + rr * cos(a); val y = cy + rr * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun scallop(w: Float, h: Float, n: Int): Path = Path().apply {
    val cx = w / 2; val cy = h / 2; val r = minOf(w, h) / 2
    for (i in 0..120) {
        val a = i * 2 * PI.toFloat() / 120
        val rr = r * (.84f + .16f * cos(n * a))
        val x = cx + rr * cos(a); val y = cy + rr * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun heart(w: Float, h: Float): Path = Path().apply {
    for (i in 0..80) {
        val t = i * 2 * PI.toFloat() / 80
        val hx = 16 * sin(t) * sin(t) * sin(t)
        val hy = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
        val x = w * (.5f + hx / 34f); val y = h * (.47f - hy / 33f)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.face(cx: Float, cy: Float, r: Float, c: Color) {
    drawOval(c, Offset(cx - r * .42f, cy - r * .42f), Size(r * .2f, r * .36f))
    drawOval(c, Offset(cx + r * .22f, cy - r * .42f), Size(r * .2f, r * .36f))
    drawArc(c, 20f, 140f, false, Offset(cx - r * .38f, cy - r * .12f), Size(r * .76f, r * .55f), style = Stroke(r * .1f, cap = StrokeCap.Round))
}

/** One stamp, as wide as its parent lets it be; its height follows [Stamp.aspect]. [c] picks the accent from the pen colours. */
@Composable
fun StampView(s: Stamp, c: Int, seed: Int, modifier: Modifier = Modifier) {
    val a = MarkColors[c.mod(MarkColors.size)]
    val onA = if (a.luminance() > .45f) Ink else Paper
    BoxWithConstraints(modifier.aspectRatio(1f / s.aspect), contentAlignment = Alignment.Center) {
        val unit = with(LocalDensity.current) { maxWidth.toSp() }
        val bw = (maxWidth.value * .035f).coerceAtLeast(1.5f).dp
        /** A line of heavy type that is sized to fit [avail] of the width. */
        @Composable fun t(txt: String, avail: Float, fg: Color, wght: Int = 850, wdth: Float = 100f, mono: Boolean = false, cap: Float = .4f) {
            val k = (avail / (txt.length * (if (mono) .6f else .58f))).coerceAtMost(cap)
            BasicText(
                txt, maxLines = 1, softWrap = false,
                style = (if (mono) TextStyle(fontFamily = FontFamily.Monospace, fontSize = unit * k, lineHeight = unit * k * 1.1f) else flexStyle(unit * k, wght = wght, wdth = wdth, lh = 1f))
                    .copy(color = fg, textAlign = TextAlign.Center),
            )
        }
        when (s) {
            Stamp.Yes -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14)).background(Paper).border(bw, Ink, RoundedCornerShape(14)), contentAlignment = Alignment.Center) { t("YES", .78f, a, 900, 110f) }
            Stamp.NoCap -> Box(Modifier.fillMaxSize().clip(CircleShape).background(Ink).border(bw, a, CircleShape), contentAlignment = Alignment.Center) { t("NO CAP", .74f, a, 800, 90f) }
            Stamp.Omg -> {
                Canvas(Modifier.fillMaxSize()) { drawPath(burst(size.width, size.height, 14, .78f), a); drawPath(burst(size.width, size.height, 14, .78f), Ink, style = Stroke(bw.toPx(), join = StrokeJoin.Round)) }
                t("OMG", .62f, onA, 900)
            }
            Stamp.Ok -> {
                Canvas(Modifier.fillMaxSize()) { drawPath(burst(size.width, size.height, 18, .84f), a) }
                t("OK!", .5f, onA, 900)
            }
            Stamp.GoodVibes -> Box(Modifier.fillMaxSize().clip(CircleShape).background(Ink).border(bw, a, CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { t("good", .5f, a, 800, 100f); t("vibes", .5f, a, 800, 100f) }
            }
            Stamp.Slaps -> Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) { t("SLAPS", .8f, a, 900, 120f) }
            Stamp.VibeCheck -> Box(Modifier.fillMaxSize().clip(CircleShape).background(a).border(bw, Ink, CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { t("VIBE", .5f, onA, 900); t("CHECK", .56f, onA, 900) }
            }
            Stamp.Goat -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8)).background(a).border(bw, Ink, RoundedCornerShape(8)), contentAlignment = Alignment.Center) { t("GOAT", .7f, onA, 900, 110f) }
            Stamp.Flower -> {
                Canvas(Modifier.fillMaxSize()) { drawPath(scallop(size.width, size.height, 8), a); face(size.width / 2, size.height / 2, size.width * .36f, Ink) }
            }
            Stamp.HeartFace -> {
                Canvas(Modifier.fillMaxSize()) { drawPath(heart(size.width, size.height), a); face(size.width / 2, size.height * .46f, size.width * .3f, Ink) }
            }
            Stamp.Thanks -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(30)).background(Ink), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { t("thank", .6f, a, 800); t("you!", .6f, a, 800) }
            }
            Stamp.New -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16)).background(a), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawCircle(Ink, size.width * .07f, Offset(size.width * .14f, size.height / 2)) }
                Box(Modifier.padding(start = 0.dp)) { t("NEW", .5f, onA, 900) }
            }
            Stamp.Barcode -> Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val seeded = kotlin.random.Random(seed)
                    var x = size.width * .08f; val end = size.width * .92f
                    while (x < end) {
                        val bar = size.width * (.008f + seeded.nextFloat() * .03f)
                        drawRect(Ink, Offset(x, size.height * .1f), Size(bar.coerceAtMost(end - x), size.height * .64f))
                        x += bar + size.width * (.008f + seeded.nextFloat() * .026f)
                    }
                }
                Box(Modifier.fillMaxSize().padding(top = (maxHeight.value * .8f).dp), contentAlignment = Alignment.TopCenter) { t("0 24563 84926 54 2", .84f, Ink, mono = true, cap = .07f) }
            }
            Stamp.Label -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(5)).background(a).border(bw, Ink, RoundedCornerShape(5))) {
                Canvas(Modifier.fillMaxSize()) {
                    val seeded = kotlin.random.Random(seed)
                    drawCircle(Ink, size.width * .1f, Offset(size.width * .2f, size.height * .24f), style = Stroke(bw.toPx()))
                    face(size.width * .2f, size.height * .24f, size.width * .1f, Ink)
                    drawLine(Ink, Offset(0f, size.height * .46f), Offset(size.width, size.height * .46f), bw.toPx())
                    var x = size.width * .08f
                    while (x < size.width * .92f) { val b = size.width * (.01f + seeded.nextFloat() * .02f); drawRect(Ink, Offset(x, size.height * .54f), Size(b, size.height * .3f)); x += b + size.width * .014f }
                    for (i in 0..2) drawLine(Ink, Offset(size.width * .42f, size.height * (.12f + i * .1f)), Offset(size.width * (.9f - i * .12f), size.height * (.12f + i * .1f)), bw.toPx() * .8f)
                }
            }
            Stamp.Globe -> Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2 - bw.toPx() / 2
                drawCircle(a, r, center); drawCircle(Ink, r, center, style = Stroke(bw.toPx()))
                drawOval(Ink, Offset(center.x - r * .45f, center.y - r), Size(r * .9f, r * 2), style = Stroke(bw.toPx() * .7f))
                drawLine(Ink, Offset(center.x - r, center.y), Offset(center.x + r, center.y), bw.toPx() * .7f)
                drawLine(Ink, Offset(center.x, center.y - r), Offset(center.x, center.y + r), bw.toPx() * .7f)
                drawArc(Ink, 0f, 180f, false, Offset(center.x - r * .86f, center.y - r * .5f), Size(r * 1.72f, r), style = Stroke(bw.toPx() * .7f))
            }
            Stamp.Spark -> Canvas(Modifier.fillMaxSize()) { drawPath(burst(size.width, size.height, 8, .3f), a); drawPath(burst(size.width, size.height, 8, .3f), Ink, style = Stroke(bw.toPx(), join = StrokeJoin.Round)) }
            Stamp.BigArrow -> Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val p = Path().apply { moveTo(w * .05f, h * .36f); lineTo(w * .55f, h * .36f); lineTo(w * .55f, h * .06f); lineTo(w * .96f, h * .5f); lineTo(w * .55f, h * .94f); lineTo(w * .55f, h * .64f); lineTo(w * .05f, h * .64f); close() }
                drawPath(p, a); drawPath(p, Ink, style = Stroke(bw.toPx(), join = StrokeJoin.Round))
            }
            Stamp.Tape -> Box(Modifier.fillMaxSize().background(a), contentAlignment = Alignment.Center) { t("SPEC SHEET ✱ SPEC SHEET ✱ SPEC SHEET", .96f, onA, mono = true, cap = .035f) }
            Stamp.Squig -> Canvas(Modifier.fillMaxSize()) {
                val p = Path()
                for (i in 0..60) { val x = size.width * (.04f + .92f * i / 60f); val y = size.height * (.5f + .36f * sin(i / 60f * 2 * PI.toFloat() * 3.5f)); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                drawPath(p, a, style = Stroke(size.width * .08f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Stamp.Dots -> Canvas(Modifier.fillMaxSize()) {
                for (i in 0..3) for (j in 0..3) drawCircle(a, size.width * .07f, Offset(size.width * (.12f + i * .253f), size.height * (.12f + j * .253f)))
            }
            Stamp.Zig -> Canvas(Modifier.fillMaxSize()) {
                val p = Path()
                for (i in 0..8) { val x = size.width * (.04f + .92f * i / 8f); val y = if (i % 2 == 0) size.height * .78f else size.height * .22f; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                drawPath(p, a, style = Stroke(size.width * .075f, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
            }
            Stamp.Half -> Canvas(Modifier.fillMaxSize()) {
                val r = size.width / 2
                drawArc(a, 180f, 180f, true, Offset(0f, size.height - r), Size(size.width, size.width))
                for (i in 1..3) drawArc(Ink, 180f, 180f, false, Offset(r * i * .22f, size.height - r + r * i * .22f), Size(size.width - r * i * .44f, size.width - r * i * .44f), style = Stroke(bw.toPx() * .8f))
            }
        }
    }
}
