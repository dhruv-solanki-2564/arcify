package app.arcify.core.batching

import java.time.Instant
import java.time.ZoneId

/**
 * Notification batching (spec F13): notifications from the chosen apps are held and delivered
 * together at fixed times; every other app stays instant.
 */
data class BatchingSettings(
    val enabled: Boolean = false,
    /** Deliveries happen every [intervalMinutes], aligned to local midnight (e.g. on the hour). */
    val intervalMinutes: Int = DEFAULT_INTERVAL_MIN,
    /** Apps whose notifications are held. */
    val packages: Set<String> = emptySet(),
) {
    companion object {
        const val DEFAULT_INTERVAL_MIN = 60
        val INTERVAL_CHOICES = listOf(30, 60, 120, 180)
    }
}

object BatchingRules {

    /** Kinds that must always arrive at once, whatever the settings. */
    private val NEVER_HOLD_CATEGORIES = setOf(
        "call", "alarm", "reminder", "navigation", "transport", "stopwatch", "progress", "service", "sys", "event",
    )

    /**
     * Whether a notification should be held for the next delivery. Ongoing notifications (music,
     * navigation, downloads), calls, alarms and Arcify's own are never held.
     */
    fun shouldHold(
        packageName: String,
        settings: BatchingSettings,
        ongoing: Boolean,
        category: String?,
        ownPackage: String,
    ): Boolean =
        settings.enabled &&
            packageName != ownPackage &&
            packageName in settings.packages &&
            !ongoing &&
            category !in NEVER_HOLD_CATEGORIES

    /** The next delivery strictly after [nowMs]: a multiple of the interval counted from local midnight. */
    fun nextDelivery(nowMs: Long, intervalMinutes: Int, zone: ZoneId): Long {
        val interval = intervalMinutes.coerceAtLeast(1).toLong() * 60_000L
        val midnight = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val slots = (nowMs - midnight) / interval + 1
        return midnight + slots * interval
    }
}
