package app.arcify.core.timer

import app.arcify.core.util.formatDuration
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRulesTest {

    private fun summary(focusedMs: Long) = SessionSummary(
        modeKey = "countdown",
        tagId = null,
        startedAt = 0,
        endedAt = focusedMs,
        focusedMs = focusedMs,
        pausedMs = 0,
        completed = true,
        strict = false,
        focusCycles = 0,
    )

    @Test
    fun sessionsUnderAMinuteAreNotRecorded() {
        assertFalse(SessionRules.shouldRecord(summary(59_999)))
        assertTrue(SessionRules.shouldRecord(summary(MINUTE_MS)))
    }

    @Test
    fun sessionsPushingTheDayPastTheCapAreOutliers() {
        val fifteenHours = 15 * 60 * MINUTE_MS
        assertFalse(SessionRules.isOutlier(fifteenHours, summary(60 * MINUTE_MS)))
        assertTrue(SessionRules.isOutlier(fifteenHours, summary(61 * MINUTE_MS)))
    }

    @Test
    fun dayBoundsFollowTheLocalZone() {
        val zone = ZoneId.of("Asia/Kolkata")
        val tenAm = LocalDateTime.of(2026, 11, 5, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val range = DayBounds.of(tenAm, zone)
        val midnight = LocalDateTime.of(2026, 11, 5, 0, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(midnight, range.startMs)
        assertEquals(midnight + 24 * 60 * MINUTE_MS, range.endMs)
    }

    @Test
    fun durationsFormatAsClockText() {
        assertEquals("00:00", formatDuration(-5))
        assertEquals("00:59", formatDuration(59_999))
        assertEquals("25:00", formatDuration(25 * MINUTE_MS))
        assertEquals("1:02:03", formatDuration((3600 + 120 + 3) * 1000L))
    }
}
