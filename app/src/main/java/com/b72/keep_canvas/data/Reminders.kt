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

/** "Remind me about this note": one alarm per note, a notification that opens the note. */
object Reminders {
    private const val CHANNEL = "reminders"

    private fun channel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun alarm(ctx: Context, noteId: String, title: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, noteId.hashCode(),
            Intent(ctx, ReminderReceiver::class.java).putExtra("id", noteId).putExtra("title", title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Inexact on purpose (it needs no special permission and can be a few minutes late). */
    fun schedule(ctx: Context, noteId: String, title: String, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(ctx, noteId, title)) }
    }

    fun cancel(ctx: Context, noteId: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        runCatching { am.cancel(alarm(ctx, noteId, "")) }
    }

    fun notify(ctx: Context, noteId: String, title: String) {
        channel(ctx)
        if (Build_SDK_33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            ctx, noteId.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(OPEN_KEY, noteId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title.ifBlank { "A note you wanted to revisit" })
            .setContentText("Reminder from B-72")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(noteId.hashCode(), n) }
    }

    private val Build_SDK_33 get() = android.os.Build.VERSION.SDK_INT >= 33
}

const val OPEN_KEY = "b72.open"
const val NEW_KEY = "b72.new"

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.hasExtra("url")) { TripAlerts.notify(ctx, intent); com.b72.keep_canvas.widget.TripWidget.refresh(ctx); return }
        Reminders.notify(ctx, intent.getStringExtra("id").orEmpty(), intent.getStringExtra("title").orEmpty())
    }
}

/** Alarms do not survive a reboot, so the upcoming ones are set again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val now = System.currentTimeMillis()
                val notes = Store.load(ctx.applicationContext).library.notes
                notes.filter { it.remind > now }.forEach { Reminders.schedule(ctx.applicationContext, it.id, it.title, it.remind) }
                Trips.all(notes).forEach { (n, t) -> TripAlerts.schedule(ctx.applicationContext, n.id, n.title, t) }
            }
            pending.finish()
        }
    }
}

/** What a launcher shortcut or a reminder notification asks the app to do. */
class Launch(val openId: String?, val newKind: Kind?, val token: Long = System.nanoTime())

fun Intent.toLaunch(): Launch? {
    val id = getStringExtra(OPEN_KEY)
    val kind = getStringExtra(NEW_KEY)?.let { k -> Kind.entries.firstOrNull { it.name == k } }
    return if (id == null && kind == null) null else Launch(id, kind)
}

/**
 * Two alerts per journey: two days before (check the PNR) and three hours before (go, and see where the train is).
 * Each carries a button that opens the right status page.
 */
object TripAlerts {
    private const val CHANNEL = "trips"
    private const val H = 3_600_000L

    private fun code(noteId: String, slot: Int) = "trip$noteId#$slot".hashCode()

    private fun alarm(ctx: Context, noteId: String, slot: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code(noteId, slot),
            Intent(ctx, ReminderReceiver::class.java).putExtra("id", noteId).apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(ctx: Context, noteId: String, title: String, t: Trip) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val clock = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val at = java.time.Instant.ofEpochMilli(t.dep).atZone(java.time.ZoneId.systemDefault())
        val what = t.name.ifBlank { t.number }.ifBlank { t.mode }
        val head = listOf(t.route, what).filter { it.isNotBlank() }.joinToString(" · ")
        val early = t.pnrLink() ?: t.liveLink()
        val late = t.liveLink() ?: t.pnrLink()
        val whenText = if (t.timeKnown) "at ${clock.format(at)}" else "on ${at.toLocalDate()}"
        val plan = listOf(
            Triple(0, t.dep - 48 * H, early to (if (t.pnrLink() != null) "Leaves $whenText, in two days. Check that your seat is confirmed." else "Leaves $whenText, in two days.")),
            Triple(1, if (t.timeKnown) t.dep - 3 * H else t.dep + 6 * H, late to "Leaves $whenText. See where it is."),
        )
        for ((slot, time, content) in plan) {
            val (link, text) = content
            if (time <= now || link == null) { runCatching { am.cancel(alarm(ctx, noteId, slot)) }; continue }
            val pi = alarm(ctx, noteId, slot) { putExtra("title", head).putExtra("text", text).putExtra("url", link.url).putExtra("label", link.label) }
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi) }
        }
    }

    fun cancel(ctx: Context, noteId: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (slot in 0..1) runCatching { am.cancel(alarm(ctx, noteId, slot)) }
    }

    fun notify(ctx: Context, i: Intent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Trips", NotificationManager.IMPORTANCE_HIGH))
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = i.getStringExtra("id").orEmpty()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            ctx, id.hashCode(), Intent(ctx, MainActivity::class.java).putExtra(OPEN_KEY, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), flags,
        )
        val url = i.getStringExtra("url").orEmpty()
        val go = PendingIntent.getActivity(
            ctx, ("go$id${i.getStringExtra("label")}").hashCode(),
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(i.getStringExtra("title"))
            .setContentText(i.getStringExtra("text"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(i.getStringExtra("text")))
            .setContentIntent(open)
            .addAction(0, i.getStringExtra("label") ?: "Open", go)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(("trip$id${i.getStringExtra("label")}").hashCode(), n) }
    }
}
