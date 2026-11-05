package app.arcify.blocker

/**
 * Stops the engine from acting on the same thing again and again while its first action
 * (pressing Back, going Home, showing a block screen) is still taking effect. Without it, the
 * flood of accessibility events right after a block triggers more blocks: screen flashes and
 * extra Back presses that throw the user out of the app.
 */
class ActionCooldown(private val windowMs: Long = DEFAULT_WINDOW_MS) {
    private val until = HashMap<String, Long>()

    fun isCooling(key: String, now: Long): Boolean = (until[key] ?: Long.MIN_VALUE) > now

    /** Records an action on [key] and returns true, unless one happened within the window. */
    fun tryAct(key: String, now: Long): Boolean {
        if (isCooling(key, now)) return false
        start(key, now)
        return true
    }

    fun start(key: String, now: Long) {
        until[key] = now + windowMs
        if (until.size > MAX_KEYS) until.entries.removeAll { it.value <= now }
    }

    private companion object {
        const val DEFAULT_WINDOW_MS = 2_000L
        const val MAX_KEYS = 200
    }
}
