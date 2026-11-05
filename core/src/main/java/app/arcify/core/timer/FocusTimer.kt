package app.arcify.core.timer

/**
 * Pure state machine for focus sessions (spec F01). Every function takes the current time and
 * returns new state, so the whole timer is testable without Android or real clocks.
 *
 * Rules:
 * - Strict sessions cannot be paused.
 * - Strict sessions can only be stopped early outside a focus phase (i.e. during a Pomodoro break).
 * - The stopwatch never runs in strict mode, since it has no natural end.
 * - Stopping a stopwatch, or a Pomodoro with at least one finished focus cycle, counts as completed.
 *   Stopping a countdown early does not.
 */
object FocusTimer {

    fun start(mode: TimerMode, now: Long, strict: Boolean = false, tagId: Long? = null): ActiveTimer =
        ActiveTimer(
            mode = mode,
            strict = strict && mode !is TimerMode.Stopwatch,
            tagId = tagId,
            sessionStartedAt = now,
            phase = TimerPhase.FOCUS,
            completedFocusCycles = 0,
            elapsedBeforeRunMs = 0,
            runningSince = now,
            focusedMsBeforePhase = 0,
            pausedBeforeMs = 0,
            pausedSince = null,
        )

    fun canPause(timer: ActiveTimer): Boolean = timer.isRunning && !timer.strict

    fun canStop(timer: ActiveTimer): Boolean = !timer.strict || timer.phase != TimerPhase.FOCUS

    fun pause(timer: ActiveTimer, now: Long): ActiveTimer {
        if (!canPause(timer)) return timer
        return timer.copy(
            elapsedBeforeRunMs = timer.phaseElapsedMs(now),
            runningSince = null,
            pausedSince = now,
        )
    }

    fun resume(timer: ActiveTimer, now: Long): ActiveTimer {
        if (timer.isRunning) return timer
        return timer.copy(
            runningSince = now,
            pausedBeforeMs = timer.pausedMs(now),
            pausedSince = null,
        )
    }

    /**
     * Moves the timer to [now], completing as many phases as have elapsed (the device may have
     * slept through several). Paused timers and stopwatches never complete on their own.
     */
    fun advance(timer: ActiveTimer, now: Long): Advance {
        if (!timer.isRunning) return Advance(timer, emptyList())
        val events = mutableListOf<TimerEvent>()
        var current = timer
        while (true) {
            val duration = current.phaseDurationMs() ?: return Advance(current, events)
            val elapsed = current.phaseElapsedMs(now)
            if (elapsed < duration) return Advance(current, events)

            val overflow = elapsed - duration
            val phaseEndedAt = now - overflow
            when (val mode = current.mode) {
                is TimerMode.Countdown -> {
                    events += TimerEvent.SessionFinished(summarize(current, phaseEndedAt, completed = true))
                    return Advance(null, events)
                }

                is TimerMode.Pomodoro -> {
                    val config = mode.config
                    if (current.phase == TimerPhase.FOCUS) {
                        val cycles = current.completedFocusCycles + 1
                        val focused = current.focusedMsBeforePhase + duration
                        val target = config.targetCycles
                        if (target != null && cycles >= target) {
                            events += TimerEvent.SessionFinished(
                                SessionSummary(
                                    modeKey = mode.key,
                                    tagId = current.tagId,
                                    startedAt = current.sessionStartedAt,
                                    endedAt = phaseEndedAt,
                                    focusedMs = focused,
                                    pausedMs = current.pausedMs(phaseEndedAt),
                                    completed = true,
                                    strict = current.strict,
                                    focusCycles = cycles,
                                ),
                            )
                            return Advance(null, events)
                        }
                        val next = if (cycles % config.cyclesBeforeLongBreak == 0) {
                            TimerPhase.LONG_BREAK
                        } else {
                            TimerPhase.SHORT_BREAK
                        }
                        events += TimerEvent.PhaseCompleted(TimerPhase.FOCUS, next, phaseEndedAt, cycles)
                        current = current.copy(
                            phase = next,
                            completedFocusCycles = cycles,
                            focusedMsBeforePhase = focused,
                            elapsedBeforeRunMs = overflow,
                            runningSince = now,
                        )
                    } else {
                        events += TimerEvent.PhaseCompleted(
                            current.phase,
                            TimerPhase.FOCUS,
                            phaseEndedAt,
                            current.completedFocusCycles,
                        )
                        current = current.copy(
                            phase = TimerPhase.FOCUS,
                            elapsedBeforeRunMs = overflow,
                            runningSince = now,
                        )
                    }
                }

                TimerMode.Stopwatch -> return Advance(current, events)
            }
        }
    }

    /**
     * Ends the session at [now]. Pending phase completions are applied first, so the returned
     * events always end with exactly one [TimerEvent.SessionFinished].
     */
    fun stop(timer: ActiveTimer, now: Long): List<TimerEvent> {
        val advanced = advance(timer, now)
        val remaining = advanced.timer ?: return advanced.events
        val completed = when (remaining.mode) {
            TimerMode.Stopwatch -> true
            is TimerMode.Pomodoro -> remaining.completedFocusCycles > 0
            is TimerMode.Countdown -> false
        }
        return advanced.events + TimerEvent.SessionFinished(summarize(remaining, now, completed))
    }

    private fun summarize(timer: ActiveTimer, endedAt: Long, completed: Boolean) = SessionSummary(
        modeKey = timer.mode.key,
        tagId = timer.tagId,
        startedAt = timer.sessionStartedAt,
        endedAt = endedAt,
        focusedMs = timer.focusedMs(endedAt),
        pausedMs = timer.pausedMs(endedAt),
        completed = completed,
        strict = timer.strict,
        focusCycles = timer.completedFocusCycles,
    )
}
