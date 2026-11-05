package app.arcify.core.block

import app.arcify.core.timer.ActiveTimer
import app.arcify.core.timer.TimerPhase

/** A strict focus phase in progress: only [allowedPackages] (plus always-allowed apps) may be used. */
data class FocusLock(val allowedPackages: Set<String>)

sealed interface BlockDecision {
    data object Allow : BlockDecision

    /** The home screen was opened during strict focus: go back to the focus screen instead. */
    data object ReturnToFocus : BlockDecision

    data class StrictFocus(val packageName: String) : BlockDecision

    data class LimitReached(val packageName: String, val usedMs: Long, val limitMs: Long) : BlockDecision
}

/** Decides what happens when an app comes to the foreground (spec F02/F03). Pure. */
object BlockPolicy {

    /** Strict sessions lock the phone only during focus phases; Pomodoro breaks are free. */
    fun focusLock(timer: ActiveTimer?, allowedPackages: Set<String>): FocusLock? =
        if (timer != null && timer.strict && timer.phase == TimerPhase.FOCUS) FocusLock(allowedPackages) else null

    /**
     * @param alwaysAllowed apps that are never blocked: Arcify itself, phone and emergency apps,
     *   keyboards, system UI.
     * @param launcherPackages home-screen apps.
     * @param limit the foreground app's limit, if it has one.
     * @param usedMs the foreground app's usage today.
     * @param userFacing whether the foreground package is an app with a launcher icon. Strict
     *   focus only blocks those: system screens without an icon (settings sub-pages, permission
     *   dialogs, OEM helpers) open from inside allowed apps and must keep working.
     */
    fun decide(
        foreground: String,
        alwaysAllowed: Set<String>,
        launcherPackages: Set<String>,
        focus: FocusLock?,
        limit: AppLimit?,
        usedMs: Long,
        userFacing: Boolean = true,
    ): BlockDecision {
        if (foreground in alwaysAllowed) return BlockDecision.Allow
        val isLauncher = foreground in launcherPackages
        if (focus != null) {
            if (isLauncher) return BlockDecision.ReturnToFocus
            if (userFacing && foreground !in focus.allowedPackages) return BlockDecision.StrictFocus(foreground)
        }
        if (isLauncher) return BlockDecision.Allow
        if (limit != null && LimitRules.status(limit, usedMs) == LimitStatus.REACHED) {
            return BlockDecision.LimitReached(foreground, usedMs, limit.dailyLimitMs)
        }
        return BlockDecision.Allow
    }
}
