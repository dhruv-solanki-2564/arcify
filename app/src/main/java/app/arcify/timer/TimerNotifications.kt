package app.arcify.timer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.arcify.MainActivity
import app.arcify.R
import app.arcify.core.timer.ActiveTimer
import app.arcify.core.timer.FocusTimer
import app.arcify.core.util.formatDuration

object TimerNotifications {
    const val CHANNEL_ONGOING = "timer_ongoing"
    const val CHANNEL_ALERTS = "timer_alerts"
    const val ONGOING_ID = 1001
    private const val ALERT_ID = 1002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_ONGOING,
                    context.getString(R.string.channel_timer),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_ALERTS,
                    context.getString(R.string.channel_timer_alerts),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            ),
        )
    }

    fun starting(context: Context): Notification =
        baseBuilder(context, CHANNEL_ONGOING)
            .setContentTitle(context.getString(R.string.notif_starting))
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun ongoing(context: Context, timer: ActiveTimer, now: Long): Notification {
        val time = formatDuration(TimerText.displayMs(timer, now))
        val builder = baseBuilder(context, CHANNEL_ONGOING)
            .setContentTitle(TimerText.phaseLabel(context, timer))
            .setContentText(if (timer.isRunning) time else context.getString(R.string.paused_at, time))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)

        if (FocusTimer.canPause(timer)) {
            builder.addAction(0, context.getString(R.string.pause), serviceIntent(context, FocusTimerService.ACTION_PAUSE))
        } else if (!timer.isRunning) {
            builder.addAction(0, context.getString(R.string.resume), serviceIntent(context, FocusTimerService.ACTION_RESUME))
        }
        if (FocusTimer.canStop(timer)) {
            builder.addAction(0, context.getString(R.string.stop), serviceIntent(context, FocusTimerService.ACTION_STOP))
        }
        return builder.build()
    }

    fun alert(context: Context, title: String, text: String?): Notification =
        baseBuilder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

    /** Posts a notification if the user allowed notifications; otherwise does nothing. */
    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, notification: Notification) {
        if (!canPost(context)) return
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun postAlert(context: Context, title: String, text: String?) = post(context, ALERT_ID, alert(context, title, text))

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun baseBuilder(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_arcify)
            .setContentIntent(openAppIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        Intent(context, FocusTimerService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
