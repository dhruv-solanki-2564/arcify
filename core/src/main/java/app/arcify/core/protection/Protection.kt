package app.arcify.core.protection

import app.arcify.core.block.AppLimit
import app.arcify.core.block.BlockPolicy
import app.arcify.core.block.LimitRules
import app.arcify.core.content.ShortFormPolicy
import app.arcify.core.content.ShortFormRule
import app.arcify.core.content.WebsitePolicy
import app.arcify.core.content.WebsiteRule
import app.arcify.core.timer.ActiveTimer

/** How hard Arcify defends itself against being switched off or uninstalled (spec F07). */
enum class ProtectionLevel { OFF, DURING_COMMITMENTS, ALWAYS }

/** What the user is currently committed to. Protection and its settings follow this. */
data class Commitments(val strictSession: Boolean, val lockedRules: Int) {
    val any: Boolean get() = strictSession || lockedRules > 0
}

data class ProtectionSettings(
    val level: ProtectionLevel = ProtectionRules.DEFAULT_LEVEL,
    val hardExitEnabled: Boolean = false,
    /** Optional home-screen hiding via Arcify as the home app (spec F02). */
    val homeHidingEnabled: Boolean = false,
    /** The launcher Arcify forwards Home to outside strict focus. */
    val homeLauncher: String? = null,
)

object ProtectionRules {
    val DEFAULT_LEVEL = ProtectionLevel.DURING_COMMITMENTS

    fun isActive(level: ProtectionLevel, commitments: Commitments): Boolean = when (level) {
        ProtectionLevel.OFF -> false
        ProtectionLevel.DURING_COMMITMENTS -> commitments.any
        ProtectionLevel.ALWAYS -> true
    }

    /** Protection can always be raised, but only lowered when nothing is committed. */
    fun canChangeLevel(current: ProtectionLevel, updated: ProtectionLevel, commitments: Commitments): Boolean =
        updated.ordinal >= current.ordinal || !commitments.any

    /** Anything that weakens protection (emergency exit on, home hiding off, device admin off) waits for commitments to end. */
    fun canWeaken(commitments: Commitments): Boolean = !commitments.any

    /**
     * A strict session counts for its whole length, breaks included, so a Pomodoro break can't be
     * used to switch protection off. Locked rules count until their lock ends.
     */
    fun commitments(
        timer: ActiveTimer?,
        limits: List<AppLimit>,
        shortFormRules: List<ShortFormRule>,
        websiteRules: List<WebsiteRule>,
        now: Long,
    ): Commitments = Commitments(
        strictSession = timer?.strict == true,
        lockedRules = limits.count { LimitRules.isStrict(it, now) } +
            shortFormRules.count { ShortFormPolicy.isStrict(it, now) } +
            websiteRules.count { WebsitePolicy.isStrict(it, now) },
    )
}

/** The optional emergency exit for strict sessions: type a phrase to end early (spec F02). */
object HardExit {
    const val PHRASE = "I am choosing to end my focus session early"

    /** Case and extra spaces don't matter, and a final full stop is optional. Everything else must match. */
    fun matches(input: String, phrase: String = PHRASE): Boolean = normalize(input) == normalize(phrase)

    private fun normalize(text: String): String =
        text.trim().trimEnd('.').trim().lowercase().replace(Regex("\\s+"), " ")
}

sealed interface HomeRoute {
    /** Strict focus is on: Home shows the focus screen. */
    data object Focus : HomeRoute

    data class Launcher(val packageName: String) : HomeRoute

    /** No usable launcher chosen yet: let the user pick one, so Home always leads somewhere. */
    data object Chooser : HomeRoute
}

object HomeRouting {
    /** Where Home goes when Arcify is the home app. Breaks in a strict Pomodoro use the real launcher. */
    fun route(timer: ActiveTimer?, chosenLauncher: String?, installedLaunchers: Set<String>): HomeRoute = when {
        BlockPolicy.focusLock(timer, emptySet()) != null -> HomeRoute.Focus
        chosenLauncher != null && chosenLauncher in installedLaunchers -> HomeRoute.Launcher(chosenLauncher)
        else -> HomeRoute.Chooser
    }
}
