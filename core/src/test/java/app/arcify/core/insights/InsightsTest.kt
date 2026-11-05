package app.arcify.core.insights

import app.arcify.core.nudge.NudgeInput
import app.arcify.core.nudge.NudgeKind
import app.arcify.core.nudge.NudgeRules
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class InsightsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 5)

    @Test
    fun usageSplitsByCategory() {
        val split = InsightRules.split(mapOf("social" to 300L, "docs" to 100L, "clock" to 5L)) { pkg ->
            when (pkg) {
                "social" -> AppCategory.DISTRACTING
                "docs" -> AppCategory.PRODUCTIVE
                else -> AppCategory.NEUTRAL
            }
        }
        assertEquals(CategorySplit(productiveMs = 100, neutralMs = 5, distractingMs = 300), split)
        assertEquals(405L, split.totalMs)
    }

    @Test
    fun focusIsSummedPerLocalDay() {
        val morning = LocalDateTime.of(2026, 11, 5, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val evening = LocalDateTime.of(2026, 11, 5, 21, 0).atZone(zone).toInstant().toEpochMilli()
        val nextDay = LocalDateTime.of(2026, 11, 6, 0, 30).atZone(zone).toInstant().toEpochMilli()
        val byDay = InsightRules.focusByDay(listOf(morning to 10L, evening to 20L, nextDay to 5L), zone)
        assertEquals(30L, byDay[InsightRules.dayKey(today, zone)])
        assertEquals(5L, byDay[InsightRules.dayKey(today.plusDays(1), zone)])
        assertEquals(today, InsightRules.dateOf(InsightRules.dayKey(today, zone), zone))
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        val achieved = setOf(today.minusDays(3), today.minusDays(2), today.minusDays(1))
        // Today isn't done yet: the streak still stands at 3.
        assertEquals(Streak(3, 3), StreakRules.streak(achieved, today, firstDay = today.minusDays(10)))
        // Achieving today extends it.
        assertEquals(Streak(4, 4), StreakRules.streak(achieved + today, today, firstDay = today.minusDays(10)))
    }

    @Test
    fun aMissedDayBreaksTheStreakButBestIsKept() {
        val achieved = setOf(today.minusDays(6), today.minusDays(5), today.minusDays(4), today.minusDays(1))
        assertEquals(Streak(1, 3), StreakRules.streak(achieved, today, firstDay = today.minusDays(6)))
    }

    @Test
    fun noHistoryMeansNoStreak() {
        assertEquals(Streak(0, 0), StreakRules.streak(emptySet(), today, firstDay = today.plusDays(1)))
    }

    @Test
    fun limitDaysNeedEveryLimitRespected() {
        val usage = mapOf(
            today.minusDays(2) to mapOf("social" to 10L, "video" to 5L),
            today.minusDays(1) to mapOf("social" to 40L),
        )
        val limits = mapOf("social" to 30L, "video" to 30L)
        assertEquals(setOf(today.minusDays(2)), StreakRules.limitDays(usage, limits))
        assertEquals(emptySet<LocalDate>(), StreakRules.limitDays(usage, emptyMap()))
    }

    @Test
    fun nudgesFireLateInTheDayAtMostOnce() {
        fun at(hour: Int) = LocalDateTime.of(2026, 11, 5, hour, 0).atZone(zone).toInstant().toEpochMilli()
        val base = NudgeInput(at(10), zone, focusedTodayMs = 0, goalMs = 60, streakBeforeToday = 3, lastSent = emptyMap())

        assertEquals(emptyList<NudgeKind>(), NudgeRules.due(base))
        assertEquals(listOf(NudgeKind.START_FOCUS), NudgeRules.due(base.copy(nowMs = at(18))))
        assertEquals(listOf(NudgeKind.START_FOCUS, NudgeKind.STREAK_AT_RISK), NudgeRules.due(base.copy(nowMs = at(21))))
        assertEquals(
            listOf(NudgeKind.STREAK_AT_RISK),
            NudgeRules.due(base.copy(nowMs = at(21), lastSent = mapOf(NudgeKind.START_FOCUS to today))),
        )
        // Goal met or no streak worth protecting: quiet.
        assertEquals(emptyList<NudgeKind>(), NudgeRules.due(base.copy(nowMs = at(21), focusedTodayMs = 60)))
        assertEquals(listOf(NudgeKind.START_FOCUS), NudgeRules.due(base.copy(nowMs = at(21), streakBeforeToday = 1)))
    }
}
