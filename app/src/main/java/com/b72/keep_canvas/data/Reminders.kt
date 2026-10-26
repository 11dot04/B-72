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
                Store.load(ctx.applicationContext).library.notes.filter { it.remind > now }.forEach { Reminders.schedule(ctx.applicationContext, it.id, it.title, it.remind) }
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
