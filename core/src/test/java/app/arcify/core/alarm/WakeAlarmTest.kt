package app.arcify.core.alarm

import app.arcify.core.schedule.ScheduleRules
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeAlarmTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val thursday = LocalDate.of(2026, 11, 5)

    private fun ms(date: LocalDate, hour: Int, minute: Int = 0) =
        LocalDateTime.of(date.year, date.month, date.dayOfMonth, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun aOneTimeAlarmRingsTodayOrTomorrow() {
        val sixThirty = WakeAlarm(minuteOfDay = 6 * 60 + 30)
        assertEquals(ms(thursday, 6, 30), WakeAlarmRules.nextTrigger(sixThirty, ms(thursday, 5), zone))
        assertEquals(ms(thursday.plusDays(1), 6, 30), WakeAlarmRules.nextTrigger(sixThirty, ms(thursday, 7), zone))
    }

    @Test
    fun repeatingAlarmsSkipUnchosenDays() {
        val saturdaysOnly = WakeAlarm(minuteOfDay = 7 * 60, daysMask = ScheduleRules.dayBit(DayOfWeek.SATURDAY))
        assertEquals(ms(thursday.plusDays(2), 7), WakeAlarmRules.nextTrigger(saturdaysOnly, ms(thursday, 8), zone))
    }

    @Test
    fun switchedOffAlarmsNeverRing() {
        assertNull(WakeAlarmRules.nextTrigger(WakeAlarm(minuteOfDay = 60, enabled = false), ms(thursday, 0), zone))
    }

    @Test
    fun theEarliestAlarmWins() {
        val early = WakeAlarm(id = 1, minuteOfDay = 6 * 60)
        val late = WakeAlarm(id = 2, minuteOfDay = 8 * 60)
        assertEquals(1L, WakeAlarmRules.next(listOf(late, early), ms(thursday, 5), zone)?.first?.id)
    }

    @Test
    fun snoozesAreLimitedAndOneTimeAlarmsSwitchOff() {
        val alarm = WakeAlarm(minuteOfDay = 360, snoozesAllowed = 2)
        assertTrue(WakeAlarmRules.canSnooze(alarm, 1))
        assertFalse(WakeAlarmRules.canSnooze(alarm, 2))
        assertFalse(WakeAlarmRules.canSnooze(alarm.copy(snoozesAllowed = 0), 0))
        assertFalse(WakeAlarmRules.afterRinging(alarm).enabled)
        assertTrue(WakeAlarmRules.afterRinging(alarm.copy(daysMask = 0b11111)).enabled)
    }

    @Test
    fun theTaskNeedsEveryAnswerRight() {
        val task = WakeTask.newTask(Random(7))
        assertEquals(WakeTask.PROBLEMS, task.size)
        assertTrue(task.all { it.a in 12..89 && it.b in 12..89 })
        val right = task.map { " ${it.answer} " }
        assertTrue(WakeTask.isSolved(task, right))
        assertFalse(WakeTask.isSolved(task, right.dropLast(1) + "0"))
        assertFalse(WakeTask.isSolved(task, right.dropLast(1)))
    }
}
