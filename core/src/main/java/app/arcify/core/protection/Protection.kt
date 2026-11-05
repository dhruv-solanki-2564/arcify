package app.arcify.core.protection

import app.arcify.core.block.AppLimit
import app.arcify.core.block.BlockPolicy
import app.arcify.core.block.LimitRules
import app.arcify.core.content.AdultFilterSettings
import app.arcify.core.content.ShortFormPolicy
import app.arcify.core.content.ShortFormRule
import app.arcify.core.content.WebsitePolicy
import app.arcify.core.content.WebsiteRule
import app.arcify.core.timer.ActiveTimer
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

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
        adultFilter: AdultFilterSettings? = null,
    ): Commitments = Commitments(
        strictSession = timer?.strict == true,
        lockedRules = limits.count { LimitRules.isStrict(it, now) } +
            shortFormRules.count { ShortFormPolicy.isStrict(it, now) } +
            websiteRules.count { WebsitePolicy.isStrict(it, now) } +
            (if (adultFilter?.isStrict(now) == true) 1 else 0),
    )
}

/**
 * The optional emergency exit for strict sessions (spec F02): the user must type a fresh random
 * code to end early. A new code each time means it can't be memorised or kept in a note, so
 * breaking a session always takes deliberate effort.
 */
object HardExit {
    const val CODE_LENGTH = 24

    // Upper case, lower case and digits, without look-alikes (0/O/o, 1/l/I) so the code is fair to type.
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijkmnpqrstuvwxyz"
    private const val DIGITS = "23456789"
    private const val ALPHABET = UPPER + LOWER + DIGITS

    /** A random code of [length] characters with at least one capital, one small letter and one digit. */
    fun newCode(length: Int = CODE_LENGTH, random: Random = SecureRandom().asKotlinRandom()): String {
        require(length >= 3) { "length must be at least 3" }
        while (true) {
            val code = String(CharArray(length) { ALPHABET[random.nextInt(ALPHABET.length)] })
            if (code.any { it in UPPER } && code.any { it in LOWER } && code.any { it in DIGITS }) return code
        }
    }

    /** Exact match, case-sensitive. Only surrounding spaces are ignored. */
    fun matches(input: String, code: String): Boolean = input.trim() == code
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
