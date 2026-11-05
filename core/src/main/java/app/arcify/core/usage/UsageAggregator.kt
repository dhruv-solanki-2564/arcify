package app.arcify.core.usage

/** One activity moving to or from the foreground, as reported by Android's usage stats. */
data class AppEvent(
    val packageName: String,
    val className: String?,
    val type: Type,
    val timeMs: Long,
) {
    enum class Type { FOREGROUND, BACKGROUND }
}

/**
 * Turns raw activity events into foreground time per app. Pure, so it is unit-tested without a
 * device (spec F03/F08).
 *
 * - An app counts as foreground while at least one of its activities is, so switching between
 *   its own screens doesn't double count or drop time.
 * - An app whose first event in the window is a BACKGROUND was already open when the window
 *   began, so it's counted from [fromMs].
 * - Apps still open at the end are counted up to [toMs].
 */
object UsageAggregator {

    fun foregroundTime(events: List<AppEvent>, fromMs: Long, toMs: Long): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val states = HashMap<String, PackageState>()

        fun add(pkg: String, start: Long, end: Long) {
            val clippedStart = maxOf(start, fromMs)
            val clippedEnd = minOf(end, toMs)
            if (clippedEnd > clippedStart) totals[pkg] = (totals[pkg] ?: 0L) + (clippedEnd - clippedStart)
        }

        for (event in events.sortedBy { it.timeMs }) {
            val state = states.getOrPut(event.packageName) { PackageState() }
            val activity = event.className ?: ""
            when (event.type) {
                AppEvent.Type.FOREGROUND -> {
                    if (state.open.isEmpty()) state.openSince = event.timeMs
                    state.open += activity
                }

                AppEvent.Type.BACKGROUND -> when {
                    activity in state.open -> {
                        state.open -= activity
                        if (state.open.isEmpty()) {
                            add(event.packageName, state.openSince ?: fromMs, event.timeMs)
                            state.openSince = null
                        }
                    }

                    !state.seen -> add(event.packageName, fromMs, event.timeMs)
                }
            }
            state.seen = true
        }

        for ((pkg, state) in states) {
            if (state.open.isNotEmpty()) add(pkg, state.openSince ?: fromMs, toMs)
        }
        return totals
    }

    private class PackageState {
        val open = HashSet<String>()
        var openSince: Long? = null
        var seen = false
    }
}
