package app.arcify.core.block

import app.arcify.core.timer.FocusTimer
import app.arcify.core.timer.MINUTE_MS
import app.arcify.core.timer.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlockPolicyTest {

    private val own = "app.arcify"
    private val launcher = "com.example.launcher"
    private val social = "com.example.social"
    private val notes = "com.example.notes"
    private val limit = AppLimit(social, dailyLimitMs = 30 * MINUTE_MS)

    private fun decide(
        foreground: String,
        focus: FocusLock? = null,
        limit: AppLimit? = null,
        usedMs: Long = 0,
    ) = BlockPolicy.decide(
        foreground = foreground,
        alwaysAllowed = setOf(own, "com.android.systemui"),
        launcherPackages = setOf(launcher),
        focus = focus,
        limit = limit,
        usedMs = usedMs,
    )

    @Test
    fun appsUnderTheirLimitAreAllowed() {
        assertEquals(BlockDecision.Allow, decide(social, limit = limit, usedMs = 29 * MINUTE_MS))
    }

    @Test
    fun appsOverTheirLimitAreBlocked() {
        assertEquals(
            BlockDecision.LimitReached(social, 31 * MINUTE_MS, 30 * MINUTE_MS),
            decide(social, limit = limit, usedMs = 31 * MINUTE_MS),
        )
    }

    @Test
    fun strictFocusBlocksAppsThatAreNotAllowed() {
        val focus = FocusLock(allowedPackages = setOf(notes))
        assertEquals(BlockDecision.StrictFocus(social), decide(social, focus))
        assertEquals(BlockDecision.Allow, decide(notes, focus))
        assertEquals(BlockDecision.Allow, decide(own, focus))
    }

    @Test
    fun systemScreensWithoutALauncherIconAreNeverBlockedByStrictFocus() {
        val decision = BlockPolicy.decide(
            foreground = "com.oplus.battery",
            alwaysAllowed = setOf(own),
            launcherPackages = setOf(launcher),
            focus = FocusLock(allowedPackages = setOf("com.android.settings")),
            limit = null,
            usedMs = 0,
            userFacing = false,
        )
        assertEquals(BlockDecision.Allow, decision)
    }

    @Test
    fun homeScreenDuringStrictFocusReturnsToFocus() {
        assertEquals(BlockDecision.ReturnToFocus, decide(launcher, FocusLock(emptySet())))
        assertEquals(BlockDecision.Allow, decide(launcher))
    }

    @Test
    fun allowedAppsStillRespectTheirLimits() {
        val focus = FocusLock(allowedPackages = setOf(social))
        assertEquals(
            BlockDecision.LimitReached(social, 40 * MINUTE_MS, 30 * MINUTE_MS),
            decide(social, focus, limit, usedMs = 40 * MINUTE_MS),
        )
    }

    @Test
    fun onlyStrictFocusPhasesLockThePhone() {
        val relaxed = FocusTimer.start(TimerMode.Countdown(25 * MINUTE_MS), 0, strict = false)
        assertNull(BlockPolicy.focusLock(relaxed, emptySet()))
        assertNull(BlockPolicy.focusLock(null, emptySet()))

        val strict = FocusTimer.start(TimerMode.Pomodoro(), 0, strict = true)
        assertNotNull(BlockPolicy.focusLock(strict, emptySet()))
        val inBreak = FocusTimer.advance(strict, 26 * MINUTE_MS).timer!!
        assertNull(BlockPolicy.focusLock(inBreak, emptySet()))
    }
}
