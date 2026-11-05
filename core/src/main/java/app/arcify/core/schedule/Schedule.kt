package app.arcify.core.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class Repeat { ONCE, DAILY, WEEKDAYS, CUSTOM }

/** A planned focus session (spec F09). */
data class ScheduledSession(
    val id: Long = 0,
    val title: String,
    /** Minutes after local midnight, 0..1439. */
    val startMinute: Int,
    val durationMin: Int,
    val pomodoro: Boolean = false,
    val strict: Boolean = false,
    val repeat: Repeat = Repeat.ONCE,
    /** For [Repeat.CUSTOM]: bit 0 = Monday … bit 6 = Sunday. */
    val daysMask: Int = 0,
    /** For [Repeat.ONCE]: the calendar day, as epoch day. */
    val onceDate: Long? = null,
) {
    init {
        require(startMinute in 0 until 24 * 60) { "startMinute out of range" }
        require(durationMin >= 1) { "durationMin must be positive" }
    }
}

data class Occurrence(val session: ScheduledSession, val date: LocalDate, val startMs: Long) {
    val endMs: Long get() = startMs + session.durationMin * 60_000L
}

enum class OccurrenceStatus { UPCOMING, NOW, DONE, MISSED }

object ScheduleRules {

    fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun occursOn(session: ScheduledSession, date: LocalDate): Boolean = when (session.repeat) {
        Repeat.ONCE -> session.onceDate == date.toEpochDay()
        Repeat.DAILY -> true
        Repeat.WEEKDAYS -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
        Repeat.CUSTOM -> session.daysMask and dayBit(date.dayOfWeek) != 0
    }

    fun occurrence(session: ScheduledSession, date: LocalDate, zone: ZoneId): Occurrence {
        val start = date.atStartOfDay(zone).plusMinutes(session.startMinute.toLong()).toInstant().toEpochMilli()
        return Occurrence(session, date, start)
    }

    /** All occurrences on the days from [from] to [to] inclusive, in time order. */
    fun between(sessions: List<ScheduledSession>, from: LocalDate, to: LocalDate, zone: ZoneId): List<Occurrence> {
        val result = ArrayList<Occurrence>()
        var day = from
        while (!day.isAfter(to)) {
            sessions.filter { occursOn(it, day) }.forEach { result += occurrence(it, day, zone) }
            day = day.plusDays(1)
        }
        return result.sortedBy { it.startMs }
    }

    /** The first occurrence that starts after [nowMs], looking up to [horizonDays] ahead. */
    fun next(sessions: List<ScheduledSession>, nowMs: Long, zone: ZoneId, horizonDays: Long = 8): Occurrence? {
        val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return between(sessions, today, today.plusDays(horizonDays), zone).firstOrNull { it.startMs > nowMs }
    }

    /**
     * Done if a focus session started within 15 minutes before the slot or during it; otherwise
     * running now, upcoming or missed.
     */
    fun status(occurrence: Occurrence, focusStarts: List<Long>, nowMs: Long): OccurrenceStatus {
        val graceStart = occurrence.startMs - GRACE_MS
        if (focusStarts.any { it in graceStart..occurrence.endMs }) return OccurrenceStatus.DONE
        return when {
            nowMs < occurrence.startMs -> OccurrenceStatus.UPCOMING
            nowMs <= occurrence.endMs -> OccurrenceStatus.NOW
            else -> OccurrenceStatus.MISSED
        }
    }

    private const val GRACE_MS = 15 * 60_000L
}
