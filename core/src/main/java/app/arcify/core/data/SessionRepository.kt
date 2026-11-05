package app.arcify.core.data

import app.arcify.core.timer.DayBounds
import app.arcify.core.timer.SessionRules
import app.arcify.core.timer.SessionSummary
import app.arcify.core.timer.WallClock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SessionRepository @Inject constructor(
    private val dao: FocusSessionDao,
    private val clock: WallClock,
) {
    /** Saves a finished session if it passes [SessionRules]. Returns whether it was saved. */
    suspend fun record(summary: SessionSummary): Boolean {
        if (!SessionRules.shouldRecord(summary)) return false
        val day = DayBounds.of(summary.startedAt, ZoneId.systemDefault())
        val earlierToday = dao.focusedBetween(day.startMs, day.endMs)
        dao.insert(
            FocusSessionEntity(
                mode = summary.modeKey,
                tagId = summary.tagId,
                startedAt = summary.startedAt,
                endedAt = summary.endedAt,
                focusedMs = summary.focusedMs,
                pausedMs = summary.pausedMs,
                completed = summary.completed,
                strict = summary.strict,
                focusCycles = summary.focusCycles,
                flaggedOutlier = SessionRules.isOutlier(earlierToday, summary),
            ),
        )
        return true
    }

    /** Focus time recorded today, excluding outliers. The day is fixed when this is called. */
    fun observeFocusedToday(): Flow<Long> {
        val day = DayBounds.of(clock.nowMs(), ZoneId.systemDefault())
        return dao.observeFocusedBetween(day.startMs, day.endMs)
    }
}
