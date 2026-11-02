package com.b72.keep_canvas.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.b72.keep_canvas.MainActivity
import com.b72.keep_canvas.R
import com.b72.keep_canvas.data.NEW_KEY

private fun startIntent(ctx: Context, kind: String) = Intent(ctx, MainActivity::class.java)
    .setAction(Intent.ACTION_MAIN)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    .putExtra(NEW_KEY, kind)

/** One row of four buttons on the home screen: note, list, photo, voice. Each starts a fresh note of that kind. */
class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_quick)
        listOf(R.id.q_note to "Text", R.id.q_list to "Checklist", R.id.q_photo to "Photo", R.id.q_voice to "Audio").forEachIndexed { i, (view, kind) ->
            rv.setOnClickPendingIntent(view, PendingIntent.getActivity(ctx, 700 + i, startIntent(ctx, kind), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        ids.forEach { mgr.updateAppWidget(it, rv) }
    }
}

/** A quick-settings tile: pull down, tap, and a new note is open. */
class QuickTile : TileService() {
    override fun onStartListening() {
        qsTile?.let { it.state = Tile.STATE_INACTIVE; it.label = getString(R.string.tile_label); it.icon = Icon.createWithResource(this, R.drawable.ic_tile); it.updateTile() }
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        val i = startIntent(this, "Text")
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 710, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else startActivityAndCollapse(i)
    }
}
