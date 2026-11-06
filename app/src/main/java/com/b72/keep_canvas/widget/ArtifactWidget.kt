package com.b72.keep_canvas.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import com.b72.keep_canvas.MainActivity
import com.b72.keep_canvas.R
import com.b72.keep_canvas.data.Store
import com.b72.keep_canvas.data.TAB_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One of your artifacts (a die-cut sticker or a stamp), on the home screen with nothing behind it. It changes every few hours, and a tap
 * opens the Artifacts tab.
 */
class ArtifactWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { paint(ctx.applicationContext, mgr, ids) }
            pending.finish()
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: android.os.Bundle?) {
        onUpdate(ctx, mgr, intArrayOf(id))
    }

    private fun paint(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val paths = kotlinx.coroutines.runBlocking { Store.load(ctx).library.stickers }
            .mapNotNull { ((it as? JsonObject)?.get("p") as? JsonPrimitive)?.content }
            .filter { java.io.File(it).exists() }
        val slot = System.currentTimeMillis() / (4 * 3_600_000L)
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(TAB_KEY, 3)
        val tap = PendingIntent.getActivity(ctx, 31, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        for (id in ids) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_artifact)
            val o = mgr.getAppWidgetOptions(id)
            val dp = minOf(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110))
            val px = (dp * ctx.resources.displayMetrics.density).toInt().coerceIn(160, 420)
            // each widget starts at a different artifact, so two of them never show the same one
            val bmp = if (paths.isEmpty()) null else load(paths[((slot + id) % paths.size).toInt()], px)
            if (bmp != null) { v.setImageViewBitmap(R.id.art, bmp); v.setViewVisibility(R.id.art, View.VISIBLE); v.setViewVisibility(R.id.empty, View.GONE) }
            else { v.setViewVisibility(R.id.art, View.GONE); v.setViewVisibility(R.id.empty, View.VISIBLE) }
            v.setOnClickPendingIntent(R.id.root, tap)
            mgr.updateAppWidget(id, v)
        }
    }

    private fun load(path: String, px: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= px) s *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()

    companion object {
        fun refresh(ctx: Context) {
            runCatching {
                val app = ctx.applicationContext
                val ids = AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, ArtifactWidget::class.java))
                if (ids.isNotEmpty()) app.sendBroadcast(Intent(app, ArtifactWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
            }
        }
    }
}
