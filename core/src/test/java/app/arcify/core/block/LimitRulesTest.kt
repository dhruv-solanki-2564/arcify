package app.arcify.core.block

import app.arcify.core.timer.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitRulesTest {

    private val limit = AppLimit("com.example.social", dailyLimitMs = 30 * MINUTE_MS)

    @Test
    fun statusMovesFromUnderToReminderToReached() {
        assertEquals(LimitStatus.UNDER, LimitRules.status(limit, 23 * MINUTE_MS))
        assertEquals(LimitStatus.REMINDER, LimitRules.status(limit, 24 * MINUTE_MS)) // 80 %
        assertEquals(LimitStatus.REACHED, LimitRules.status(limit, 30 * MINUTE_MS))
    }

    @Test
    fun disabledLimitsNeverTrigger() {
        assertEquals(LimitStatus.UNDER, LimitRules.status(limit.copy(enabled = false), 999 * MINUTE_MS))
    }

    @Test
    fun noReminderWhenReminderIsOff() {
        assertEquals(LimitStatus.UNDER, LimitRules.status(limit.copy(reminderPercent = null), 29 * MINUTE_MS))
    }

    @Test
    fun unlockedLimitsCanChangeFreely() {
        assertTrue(LimitRules.canChange(limit, limit.copy(dailyLimitMs = 60 * MINUTE_MS), now = 0))
        assertTrue(LimitRules.canChange(limit, null, now = 0))
    }

    @Test
    fun strictLimitsCanOnlyGetTighter() {
        val locked = limit.copy(strictUntil = 1_000)
        assertFalse(LimitRules.canChange(locked, null, now = 500))
        assertFalse(LimitRules.canChange(locked, locked.copy(enabled = false), now = 500))
        assertFalse(LimitRules.canChange(locked, locked.copy(dailyLimitMs = 45 * MINUTE_MS), now = 500))
        assertFalse(LimitRules.canChange(locked, locked.copy(strictUntil = 900), now = 500))
        assertTrue(LimitRules.canChange(locked, locked.copy(dailyLimitMs = 15 * MINUTE_MS), now = 500))
        assertTrue(LimitRules.canChange(locked, locked.copy(strictUntil = 2_000), now = 500))
    }

    @Test
    fun expiredLocksNoLongerApply() {
        val locked = limit.copy(strictUntil = 1_000)
        assertFalse(LimitRules.isStrict(locked, now = 1_000))
        assertTrue(LimitRules.canChange(locked, null, now = 1_000))
    }
}
