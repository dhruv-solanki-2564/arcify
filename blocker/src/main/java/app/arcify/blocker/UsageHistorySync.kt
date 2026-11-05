package app.arcify.blocker

import app.arcify.core.data.InsightsRepository
import app.arcify.core.insights.InsightRules
import app.arcify.core.timer.WallClock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies recent usage from Android into Arcify's own history (spec F08). Android keeps
 * detailed events for only a few days, so they're saved here. Today and yesterday are always
 * refreshed. Older days are only filled in if missing, so a partly expired day never overwrites
 * a complete stored one.
 */
@Singleton
class UsageHistorySync @Inject constructor(
    private val reader: UsageReader,
    private val insights: InsightsRepository,
    private val clock: WallClock,
) {
    suspend fun sync(days: Int = 7) = withContext(Dispatchers.IO) {
        if (!reader.hasAccess()) return@withContext
        val zone = ZoneId.systemDefault()
        val now = clock.nowMs()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (offset in 0 until days.coerceAtLeast(1)) {
            val date = today.minusDays(offset.toLong())
            val start = InsightRules.dayKey(date, zone)
            val alwaysRefresh = offset <= 1
            if (!alwaysRefresh && insights.hasDay(start)) continue
            val end = minOf(InsightRules.dayKey(date.plusDays(1), zone), now)
            val usage = reader.usage(start, end)
            if (!alwaysRefresh && usage.apps.isEmpty()) continue
            insights.saveDay(start, usage.apps, usage.unlocks)
        }
    }
}
