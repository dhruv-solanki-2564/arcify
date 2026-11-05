package app.arcify.block

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import app.arcify.R
import app.arcify.core.util.formatShortDuration
import app.arcify.timer.TimerNotifications

object BlockNotifications {
    private const val CHANNEL_LIMITS = "limits"
    private const val REMINDER_ID_BASE = 2000

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_LIMITS,
                context.getString(R.string.channel_limits),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun postReminder(context: Context, packageName: String, label: String, usedMs: Long, limitMs: Long) {
        val notification = NotificationCompat.Builder(context, CHANNEL_LIMITS)
            .setSmallIcon(R.drawable.ic_stat_arcify)
            .setContentTitle(context.getString(R.string.limit_reminder_title, label))
            .setContentText(
                context.getString(R.string.limit_reminder_text, formatShortDuration(usedMs), formatShortDuration(limitMs)),
            )
            .setAutoCancel(true)
            .build()
        TimerNotifications.post(context, REMINDER_ID_BASE + (packageName.hashCode() and 0xFFF), notification)
    }
}
