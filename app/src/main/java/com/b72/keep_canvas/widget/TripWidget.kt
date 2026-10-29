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

internal fun paintWidgets(ctx: Context, mgr: AppWidgetManager, ids: IntArray, layout: Int, small: Boolean) {
    val notes = kotlinx.coroutines.runBlocking { Store.load(ctx).library.notes }
    val p = WidgetPick.pick(notes, System.currentTimeMillis())
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    for (id in ids) {
        val v = RemoteViews(ctx.packageName, layout)
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
