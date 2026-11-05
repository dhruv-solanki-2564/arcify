package app.arcify.core.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val thursday = LocalDate.of(2026, 11, 5)
    private val saturday = LocalDate.of(2026, 11, 7)

    private fun session(repeat: Repeat, mask: Int = 0, once: LocalDate? = null, start: Int = 9 * 60, id: Long = 1) =
        ScheduledSession(id, "Study", start, 50, repeat = repeat, daysMask = mask, onceDate = once?.toEpochDay())

    private fun ms(date: LocalDate, hour: Int, minute: Int = 0) =
        LocalDateTime.of(date.year, date.month, date.dayOfMonth, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun repeatsMatchTheRightDays() {
        assertTrue(ScheduleRules.occursOn(session(Repeat.DAILY), saturday))
        assertTrue(ScheduleRules.occursOn(session(Repeat.WEEKDAYS), thursday))
        assertFalse(ScheduleRules.occursOn(session(Repeat.WEEKDAYS), saturday))
        val satOnly = ScheduleRules.dayBit(DayOfWeek.SATURDAY)
        assertTrue(ScheduleRules.occursOn(session(Repeat.CUSTOM, mask = satOnly), saturday))
        assertFalse(ScheduleRules.occursOn(session(Repeat.CUSTOM, mask = satOnly), thursday))
        assertTrue(ScheduleRules.occursOn(session(Repeat.ONCE, once = thursday), thursday))
        assertFalse(ScheduleRules.occursOn(session(Repeat.ONCE, once = thursday), saturday))
    }

    @Test
    fun occurrencesAreListedInTimeOrder() {
        val evening = session(Repeat.DAILY, start = 20 * 60, id = 2)
        val morning = session(Repeat.WEEKDAYS, start = 8 * 60, id = 3)
        val list = ScheduleRules.between(listOf(evening, morning), thursday, thursday.plusDays(2), zone)
        // Thu 8:00, Thu 20:00, Fri 8:00, Fri 20:00, Sat 20:00
        assertEquals(listOf(3L, 2L, 3L, 2L, 2L), list.map { it.session.id })
        assertEquals(ms(thursday, 8), list.first().startMs)
    }

    @Test
    fun nextIsTheFirstSlotStillAhead() {
        val daily = session(Repeat.DAILY, start = 9 * 60)
        assertEquals(ms(thursday, 9), ScheduleRules.next(listOf(daily), ms(thursday, 7), zone)?.startMs)
        assertEquals(ms(thursday.plusDays(1), 9), ScheduleRules.next(listOf(daily), ms(thursday, 10), zone)?.startMs)
        assertNull(ScheduleRules.next(listOf(session(Repeat.ONCE, once = thursday)), ms(thursday, 10), zone))
    }

    @Test
    fun statusFollowsRecordedFocusAndTheClock() {
        val occ = ScheduleRules.occurrence(session(Repeat.DAILY), thursday, zone) // 9:00–9:50
        assertEquals(OccurrenceStatus.UPCOMING, ScheduleRules.status(occ, emptyList(), ms(thursday, 8)))
        assertEquals(OccurrenceStatus.NOW, ScheduleRules.status(occ, emptyList(), ms(thursday, 9, 10)))
        assertEquals(OccurrenceStatus.MISSED, ScheduleRules.status(occ, emptyList(), ms(thursday, 11)))
        // Starting 10 minutes early still counts.
        assertEquals(OccurrenceStatus.DONE, ScheduleRules.status(occ, listOf(ms(thursday, 8, 50)), ms(thursday, 11)))
        assertEquals(OccurrenceStatus.MISSED, ScheduleRules.status(occ, listOf(ms(thursday, 8, 0)), ms(thursday, 11)))
    }
}
