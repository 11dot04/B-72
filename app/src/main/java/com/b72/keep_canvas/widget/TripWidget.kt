package com.b72.keep_canvas.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.b72.keep_canvas.MainActivity
import com.b72.keep_canvas.R
import com.b72.keep_canvas.data.Kind
import com.b72.keep_canvas.data.Note
import com.b72.keep_canvas.data.OPEN_KEY
import com.b72.keep_canvas.data.Store
import com.b72.keep_canvas.data.Trips
import com.b72.keep_canvas.data.links
import com.b72.keep_canvas.data.phase
import com.b72.keep_canvas.data.route
import com.b72.keep_canvas.ui.tripStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What a widget shows right now: one note, why it is on show, and optionally one button. */
class Pick(val noteId: String?, val kicker: String, val head: String, val line: String, val at: String = "", val action: Pair<String, String>? = null)

/**
 * The widget picks the most useful thing for this moment: a trip that is close, a reminder that is due, a starred note,
 * a list that is not finished, an older note to revisit, or else the latest note. It rotates through starred notes
 * and old ones through the day so it does not sit on the same thing.
 */
object WidgetPick {
    private const val HOUR = 3_600_000L

    fun pick(notes: List<Note>, now: Long): Pick {
        val zone = ZoneId.systemDefault()
        fun clock(ms: Long, p: String) = Instant.ofEpochMilli(ms).atZone(zone).format(DateTimeFormatter.ofPattern(p))
        fun name(n: Note) = n.title.ifBlank { n.snippet(60) }.ifBlank { "Untitled note" }
        val slot = now / (6 * HOUR)

        Trips.next(notes, now)?.let { (n, t) ->
            if (t.dep - now < 48 * HOUR) {
                val link = t.links(t.phase(now)).firstOrNull()
                val fmt = if (t.timeKnown) "EEE d MMM · HH:mm" else "EEE d MMM"
                return Pick(n.id, listOf(t.mode.uppercase(), t.number, t.name.uppercase()).filter { it.isNotBlank() }.joinToString(" · "),
                    t.route.ifBlank { name(n) }, tripStatus(t, now), clock(t.dep, fmt), link?.let { it.label to it.url })
            }
        }
        notes.filter { it.remind in (now - HOUR)..(now + 24 * HOUR) }.minByOrNull { it.remind }?.let { n ->
            return Pick(n.id, if (n.remind < now) "REMINDER · EARLIER" else "REMINDER", name(n), n.snippet(90).takeIf { it != name(n) } ?: "", clock(n.remind, "EEE d MMM · HH:mm"))
        }
        notes.filter { it.starred }.takeIf { it.isNotEmpty() }?.let { l ->
            val n = l[(slot % l.size).toInt()]
            return Pick(n.id, "STARRED", name(n), n.snippet(90).takeIf { it != name(n) } ?: "")
        }
        notes.filter { n -> n.blocks.any { it.kind == Kind.Checklist && it.checkItems.any { s -> !s.startsWith("x ") } } }
            .maxByOrNull { maxOf(it.edited, it.id.toLongOrNull()?.div(1000) ?: 0) }?.let { n ->
                val items = n.blocks.first { it.kind == Kind.Checklist }.checkItems
                val done = items.count { it.startsWith("x ") }
                return Pick(n.id, "TO DO · $done OF ${items.size} DONE", name(n), "Next: " + items.first { !it.startsWith("x ") })
            }
        notes.mapNotNull { n -> n.id.toLongOrNull()?.div(1000)?.let { born -> ((now - born) / 86_400_000L).toInt().takeIf { it >= 7 }?.let { n to it } } }
            .filter { (n, _) -> name(n) != "Untitled note" }.takeIf { it.isNotEmpty() }?.let { l ->
                val (n, days) = l[((slot * 31 + l.size) % l.size).toInt()]
                val ago = when { days >= 365 -> "${days / 365} YEAR${if (days >= 730) "S" else ""} AGO"; days >= 60 -> "${days / 30} MONTHS AGO"; else -> "$days DAYS AGO" }
                return Pick(n.id, "FROM $ago", name(n), n.snippet(90).takeIf { it != name(n) } ?: "")
            }
        notes.lastOrNull()?.let { n -> return Pick(n.id, "LATEST", name(n), n.snippet(90).takeIf { it != name(n) } ?: "") }
        return Pick(null, "B-72", "Nothing here yet", "Add a note and it shows up here.")
    }
}

