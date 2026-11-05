package app.arcify.timer

import android.content.Context
import androidx.annotation.StringRes
import app.arcify.R
import app.arcify.core.timer.ActiveTimer
import app.arcify.core.timer.TimerMode
import app.arcify.core.timer.TimerPhase

/** User-facing names for timer states, shared by the screen and the notification. */
object TimerText {
    @StringRes
    fun phaseLabel(timer: ActiveTimer): Int = when {
        timer.mode is TimerMode.Stopwatch -> R.string.phase_stopwatch
        timer.phase == TimerPhase.SHORT_BREAK -> R.string.phase_short_break
        timer.phase == TimerPhase.LONG_BREAK -> R.string.phase_long_break
        else -> R.string.phase_focus
    }

    fun phaseLabel(context: Context, timer: ActiveTimer): String = context.getString(phaseLabel(timer))

    /** Remaining time for timed phases, elapsed time for the stopwatch. */
    fun displayMs(timer: ActiveTimer, now: Long): Long = timer.remainingMs(now) ?: timer.phaseElapsedMs(now)
}
