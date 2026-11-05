package app.arcify.schedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.arcify.MainActivity
import app.arcify.R
import app.arcify.core.data.ScheduleRepository
import app.arcify.core.data.TimerSettingsRepository
import app.arcify.core.schedule.ScheduleRules
import app.arcify.core.schedule.ScheduledSession
import app.arcify.core.timer.MINUTE_MS
import app.arcify.core.timer.PomodoroConfig
import app.arcify.core.timer.TimerController
import app.arcify.core.timer.TimerMode
import app.arcify.timer.FocusTimerService
import app.arcify.timer.TimerNotifications
import app.arcify.work.appEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Starts focus sessions for scheduled slots (spec F09). */
@Singleton
class ScheduledStarter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val controller: TimerController,
    private val settings: TimerSettingsRepository,
) {
    suspend fun start(session: ScheduledSession): Boolean {
        val mode = modeFor(session, settings.pomodoro.first())
        val started = controller.start(mode, session.strict)
        if (started) FocusTimerService.start(context)
        return started
    }

    /** One-tap start from the widget: the user's Pomodoro defaults, not strict. */
    suspend fun startQuick(): Boolean {
        val started = controller.start(TimerMode.Pomodoro(settings.pomodoro.first()), strict = false)
        if (started) FocusTimerService.start(context)
        return started
    }

    companion object {
        /** Pomodoro slots run as many focus + short break cycles as fit, at least one. */
        fun modeFor(session: ScheduledSession, pomodoro: PomodoroConfig): TimerMode =
            if (session.pomodoro) {
                val cycleMs = pomodoro.focusMs + pomodoro.shortBreakMs
                val cycles = (session.durationMin * MINUTE_MS / cycleMs).toInt().coerceAtLeast(1)
                TimerMode.Pomodoro(pomodoro.copy(targetCycles = cycles))
            } else {
                TimerMode.Countdown(session.durationMin * MINUTE_MS)
            }
    }
}

/** Keeps exactly one alarm armed: the next scheduled slot across all sessions. */
object ScheduleAlarms {
    internal const val EXTRA_SESSION_ID = "session_id"

    suspend fun rearm(context: Context, repository: ScheduleRepository) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val next = ScheduleRules.next(repository.all(), System.currentTimeMillis(), ZoneId.systemDefault())
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ScheduleAlarmReceiver::class.java).putExtra(EXTRA_SESSION_ID, next?.session?.id ?: -1L),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (next == null) {
            alarms.cancel(pending)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.startMs, pending)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.startMs, pending)
        }
    }
}

object ScheduleNotifications {
    private const val CHANNEL = "schedule"
    private const val ID_BASE = 3000
    internal const val ACTION_START = "app.arcify.schedule.START"

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_schedule), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun post(context: Context, session: ScheduledSession, started: Boolean) {
        val open = PendingIntent.getActivity(
            context,
            ID_BASE,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_arcify)
            .setContentTitle(
                context.getString(if (started) R.string.schedule_started_title else R.string.schedule_due_title, session.title),
            )
            .setContentText(context.getString(R.string.schedule_due_text, session.durationMin))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (!started) {
            val start = PendingIntent.getBroadcast(
                context,
                session.id.toInt(),
                Intent(context, ScheduleStartReceiver::class.java)
                    .setAction(ACTION_START)
                    .putExtra(ScheduleAlarms.EXTRA_SESSION_ID, session.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, context.getString(R.string.schedule_start_now), start)
        }
        TimerNotifications.post(context, notificationId(session), builder.build())
    }

    fun cancel(context: Context, session: ScheduledSession) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(session))
    }

    private fun notificationId(session: ScheduledSession) = ID_BASE + (session.id.toInt() and 0xFFF)
}

/** Runs a short suspend block from a receiver without blocking its thread. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** A scheduled slot has arrived: strict slots start by themselves, others get a "Start now" notification. */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deps = context.appEntryPoint()
        runAsync {
            val session = deps.scheduleRepository().get(intent.getLongExtra(ScheduleAlarms.EXTRA_SESSION_ID, -1L))
            if (session != null) {
                val started = session.strict && deps.scheduledStarter().start(session)
                ScheduleNotifications.post(context, session, started)
            }
            ScheduleAlarms.rearm(context, deps.scheduleRepository())
        }
    }
}

/** "Start now" on a schedule notification. */
class ScheduleStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deps = context.appEntryPoint()
        runAsync {
            val session = deps.scheduleRepository().get(intent.getLongExtra(ScheduleAlarms.EXTRA_SESSION_ID, -1L)) ?: return@runAsync
            deps.scheduledStarter().start(session)
            ScheduleNotifications.cancel(context, session)
        }
    }
}

/** Alarms are lost on reboot and shift with clock changes, so re-arm then (and after updates). */
class ScheduleRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deps = context.appEntryPoint()
        runAsync { ScheduleAlarms.rearm(context, deps.scheduleRepository()) }
    }
}
