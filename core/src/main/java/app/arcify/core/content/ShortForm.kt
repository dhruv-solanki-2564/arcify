package app.arcify.core.content

/** Apps with short-form video feeds (spec F04). */
enum class Platform { INSTAGRAM, YOUTUBE, SNAPCHAT, FACEBOOK }

/** Where in the Instagram app a reel was opened (Arcify-only feature, spec F04). */
enum class IgSurface { REELS_TAB, FEED, EXPLORE, DM, UNKNOWN }

enum class ShortFormMode { ALLOW, BLOCK, LIMIT }

/**
 * What to do with short-form videos on a platform, or on one Instagram surface when
 * [surface] is set. Surface rules override the platform-wide rule.
 */
data class ShortFormRule(
    val platform: Platform,
    val surface: IgSurface? = null,
    val mode: ShortFormMode,
    /** For [ShortFormMode.LIMIT]: items allowed per day before blocking. */
    val dailyLimit: Int? = null,
    /** While in the future, the rule can only get stricter. */
    val strictUntil: Long? = null,
)

/** One short-form item seen on screen. [viaPackage] is the app or browser it was seen in. */
data class ShortFormSighting(
    val platform: Platform,
    val viaPackage: String,
    val surface: IgSurface?,
    val itemKey: String,
    val atMs: Long,
)

sealed interface ShortFormDecision {
    /** Allowed. A new item counts toward today's total. */
    data object Allow : ShortFormDecision

    data class Blocked(val platform: Platform, val surface: IgSurface?) : ShortFormDecision

    data class LimitReached(
        val platform: Platform,
        val surface: IgSurface?,
        val watched: Int,
        val limit: Int,
    ) : ShortFormDecision
}

/** Today's watched counts, as needed by [ShortFormPolicy]. A null surface means all surfaces. */
fun interface ShortFormCounts {
    fun watched(platform: Platform, surface: IgSurface?): Int
}

object ShortFormPolicy {

    /**
     * The rule that governs a sighting. An Instagram surface rule wins over the Instagram-wide
     * rule. When the surface can't be determined, the Instagram-wide rule applies.
     */
    fun ruleFor(platform: Platform, surface: IgSurface?, rules: List<ShortFormRule>): ShortFormRule? {
        val forPlatform = rules.filter { it.platform == platform }
        val general = forPlatform.firstOrNull { it.surface == null }
        // An unknown surface (e.g. a reel in a browser) follows the Instagram-wide rule, so only
        // what the user explicitly blocked gets blocked.
        if (platform != Platform.INSTAGRAM || surface == null || surface == IgSurface.UNKNOWN) return general
        return forPlatform.firstOrNull { it.surface == surface } ?: general
    }

    fun decide(sighting: ShortFormSighting, rules: List<ShortFormRule>, counts: ShortFormCounts): ShortFormDecision {
        val rule = ruleFor(sighting.platform, sighting.surface, rules) ?: return ShortFormDecision.Allow
        return when (rule.mode) {
            ShortFormMode.ALLOW -> ShortFormDecision.Allow
            ShortFormMode.BLOCK -> ShortFormDecision.Blocked(sighting.platform, sighting.surface)
            ShortFormMode.LIMIT -> {
                val limit = rule.dailyLimit ?: 0
                val watched = counts.watched(rule.platform, rule.surface)
                if (watched >= limit) {
                    ShortFormDecision.LimitReached(sighting.platform, sighting.surface, watched, limit)
                } else {
                    ShortFormDecision.Allow
                }
            }
        }
    }

    /** True if [candidate] blocks at least as much as [reference]. */
    fun isAtLeastAsStrict(candidate: ShortFormRule, reference: ShortFormRule): Boolean = when (reference.mode) {
        ShortFormMode.ALLOW -> true
        ShortFormMode.BLOCK -> candidate.mode == ShortFormMode.BLOCK
        ShortFormMode.LIMIT -> candidate.mode == ShortFormMode.BLOCK ||
            (candidate.mode == ShortFormMode.LIMIT && (candidate.dailyLimit ?: 0) <= (reference.dailyLimit ?: 0))
    }

    fun isStrict(rule: ShortFormRule, now: Long): Boolean = (rule.strictUntil ?: Long.MIN_VALUE) > now

    /** While [current] is strictly locked it can only get stricter, and its lock can't shrink. */
    fun canChange(current: ShortFormRule, updated: ShortFormRule?, now: Long): Boolean {
        if (!isStrict(current, now)) return true
        if (updated == null) return false
        val lockEnd = current.strictUntil ?: return true
        return isAtLeastAsStrict(updated, current) && (updated.strictUntil ?: Long.MIN_VALUE) >= lockEnd
    }
}

/**
 * Decides whether a sighting is a *new* item worth counting. The detector reports the same
 * item many times a second while it plays, so only a change of item counts. Swiping back to an
 * item seen in the last [windowMs] doesn't count twice either.
 */
class ShortFormDeduper(private val windowMs: Long = 10_000) {
    private val current = HashMap<String, String>()
    private val lastSeen = HashMap<String, Long>()

    fun isNew(sighting: ShortFormSighting): Boolean {
        val via = sighting.viaPackage
        val key = "$via|${sighting.itemKey}"
        val previous = lastSeen[key]
        val changed = current[via] != sighting.itemKey
        current[via] = sighting.itemKey
        lastSeen[key] = sighting.atMs
        if (lastSeen.size > MAX_REMEMBERED) lastSeen.entries.removeAll { sighting.atMs - it.value > windowMs }
        return changed && (previous == null || sighting.atMs - previous > windowMs)
    }

    /** After a block, the next item shown in [viaPackage] is treated as new. */
    fun forget(viaPackage: String) {
        current.remove(viaPackage)
    }

    private companion object {
        const val MAX_REMEMBERED = 500
    }
}
