package app.arcify.core.block

/** A daily time limit for one app (spec F03). */
data class AppLimit(
    val packageName: String,
    val dailyLimitMs: Long,
    val enabled: Boolean = true,
    /** While in the future, the limit can only get tighter (Strict Mode). */
    val strictUntil: Long? = null,
    /** Remind the user once usage passes this share of the limit; null = no reminder. */
    val reminderPercent: Int? = DEFAULT_REMINDER_PERCENT,
) {
    companion object {
        const val DEFAULT_REMINDER_PERCENT = 80
    }
}

enum class LimitStatus { UNDER, REMINDER, REACHED }

object LimitRules {

    fun isStrict(limit: AppLimit, now: Long): Boolean = (limit.strictUntil ?: Long.MIN_VALUE) > now

    fun status(limit: AppLimit, usedMs: Long): LimitStatus {
        if (!limit.enabled) return LimitStatus.UNDER
        if (usedMs >= limit.dailyLimitMs) return LimitStatus.REACHED
        val reminder = limit.reminderPercent ?: return LimitStatus.UNDER
        return if (usedMs * 100 >= limit.dailyLimitMs * reminder) LimitStatus.REMINDER else LimitStatus.UNDER
    }

    /**
     * Whether [current] may be replaced by [updated] (null = deleted). A strict limit can't be
     * deleted, disabled, loosened or have its lock shortened until the lock ends. Making it
     * stricter is always allowed.
     */
    fun canChange(current: AppLimit, updated: AppLimit?, now: Long): Boolean {
        if (!isStrict(current, now)) return true
        if (updated == null) return false
        val lockEnd = current.strictUntil ?: return true
        return updated.enabled &&
            updated.dailyLimitMs <= current.dailyLimitMs &&
            (updated.strictUntil ?: Long.MIN_VALUE) >= lockEnd
    }
}
