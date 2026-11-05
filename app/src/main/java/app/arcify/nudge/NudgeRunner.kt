package app.arcify.nudge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.arcify.MainActivity
import app.arcify.R
import app.arcify.core.data.InsightsRepository
import app.arcify.core.data.TimerSettingsRepository
import app.arcify.core.insights.InsightRules
import app.arcify.core.insights.StreakRules
import app.arcify.core.nudge.NudgeInput
import app.arcify.core.nudge.NudgeKind
import app.arcify.core.nudge.NudgeRules
import app.arcify.core.timer.MINUTE_MS
import app.arcify.core.timer.WallClock
import app.arcify.timer.TimerNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Checks the local nudge rules and posts any that are due (spec F10). Run by the hourly worker. */
@Singleton
class NudgeRunner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: TimerSettingsRepository,
    private val insights: InsightsRepository,
    private val clock: WallClock,
) {
    suspend fun run() {
        if (!settings.nudgesEnabled.first()) return
        val zone = ZoneId.systemDefault()
        val now = clock.nowMs()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val goalMs = settings.dailyGoalMinutes.first() * MINUTE_MS

        val since = InsightRules.dayKey(today.minusDays(HISTORY_DAYS), zone)
        val focus = InsightRules.focusByDay(insights.observeFocusSince(since).first().map { it.startedAt to it.focusedMs }, zone)
        val focusedToday = focus[InsightRules.dayKey(today, zone)] ?: 0L
        val achieved = focus.filterValues { it >= goalMs }.keys.map { InsightRules.dateOf(it, zone) }.toSet() - today
        val firstDay = focus.keys.minOrNull()?.let { InsightRules.dateOf(it, zone) } ?: today
        val streak = StreakRules.streak(achieved, today.minusDays(1), firstDay).current

        val due = NudgeRules.due(NudgeInput(now, zone, focusedToday, goalMs, streak, settings.lastNudges()))
        for (kind in due) {
            NudgeNotifications.post(context, kind, streak)
            settings.markNudged(kind, today)
        }
    }

    private companion object {
        const val HISTORY_DAYS = 60L
    }
}

object NudgeNotifications {
    private const val CHANNEL = "nudges"
    private const val ID_BASE = 4000

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_nudges), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun post(context: Context, kind: NudgeKind, streak: Int) {
        val (title, text) = when (kind) {
            NudgeKind.START_FOCUS -> context.getString(R.string.nudge_start_title) to context.getString(R.string.nudge_start_text)
            NudgeKind.STREAK_AT_RISK ->
                context.getString(R.string.nudge_streak_title, streak) to context.getString(R.string.nudge_streak_text)
        }
        val open = PendingIntent.getActivity(
            context,
            ID_BASE + kind.ordinal,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_arcify)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        TimerNotifications.post(context, ID_BASE + kind.ordinal, notification)
    }
}
