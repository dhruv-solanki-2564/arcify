package app.arcify.core.util

import java.util.Locale

/** Formats a duration as `MM:SS`, or `H:MM:SS` from one hour up. Negative values show as zero. */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/** Formats a duration in whole minutes for labels: `0 min`, `45 min`, `1 h 5 min`, `2 h`. */
fun formatShortDuration(ms: Long): String {
    val totalMinutes = ms.coerceAtLeast(0) / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours h"
        else -> "$hours h $minutes min"
    }
}
