package app.arcify.core.nudge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Local, rule-based nudges (spec F10). No server and no tracking. */
enum class NudgeKind {
    /** Late in the day with no focus yet. */
    START_FOCUS,

    /** A focus-goal streak would break tonight. */
    STREAK_AT_RISK,
}

data class NudgeInput(
    val nowMs: Long,
    val zone: ZoneId,
    val focusedTodayMs: Long,
    val goalMs: Long,
    /** Focus-goal streak up to yesterday. */
    val streakBeforeToday: Int,
    /** Last day each nudge was sent, so each fires at most once a day. */
    val lastSent: Map<NudgeKind, LocalDate>,
)

object NudgeRules {
    const val START_FOCUS_HOUR = 17
    const val STREAK_RISK_HOUR = 20
    const val MIN_STREAK_TO_PROTECT = 2

    fun due(input: NudgeInput): List<NudgeKind> {
        val now = Instant.ofEpochMilli(input.nowMs).atZone(input.zone)
        val today = now.toLocalDate()
        val hour = now.hour
        fun notSentToday(kind: NudgeKind) = input.lastSent[kind] != today

        return buildList {
            if (hour >= START_FOCUS_HOUR && input.focusedTodayMs == 0L && notSentToday(NudgeKind.START_FOCUS)) {
                add(NudgeKind.START_FOCUS)
            }
            if (hour >= STREAK_RISK_HOUR &&
                input.streakBeforeToday >= MIN_STREAK_TO_PROTECT &&
                input.focusedTodayMs < input.goalMs &&
                notSentToday(NudgeKind.STREAK_AT_RISK)
            ) {
                add(NudgeKind.STREAK_AT_RISK)
            }
        }
    }
}
