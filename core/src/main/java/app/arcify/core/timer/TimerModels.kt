package app.arcify.core.timer

const val MINUTE_MS: Long = 60_000L

enum class TimerPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

/** Pomodoro settings. Every duration is at least one minute (spec F01). */
data class PomodoroConfig(
    val focusMs: Long = 25 * MINUTE_MS,
    val shortBreakMs: Long = 5 * MINUTE_MS,
    val longBreakMs: Long = 15 * MINUTE_MS,
    val cyclesBeforeLongBreak: Int = 4,
    /** Finish the session automatically after this many focus cycles; null = run until stopped. */
    val targetCycles: Int? = null,
) {
    init {
        require(focusMs >= MINUTE_MS && shortBreakMs >= MINUTE_MS && longBreakMs >= MINUTE_MS) {
            "Pomodoro durations must be at least one minute"
        }
        require(cyclesBeforeLongBreak >= 1) { "cyclesBeforeLongBreak must be >= 1" }
        require(targetCycles == null || targetCycles >= 1) { "targetCycles must be >= 1" }
    }

    fun durationOf(phase: TimerPhase): Long = when (phase) {
        TimerPhase.FOCUS -> focusMs
        TimerPhase.SHORT_BREAK -> shortBreakMs
        TimerPhase.LONG_BREAK -> longBreakMs
    }
}

sealed interface TimerMode {
    /** Stable identifier stored with recorded sessions. */
    val key: String

    data class Countdown(val durationMs: Long) : TimerMode {
        init {
            require(durationMs >= MINUTE_MS) { "Countdown must be at least one minute" }
        }

        override val key: String get() = "countdown"
    }

    data object Stopwatch : TimerMode {
        override val key: String = "stopwatch"
    }

    data class Pomodoro(val config: PomodoroConfig = PomodoroConfig()) : TimerMode {
        override val key: String get() = "pomodoro"
    }
}

/**
 * A running or paused focus session.
 *
 * Time is derived from recorded timestamps (start, pause, resume), never by counting ticks,
 * so a late or missed tick cannot drift the clock.
 */
data class ActiveTimer(
    val mode: TimerMode,
    val strict: Boolean,
    val tagId: Long?,
    val sessionStartedAt: Long,
    val phase: TimerPhase,
    val completedFocusCycles: Int,
    /** Time spent in the current phase before [runningSince] (the whole phase time when paused). */
    val elapsedBeforeRunMs: Long,
    /** When the current running stretch began; null while paused. */
    val runningSince: Long?,
    /** Focus time from focus phases that already ended. */
    val focusedMsBeforePhase: Long,
    /** Paused time from pauses that already ended. */
    val pausedBeforeMs: Long,
    /** When the current pause began; null while running. */
    val pausedSince: Long?,
) {
    val isRunning: Boolean get() = runningSince != null

    fun phaseElapsedMs(now: Long): Long =
        elapsedBeforeRunMs + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0L)

    /** Planned length of the current phase, or null for the stopwatch. */
    fun phaseDurationMs(): Long? = when (mode) {
        is TimerMode.Countdown -> mode.durationMs
        TimerMode.Stopwatch -> null
        is TimerMode.Pomodoro -> mode.config.durationOf(phase)
    }

    fun remainingMs(now: Long): Long? =
        phaseDurationMs()?.let { (it - phaseElapsedMs(now)).coerceAtLeast(0) }

    fun focusedMs(now: Long): Long {
        if (phase != TimerPhase.FOCUS) return focusedMsBeforePhase
        val elapsed = phaseElapsedMs(now)
        val capped = phaseDurationMs()?.let { minOf(elapsed, it) } ?: elapsed
        return focusedMsBeforePhase + capped
    }

    fun pausedMs(now: Long): Long =
        pausedBeforeMs + (pausedSince?.let { (now - it).coerceAtLeast(0) } ?: 0L)
}

data class SessionSummary(
    val modeKey: String,
    val tagId: Long?,
    val startedAt: Long,
    val endedAt: Long,
    val focusedMs: Long,
    val pausedMs: Long,
    val completed: Boolean,
    val strict: Boolean,
    val focusCycles: Int,
)

sealed interface TimerEvent {
    /** A Pomodoro phase ended at [at] and [next] began. */
    data class PhaseCompleted(
        val completed: TimerPhase,
        val next: TimerPhase,
        val at: Long,
        val completedFocusCycles: Int,
    ) : TimerEvent

    data class SessionFinished(val summary: SessionSummary) : TimerEvent
}

/** Result of moving a timer forward to "now": the new timer (null when the session ended) and what happened. */
data class Advance(val timer: ActiveTimer?, val events: List<TimerEvent>)
