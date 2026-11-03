package com.b72.keep_canvas.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.b72.keep_canvas.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Once a week, on Sunday morning, a quiet notification brings back three older notes (at least two weeks old, starred ones first)
 * so the collection is something you revisit and not only add to. It can be switched off from the menu.
 */
object Digest {
    private const val CHANNEL = "digest"
    private const val PREFS = "digest"

    fun enabled(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        if (on) schedule(ctx) else cancel(ctx)
    }

    private fun pending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 7777, Intent(ctx, DigestReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The next Sunday at 10:00 local time; calling it again just replaces the same alarm. */
    fun schedule(ctx: Context) {
        if (!enabled(ctx)) return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).withHour(10).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val at = next.atZone(zone).toInstant().toEpochMilli()
        runCatching { ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC, at, pending(ctx)) }
    }

    fun cancel(ctx: Context) { runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx)) } }

    /** Three older notes, starred first, the rest picked by the week so they change every Sunday. */
    fun pick(notes: List<Note>, now: Long = System.currentTimeMillis()): List<Note> {
        val old = notes.filter { n ->
            val born = n.id.toLongOrNull()?.div(1000) ?: return@filter false
            now - born >= 14 * 86_400_000L && (n.title.isNotBlank() || n.snippet().isNotBlank())
        }
        if (old.isEmpty()) return emptyList()
        val week = (now / (7 * 86_400_000L)).toInt()
        val (stars, rest) = old.partition { it.starred }
        val shuffled = rest.sortedBy { (it.id.hashCode() * 31 + week).hashCode() }
        return (stars.sortedBy { (it.id.hashCode() + week).hashCode() } + shuffled).take(3)
    }

    fun notify(ctx: Context, notes: List<Note>) {
        if (notes.isEmpty()) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Weekly digest", NotificationManager.IMPORTANCE_LOW))
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val name = { n: Note -> n.title.ifBlank { n.snippet(60) } }
        val open = PendingIntent.getActivity(
            ctx, 7778, Intent(ctx, MainActivity::class.java).putExtra(OPEN_KEY, notes.first().id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Worth another look")
            .setContentText(notes.joinToString(" · ", transform = name))
            .setStyle(NotificationCompat.BigTextStyle().bigText(notes.joinToString("\n") { "• " + name(it) }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(7778, n) }
    }
}

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { if (Digest.enabled(app)) Digest.notify(app, Digest.pick(Store.load(app).library.notes)) }
            Digest.schedule(app)
            pending.finish()
        }
    }
}
