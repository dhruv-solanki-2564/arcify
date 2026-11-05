package app.arcify.core.timer

import java.time.Instant
import java.time.ZoneId

/** Recording rules for finished sessions (spec F01). */
object SessionRules {
    /** Sessions with less focus than this are not saved. */
    const val MIN_RECORDED_FOCUS_MS: Long = MINUTE_MS

    /** Focus beyond this per local day is treated as an outlier (e.g. a timer left running). */
    const val DAILY_FOCUS_CAP_MS: Long = 16 * 60 * MINUTE_MS

    fun shouldRecord(summary: SessionSummary): Boolean = summary.focusedMs >= MIN_RECORDED_FOCUS_MS

    fun isOutlier(focusedEarlierTodayMs: Long, summary: SessionSummary): Boolean =
        focusedEarlierTodayMs + summary.focusedMs > DAILY_FOCUS_CAP_MS
}

/** Start (inclusive) and end (exclusive) of the local day containing an instant, in epoch millis. */
data class DayRange(val startMs: Long, val endMs: Long)

object DayBounds {
    fun of(epochMs: Long, zone: ZoneId): DayRange {
        val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return DayRange(start, end)
    }
}
