package app.arcify.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.arcify.R
import app.arcify.core.batching.BatchingRules
import app.arcify.core.batching.BatchingSettings
import app.arcify.core.data.BatchingRepository
import app.arcify.core.data.HeldNotificationEntity
import app.arcify.timer.TimerNotifications
import app.arcify.work.appEntryPoint
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Notification batching (spec F13). Notifications from the chosen apps are taken out of the
 * shade as they arrive and kept on the phone; [BatchDelivery] gives them back together at the
 * next delivery time. Calls, alarms and ongoing notifications are never touched.
 */
class ArcifyNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var settings = BatchingSettings()

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch { appEntryPoint().batchingRepository().settings.collect { settings = it } }
    }

    override fun onListenerDisconnected() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val notification = sbn.notification ?: return
        val ongoing = notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0
        if (!BatchingRules.shouldHold(sbn.packageName, settings, ongoing, notification.category, packageName)) return

        cancelNotification(sbn.key)
        // Group summaries only repeat their children; drop them without storing.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras
        val held = HeldNotificationEntity(
            packageName = sbn.packageName,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            postedAt = sbn.postTime,
        )
        val interval = settings.intervalMinutes
        scope.launch {
            appEntryPoint().batchingRepository().hold(held)
            BatchDelivery.schedule(this@ArcifyNotificationListener, interval)
        }
    }

    companion object {
        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Asks Android to reconnect the listener (it sometimes stays unbound after an update). */
        fun requestRebind(context: Context) {
            requestRebind(ComponentName(context, ArcifyNotificationListener::class.java))
        }
    }
}

/** Gives held notifications back, one summary per app, at fixed times aligned to the clock. */
object BatchDelivery {
    private const val CHANNEL = "batched"
    private const val REQUEST = 7200
    private const val ID_BASE = 5000
    private const val MAX_LINES = 6

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_batched), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Arms the next delivery. Re-arming replaces the previous alarm, so calling it often is fine. */
    fun schedule(context: Context, intervalMinutes: Int) {
        val at = BatchingRules.nextDelivery(System.currentTimeMillis(), intervalMinutes, ZoneId.systemDefault())
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, BatchDeliveryReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    /** Posts everything held now and forgets it. */
    suspend fun deliver(context: Context, repository: BatchingRepository) {
        val held = repository.held()
        if (held.isEmpty()) return
        val pm = context.packageManager
        for ((pkg, items) in held.groupBy { it.packageName }) {
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg
            }
            val open = pm.getLaunchIntentForPackage(pkg)?.let { launch ->
                PendingIntent.getActivity(context, pkg.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            val style = NotificationCompat.InboxStyle()
            items.takeLast(MAX_LINES).forEach { item ->
                style.addLine(listOfNotNull(item.title, item.text).joinToString(": ").ifEmpty { label })
            }
            if (items.size > MAX_LINES) style.setSummaryText(context.getString(R.string.batched_more, items.size - MAX_LINES))
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_arcify)
                .setContentTitle(context.getString(R.string.batched_title, label, items.size))
                .setContentText(items.last().let { listOfNotNull(it.title, it.text).joinToString(": ") })
                .setStyle(style)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            TimerNotifications.post(context, ID_BASE + (pkg.hashCode() and 0xFFF), notification)
        }
        repository.delivered(held.map { it.id })
    }
}

class BatchDeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val repository = context.appEntryPoint().batchingRepository()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                BatchDelivery.deliver(context, repository)
                // Keep delivering on schedule while batching is on (holding re-arms it too).
                val settings = repository.settings.first()
                if (settings.enabled) BatchDelivery.schedule(context, settings.intervalMinutes)
            } finally {
                pending.finish()
            }
        }
    }
}
