package app.arcify.core.protection

import app.arcify.core.block.AppLimit
import app.arcify.core.content.Platform
import app.arcify.core.content.ShortFormMode
import app.arcify.core.content.ShortFormRule
import app.arcify.core.content.WebsiteRule
import app.arcify.core.content.WebsiteRuleKind
import app.arcify.core.timer.FocusTimer
import app.arcify.core.timer.MINUTE_MS
import app.arcify.core.timer.TimerEvent
import app.arcify.core.timer.TimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionTest {

    private val none = Commitments(strictSession = false, lockedRules = 0)
    private val committed = Commitments(strictSession = true, lockedRules = 0)

    @Test
    fun levelsActivateAsDescribed() {
        assertFalse(ProtectionRules.isActive(ProtectionLevel.OFF, committed))
        assertFalse(ProtectionRules.isActive(ProtectionLevel.DURING_COMMITMENTS, none))
        assertTrue(ProtectionRules.isActive(ProtectionLevel.DURING_COMMITMENTS, committed))
        assertTrue(ProtectionRules.isActive(ProtectionLevel.ALWAYS, none))
    }

    @Test
    fun protectionCanOnlyBeLoweredWithoutCommitments() {
        assertTrue(ProtectionRules.canChangeLevel(ProtectionLevel.OFF, ProtectionLevel.ALWAYS, committed))
        assertFalse(ProtectionRules.canChangeLevel(ProtectionLevel.ALWAYS, ProtectionLevel.OFF, committed))
        assertTrue(ProtectionRules.canChangeLevel(ProtectionLevel.ALWAYS, ProtectionLevel.OFF, none))
        assertFalse(ProtectionRules.canWeaken(Commitments(strictSession = false, lockedRules = 1)))
    }

    @Test
    fun commitmentsCountStrictSessionsAndLockedRules() {
        val now = 1_000L
        val strict = FocusTimer.start(TimerMode.Pomodoro(), 0, strict = true)
        val inBreak = FocusTimer.advance(strict, 26 * MINUTE_MS).timer!!
        // A strict session commits for its whole length, breaks included.
        assertTrue(ProtectionRules.commitments(inBreak, emptyList(), emptyList(), emptyList(), now).strictSession)

        val result = ProtectionRules.commitments(
            timer = null,
            limits = listOf(AppLimit("a", MINUTE_MS, strictUntil = 2_000), AppLimit("b", MINUTE_MS, strictUntil = 500)),
            shortFormRules = listOf(ShortFormRule(Platform.YOUTUBE, mode = ShortFormMode.BLOCK, strictUntil = 5_000)),
            websiteRules = listOf(WebsiteRule("x.com", WebsiteRuleKind.BLOCK, strictUntil = 3_000)),
            now = now,
        )
        assertEquals(Commitments(strictSession = false, lockedRules = 3), result)
    }

    @Test
    fun emergencyCodesAreLongRandomAndMixed() {
        val codes = (1..200).map { HardExit.newCode() }
        assertTrue(codes.all { it.length == HardExit.CODE_LENGTH && it.length >= 20 })
        assertTrue(codes.all { code -> code.any(Char::isUpperCase) && code.any(Char::isLowerCase) && code.any(Char::isDigit) })
        assertTrue(codes.none { code -> code.any { it in "0Oo1lI" } })
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun emergencyCodeMustMatchExactlyIncludingCase() {
        val code = HardExit.newCode()
        assertTrue(HardExit.matches("  $code ", code))
        assertFalse(HardExit.matches(code.lowercase(), code))
        assertFalse(HardExit.matches(code.dropLast(1), code))
        assertFalse(HardExit.matches("", code))
    }

    @Test
    fun abandonEndsEvenAStrictSessionAsNotCompleted() {
        val strict = FocusTimer.start(TimerMode.Countdown(25 * MINUTE_MS), 0, strict = true)
        assertFalse(FocusTimer.canStop(strict))
        val summary = (FocusTimer.abandon(strict, 10 * MINUTE_MS).single() as TimerEvent.SessionFinished).summary
        assertFalse(summary.completed)
        assertEquals(10 * MINUTE_MS, summary.focusedMs)
    }

    @Test
    fun homeRoutesToFocusLauncherOrChooser() {
        val strictFocus = FocusTimer.start(TimerMode.Countdown(25 * MINUTE_MS), 0, strict = true)
        val relaxed = FocusTimer.start(TimerMode.Countdown(25 * MINUTE_MS), 0, strict = false)
        val launchers = setOf("com.example.launcher")
        assertEquals(HomeRoute.Focus, HomeRouting.route(strictFocus, "com.example.launcher", launchers))
        assertEquals(HomeRoute.Launcher("com.example.launcher"), HomeRouting.route(relaxed, "com.example.launcher", launchers))
        assertEquals(HomeRoute.Chooser, HomeRouting.route(null, null, launchers))
        // A launcher that was uninstalled can't strand the user.
        assertEquals(HomeRoute.Chooser, HomeRouting.route(null, "com.gone.launcher", launchers))
    }
}