/** The note's picture as a rounded square for the widget. */
private fun roundedCover(path: String, px: Int): Pair<android.graphics.Bitmap, Int>? = runCatching {
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, o)
    val sample = maxOf(1, minOf(o.outWidth, o.outHeight) / px)
    val src = android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
    val side = minOf(src.width, src.height)
    val sq = android.graphics.Bitmap.createScaledBitmap(android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side), px, px, true)
    val out = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { shader = android.graphics.BitmapShader(sq, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP) }
    android.graphics.Canvas(out).drawRoundRect(android.graphics.RectF(0f, 0f, px.toFloat(), px.toFloat()), px * .28f, px * .28f, paint)
    val rgb = androidx.palette.graphics.Palette.from(sq).generate().let { (it.vibrantSwatch ?: it.dominantSwatch)?.rgb } ?: 0
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(rgb, hsl)
    out to (if (rgb != 0 && hsl[1] > .15f) hsl[0].toInt() else -1)
}.getOrNull()

internal fun paintWidgets(ctx: Context, mgr: AppWidgetManager, ids: IntArray, layout: Int, small: Boolean) {
    val notes = kotlinx.coroutines.runBlocking { Store.load(ctx).library.notes }
    val p = WidgetPick.pick(notes, System.currentTimeMillis())
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val coverPath = notes.firstOrNull { it.id == p.noteId }?.cover?.path
    val cover = if (!small && coverPath != null) roundedCover(coverPath, 320) else null
    val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    for (id in ids) {
        val v = RemoteViews(ctx.packageName, layout)
        if (!small) {
            if (cover != null) { v.setImageViewBitmap(R.id.photo, cover.first); v.setViewVisibility(R.id.photo, View.VISIBLE) } else v.setViewVisibility(R.id.photo, View.GONE)
            // the card takes the hue of its picture (Android 12 and up); text colours follow for contrast
            if (cover != null && cover.second >= 0 && android.os.Build.VERSION.SDK_INT >= 31) {
                val h = cover.second.toFloat()
                val bg = androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h, if (night) .35f else .6f, if (night) .18f else .86f))
                val ink = androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h, .4f, if (night) .92f else .1f))
                val acc = androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h, .7f, if (night) .72f else .32f))
                v.setColorStateList(R.id.root, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(bg))
                for (t in intArrayOf(R.id.route, R.id.status, R.id.at)) v.setTextColor(t, ink)
                v.setTextColor(R.id.kicker, acc)
                v.setColorStateList(R.id.action, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(acc))
                v.setTextColor(R.id.action, bg)
            }
        }
        v.setTextViewText(R.id.kicker, p.kicker)
        v.setTextViewText(R.id.route, p.head)
        v.setTextViewText(R.id.status, p.line)
        v.setViewVisibility(R.id.status, if (p.line.isBlank()) View.GONE else View.VISIBLE)
        if (!small) {
            v.setTextViewText(R.id.at, p.at)
            v.setViewVisibility(R.id.at, if (p.at.isBlank()) View.GONE else View.VISIBLE)
        }
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).apply { p.noteId?.let { putExtra(OPEN_KEY, it) } }
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, if (small) 11 else 1, open, flags))
        if (!small) {
            val a = p.action
            if (a != null) {
                v.setTextViewText(R.id.action, a.first)
                v.setViewVisibility(R.id.action, View.VISIBLE)
                v.setOnClickPendingIntent(R.id.action, PendingIntent.getActivity(ctx, 2, Intent(Intent.ACTION_VIEW, Uri.parse(a.second)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags))
            } else v.setViewVisibility(R.id.action, View.GONE)
        }
        mgr.updateAppWidget(id, v)
    }
}

/** The wide home-screen widget (4×2): the pick, its time, and a button when there is a useful link. */
class TripWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { paintWidgets(ctx.applicationContext, mgr, ids, R.layout.widget_trip, false) }
            pending.finish()
        }
    }

    companion object {
        /** Asks every placed widget, wide or small, to repaint (after notes change, and when a trip alert fires). */
        fun refresh(ctx: Context) {
            runCatching {
                val app = ctx.applicationContext
                val mgr = AppWidgetManager.getInstance(app)
                for (cls in listOf(TripWidget::class.java, TripWidgetSmall::class.java)) {
                    val ids = mgr.getAppWidgetIds(ComponentName(app, cls))
                    if (ids.isNotEmpty()) app.sendBroadcast(Intent(app, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
                }
            }
        }
    }
}

/** The 2×2 version: the same pick, with the words only. */
class TripWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { paintWidgets(ctx.applicationContext, mgr, ids, R.layout.widget_small, true) }
            pending.finish()
        }
    }
}
