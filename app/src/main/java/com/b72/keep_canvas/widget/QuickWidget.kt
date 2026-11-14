package com.b72.keep_canvas.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.SizeF
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

/**
 * The quick-add widget takes on a different look for the room it is given: a light pill with one button (2x1), a dark pill with the
 * main button and four tonal ones (4x1), and a four-lobed shape with the four around the main one (2x2). Android 12+ picks the layout
 * for the current size; older versions show the wide one.
 */
class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { push(ctx, mgr, it) } }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle?) { push(ctx, mgr, id) }

    private fun push(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val wide = wired(ctx, R.layout.widget_quick)
        if (Build.VERSION.SDK_INT >= 31) {
            val sizes = mapOf(
                SizeF(110f, 40f) to wired(ctx, R.layout.widget_quick_small),
                SizeF(200f, 40f) to wide,
                SizeF(110f, 110f) to wired(ctx, R.layout.widget_quick_square),
                SizeF(200f, 110f) to wide,
            )
            mgr.updateAppWidget(id, RemoteViews(sizes))
        } else mgr.updateAppWidget(id, wide)
    }

    private fun wired(ctx: Context, layout: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, layout)
        val buttons = listOf(R.id.q_note to "Text", R.id.q_list to "Checklist", R.id.q_voice to "Audio", R.id.q_draw to "Drawing", R.id.q_photo to "Photo")
        buttons.forEachIndexed { i, (view, kind) ->
            // each layout has only some of the buttons; the pending intent is attached only where the view exists
            if (hasView(layout, view)) rv.setOnClickPendingIntent(view, PendingIntent.getActivity(ctx, 700 + i, startIntent(ctx, kind), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        if (layout == R.layout.widget_quick_small) rv.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 720, startIntent(ctx, "Text"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return rv
    }

    private fun hasView(layout: Int, id: Int) = when (layout) {
        R.layout.widget_quick_small -> id == R.id.q_note
        else -> true
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
