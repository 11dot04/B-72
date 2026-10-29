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

/** Home-screen widget: the next journey, how long to go, and one button for the status page that matters right now. */
class TripWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { paint(ctx.applicationContext, mgr, ids) }
            pending.finish()
        }
    }

    private suspend fun paint(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val notes = Store.load(ctx).library.notes
        val next = Trips.next(notes)
        val now = System.currentTimeMillis()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        for (id in ids) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_trip)
            if (next == null) {
                v.setTextViewText(R.id.kicker, "NEXT TRIP")
                v.setTextViewText(R.id.route, "Nothing planned")
                v.setTextViewText(R.id.status, "Share a ticket or booking to B-72 and it shows up here.")
                v.setViewVisibility(R.id.at, View.GONE)
                v.setViewVisibility(R.id.action, View.GONE)
                v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags))
            } else {
                val (note, t) = next
                val link = t.links(t.phase(now)).firstOrNull()
                v.setTextViewText(R.id.kicker, listOf(t.mode.uppercase(), t.number, t.name.uppercase()).filter { it.isNotBlank() }.joinToString(" · "))
                v.setTextViewText(R.id.route, t.route.ifBlank { note.title })
                v.setTextViewText(R.id.status, tripStatus(t, now))
                val fmt = DateTimeFormatter.ofPattern(if (t.timeKnown) "EEE d MMM · HH:mm" else "EEE d MMM")
                v.setTextViewText(R.id.at, Instant.ofEpochMilli(t.dep).atZone(ZoneId.systemDefault()).format(fmt))
                v.setViewVisibility(R.id.at, View.VISIBLE)
                v.setOnClickPendingIntent(
                    R.id.root,
                    PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java).putExtra(OPEN_KEY, note.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), flags),
                )
                if (link != null) {
                    v.setTextViewText(R.id.action, link.label)
                    v.setViewVisibility(R.id.action, View.VISIBLE)
                    v.setOnClickPendingIntent(R.id.action, PendingIntent.getActivity(ctx, 2, Intent(Intent.ACTION_VIEW, Uri.parse(link.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags))
                } else v.setViewVisibility(R.id.action, View.GONE)
            }
            mgr.updateAppWidget(id, v)
        }
    }

    companion object {
        /** Asks every placed widget to repaint (after notes change, and when a trip alert fires). */
        fun refresh(ctx: Context) {
            runCatching {
                val app = ctx.applicationContext
                val ids = AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, TripWidget::class.java))
                if (ids.isEmpty()) return
                app.sendBroadcast(Intent(app, TripWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
            }
        }
    }
}
