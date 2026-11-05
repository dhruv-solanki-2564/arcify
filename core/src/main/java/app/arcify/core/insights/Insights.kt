package app.arcify.core.insights

import app.arcify.core.timer.DayBounds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How an app's time counts in insights (spec F08). */
enum class AppCategory { PRODUCTIVE, NEUTRAL, DISTRACTING }

/** Screen time split by category. */
data class CategorySplit(val productiveMs: Long = 0, val neutralMs: Long = 0, val distractingMs: Long = 0) {
    val totalMs: Long get() = productiveMs + neutralMs + distractingMs
}

object InsightRules {

    fun split(usage: Map<String, Long>, categoryOf: (String) -> AppCategory): CategorySplit {
        var productive = 0L
        var neutral = 0L
        var distracting = 0L
        for ((pkg, ms) in usage) {
            when (categoryOf(pkg)) {
                AppCategory.PRODUCTIVE -> productive += ms
                AppCategory.NEUTRAL -> neutral += ms
                AppCategory.DISTRACTING -> distracting += ms
            }
        }
        return CategorySplit(productive, neutral, distracting)
    }

    /** Sums focus time per local day (keyed by local midnight in epoch millis). */
    fun focusByDay(sessions: List<Pair<Long, Long>>, zone: ZoneId): Map<Long, Long> =
        sessions.groupBy({ (startedAt, _) -> DayBounds.of(startedAt, zone).startMs }, { it.second })
            .mapValues { (_, values) -> values.sum() }

    fun dayKey(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dateOf(dayKey: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(dayKey).atZone(zone).toLocalDate()
}

data class Streak(val current: Int, val best: Int)

object StreakRules {
    /**
     * Streaks over calendar days. Today extends the streak once achieved; while today is still
     * open, the current streak is the run ending yesterday. Days before [firstDay] don't exist.
     */
    fun streak(achieved: Set<LocalDate>, today: LocalDate, firstDay: LocalDate): Streak {
        if (firstDay.isAfter(today)) return Streak(0, 0)
        var current = 0
        var day = if (today in achieved) today else today.minusDays(1)
        while (!day.isBefore(firstDay) && day in achieved) {
            current++
            day = day.minusDays(1)
        }
        var best = 0
        var run = 0
        var d = firstDay
        while (!d.isAfter(today)) {
            run = if (d in achieved) run + 1 else 0
            best = maxOf(best, run)
            d = d.plusDays(1)
        }
        return Streak(current, best)
    }

    /** Days on which every limit was respected. Days without usage data count as not achieved. */
    fun limitDays(
        usageByDay: Map<LocalDate, Map<String, Long>>,
        limits: Map<String, Long>,
    ): Set<LocalDate> = if (limits.isEmpty()) {
        emptySet()
    } else {
        usageByDay.filter { (_, usage) -> limits.all { (pkg, limitMs) -> (usage[pkg] ?: 0L) < limitMs } }.keys
    }
}
