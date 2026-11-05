package app.arcify.core.alarm

import app.arcify.core.schedule.ScheduleRules
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * A study wake-up alarm (spec F14). It can only be silenced by starting a focus session or by
 * solving a short task, and it has a limited number of snoozes.
 */
data class WakeAlarm(
    val id: Long = 0,
    /** Minutes after local midnight, 0..1439. */
    val minuteOfDay: Int,
    /** Bit 0 = Monday … bit 6 = Sunday. 0 means "once", at the next occurrence of the time. */
    val daysMask: Int = 0,
    val enabled: Boolean = true,
    /** How many times it may be snoozed per ring (0 = no snooze). */
    val snoozesAllowed: Int = DEFAULT_SNOOZES,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MIN,
    /** Length of the focus session started from the alarm. */
    val focusMinutes: Int = DEFAULT_FOCUS_MIN,
) {
    init {
        require(minuteOfDay in 0 until 24 * 60) { "minuteOfDay out of range" }
        require(snoozesAllowed in 0..MAX_SNOOZES) { "snoozesAllowed out of range" }
        require(snoozeMinutes >= 1 && focusMinutes >= 1) { "durations must be positive" }
    }

    val repeats: Boolean get() = daysMask != 0

    companion object {
        const val DEFAULT_SNOOZES = 2
        const val DEFAULT_SNOOZE_MIN = 5
        const val DEFAULT_FOCUS_MIN = 25
        const val MAX_SNOOZES = 3
    }
}

object WakeAlarmRules {

    /** The next time [alarm] rings after [nowMs], or null if it is switched off. */
    fun nextTrigger(alarm: WakeAlarm, nowMs: Long, zone: ZoneId): Long? {
        if (!alarm.enabled) return null
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (alarm.repeats && alarm.daysMask and ScheduleRules.dayBit(date.dayOfWeek) == 0) continue
            val at = at(date, alarm.minuteOfDay, zone)
            if (at > nowMs) return at
        }
        return null
    }

    /** The earliest ring across [alarms], with the alarm that rings. */
    fun next(alarms: List<WakeAlarm>, nowMs: Long, zone: ZoneId): Pair<WakeAlarm, Long>? =
        alarms.mapNotNull { alarm -> nextTrigger(alarm, nowMs, zone)?.let { alarm to it } }.minByOrNull { it.second }

    fun canSnooze(alarm: WakeAlarm, snoozesUsed: Int): Boolean = snoozesUsed < alarm.snoozesAllowed

    /** A one-time alarm switches itself off once it has rung. */
    fun afterRinging(alarm: WakeAlarm): WakeAlarm = if (alarm.repeats) alarm else alarm.copy(enabled = false)

    private fun at(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Long =
        date.atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()
}

/** One sum the user has to solve to dismiss the alarm without starting a focus session. */
data class WakeProblem(val a: Int, val b: Int) {
    val answer: Int get() = a + b
}

/** The "short task" that dismisses an alarm (spec F14): a few two-digit additions, new every time. */
object WakeTask {
    const val PROBLEMS = 3

    fun newTask(random: Random = SecureRandom().asKotlinRandom()): List<WakeProblem> =
        List(PROBLEMS) { WakeProblem(random.nextInt(12, 90), random.nextInt(12, 90)) }

    /** Every answer must be right; spaces are ignored. */
    fun isSolved(task: List<WakeProblem>, answers: List<String>): Boolean =
        task.size == answers.size && task.zip(answers).all { (problem, answer) -> answer.trim().toIntOrNull() == problem.answer }
}
