package app.arcify.core.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimerTest {

    private val min = MINUTE_MS

    private fun Advance.finished(): SessionSummary =
        (events.last() as TimerEvent.SessionFinished).summary

    @Test
    fun countdownReportsRemainingAndFinishesAtItsDuration() {
        val timer = FocusTimer.start(TimerMode.Countdown(25 * min), now = 0)

        val midway = FocusTimer.advance(timer, 10 * min)
        assertEquals(15 * min, midway.timer!!.remainingMs(10 * min))
        assertTrue(midway.events.isEmpty())

        val end = FocusTimer.advance(timer, 26 * min)
        assertNull(end.timer)
        val summary = end.finished()
        assertEquals(25 * min, summary.focusedMs)
        assertEquals(25 * min, summary.endedAt)
        assertTrue(summary.completed)
    }

    @Test
    fun pausedTimeIsNotCountedAsFocus() {
        var timer = FocusTimer.start(TimerMode.Countdown(10 * min), now = 0)
        timer = FocusTimer.pause(timer, 2 * min)
        assertEquals(8 * min, timer.remainingMs(5 * min))

        timer = FocusTimer.resume(timer, 7 * min)
        assertEquals(5 * min, timer.pausedMs(7 * min))

        // 2 min before the pause + 8 min after resuming at minute 7 = done at minute 15.
        val summary = FocusTimer.advance(timer, 15 * min).finished()
        assertEquals(15 * min, summary.endedAt)
        assertEquals(10 * min, summary.focusedMs)
        assertEquals(5 * min, summary.pausedMs)
    }

    @Test
    fun pausedTimerDoesNotFinish() {
        val paused = FocusTimer.pause(FocusTimer.start(TimerMode.Countdown(min), 0), 30_000)
        val later = FocusTimer.advance(paused, 10 * min)
        assertSame(paused, later.timer)
        assertTrue(later.events.isEmpty())
    }

    @Test
    fun strictSessionsCannotBePausedOrStoppedDuringFocus() {
        val timer = FocusTimer.start(TimerMode.Countdown(25 * min), 0, strict = true)
        assertFalse(FocusTimer.canPause(timer))
        assertSame(timer, FocusTimer.pause(timer, min))
        assertFalse(FocusTimer.canStop(timer))
    }

    @Test
    fun strictPomodoroCanBeStoppedDuringABreak() {
        val config = PomodoroConfig(focusMs = 25 * min, shortBreakMs = 5 * min)
        val timer = FocusTimer.start(TimerMode.Pomodoro(config), 0, strict = true)
        val inBreak = FocusTimer.advance(timer, 26 * min).timer!!
        assertEquals(TimerPhase.SHORT_BREAK, inBreak.phase)
        assertTrue(FocusTimer.canStop(inBreak))
    }

    @Test
    fun stopwatchIgnoresStrictMode() {
        assertFalse(FocusTimer.start(TimerMode.Stopwatch, 0, strict = true).strict)
    }

    @Test
    fun pomodoroAlternatesPhasesAndTakesALongBreakEveryNCycles() {
        val config = PomodoroConfig(
            focusMs = 25 * min,
            shortBreakMs = 5 * min,
            longBreakMs = 15 * min,
            cyclesBeforeLongBreak = 2,
        )
        val start = FocusTimer.start(TimerMode.Pomodoro(config), 0)

        val first = FocusTimer.advance(start, 25 * min)
        assertEquals(TimerPhase.SHORT_BREAK, first.timer!!.phase)
        assertEquals(
            listOf(TimerEvent.PhaseCompleted(TimerPhase.FOCUS, TimerPhase.SHORT_BREAK, 25 * min, 1)),
            first.events,
        )

        val second = FocusTimer.advance(first.timer!!, 30 * min)
        assertEquals(TimerPhase.FOCUS, second.timer!!.phase)

        val third = FocusTimer.advance(second.timer!!, 55 * min)
        val timer = third.timer!!
        assertEquals(TimerPhase.LONG_BREAK, timer.phase)
        assertEquals(2, timer.completedFocusCycles)
        assertEquals(50 * min, timer.focusedMs(55 * min))
    }

    @Test
    fun pomodoroCatchesUpOnSeveralPhasesAfterALongGap() {
        val start = FocusTimer.start(TimerMode.Pomodoro(), 0)
        // 25 min focus + 5 min break, then 90 s into the second focus phase.
        val now = 31 * min + 30_000
        val result = FocusTimer.advance(start, now)
        val timer = result.timer!!
        assertEquals(2, result.events.size)
        assertEquals(TimerPhase.FOCUS, timer.phase)
        assertEquals(90_000L, timer.phaseElapsedMs(now))
        assertEquals(25 * min + 90_000, timer.focusedMs(now))
    }

    @Test
    fun pomodoroWithTargetCyclesFinishesItself() {
        val config = PomodoroConfig(targetCycles = 1)
        val result = FocusTimer.advance(FocusTimer.start(TimerMode.Pomodoro(config), 0), 26 * min)
        assertNull(result.timer)
        val summary = result.finished()
        assertEquals(25 * min, summary.focusedMs)
        assertEquals(1, summary.focusCycles)
        assertTrue(summary.completed)
    }

    @Test
    fun stoppingACountdownEarlyIsNotCompleted() {
        val events = FocusTimer.stop(FocusTimer.start(TimerMode.Countdown(25 * min), 0), 10 * min)
        val summary = (events.single() as TimerEvent.SessionFinished).summary
        assertFalse(summary.completed)
        assertEquals(10 * min, summary.focusedMs)
    }

    @Test
    fun stoppingAStopwatchIsCompleted() {
        val events = FocusTimer.stop(FocusTimer.start(TimerMode.Stopwatch, 0), 42 * min)
        val summary = (events.single() as TimerEvent.SessionFinished).summary
        assertTrue(summary.completed)
        assertEquals(42 * min, summary.focusedMs)
    }

    @Test
    fun stoppingAfterTheEndReportsTheNaturalFinishOnly() {
        val events = FocusTimer.stop(FocusTimer.start(TimerMode.Countdown(5 * min), 0), 9 * min)
        val summary = (events.single() as TimerEvent.SessionFinished).summary
        assertTrue(summary.completed)
        assertEquals(5 * min, summary.endedAt)
    }

    @Test
    fun invalidDurationsAreRejected() {
        val error = runCatching { TimerMode.Countdown(30_000) }.exceptionOrNull()
        assertNotNull(error)
    }
}
