package app.arcify.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.arcify.MainActivity
import app.arcify.blocker.Diag
import app.arcify.core.alarm.WakeAlarm
import app.arcify.core.alarm.WakeAlarmRules
import app.arcify.core.data.WakeAlarmRepository
import app.arcify.work.appEntryPoint
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Arms study wake-up alarms (spec F14) with Android's alarm-clock API: exact, allowed while
 * idle, and shown as the phone's next alarm. One alarm is armed at a time (the earliest), plus
 * an optional pending snooze.
 */
object WakeAlarms {
    internal const val EXTRA_ALARM_ID = "alarm_id"
    internal const val EXTRA_SNOOZES_USED = "snoozes_used"
    private const val RING_REQUEST = 7000
    private const val SNOOZE_REQUEST = 7001

    suspend fun rearm(context: Context, repository: WakeAlarmRepository) {
        val next = WakeAlarmRules.next(repository.all(), System.currentTimeMillis(), ZoneId.systemDefault())
        val pending = ringIntent(context, RING_REQUEST, next?.first?.id ?: -1L, snoozesUsed = 0)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        if (next == null) {
            alarms.cancel(pending)
            Diag.log("wake alarm: none armed")
            return
        }
        setAlarmClock(context, alarms, next.second, pending)
        Diag.log("wake alarm ${next.first.id} armed for ${java.time.Instant.ofEpochMilli(next.second)}")
    }

    /** Rings [alarm] again in its snooze time, counting the snooze. */
    fun snooze(context: Context, alarm: WakeAlarm, snoozesUsed: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        setAlarmClock(context, alarms, at, ringIntent(context, SNOOZE_REQUEST, alarm.id, snoozesUsed + 1))
    }

    private fun setAlarmClock(context: Context, alarms: AlarmManager, at: Long, operation: PendingIntent) {
        val show = PendingIntent.getActivity(
            context,
            RING_REQUEST,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } catch (_: SecurityException) {
            // Exact alarms denied: ring slightly late rather than not at all.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun ringIntent(context: Context, request: Int, alarmId: Long, snoozesUsed: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, WakeAlarmReceiver::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_SNOOZES_USED, snoozesUsed),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** The alarm time has come: start ringing, then switch off one-time alarms and arm the next one. */
class WakeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(WakeAlarms.EXTRA_ALARM_ID, -1L)
        val snoozesUsed = intent.getIntExtra(WakeAlarms.EXTRA_SNOOZES_USED, 0)
        Diag.log("wake alarm $alarmId ringing (snoozes used: $snoozesUsed)")
        ContextCompat.startForegroundService(context, WakeAlarmService.ringIntent(context, alarmId, snoozesUsed))
        val pending = goAsync()
        val repository = context.appEntryPoint().wakeAlarmRepository()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // A snooze re-ring doesn't count as a new ring of the alarm.
                if (snoozesUsed == 0) repository.get(alarmId)?.let { repository.save(WakeAlarmRules.afterRinging(it)) }
                WakeAlarms.rearm(context, repository)
            } finally {
                pending.finish()
            }
        }
    }
}
