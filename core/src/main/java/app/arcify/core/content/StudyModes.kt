package app.arcify.core.content

import app.arcify.core.timer.ActiveTimer
import app.arcify.core.timer.TimerPhase

/** A YouTube channel on the study list (spec F05). */
data class StudyChannel(
    val displayName: String,
    val handle: String? = null,
    val isDefault: Boolean = false,
)

/** User switches for the two study modes. */
data class StudyModeSettings(
    val youtubeStudyMode: Boolean = false,
    val browserStudyMode: Boolean = false,
)

object StudyModes {
    /** Study modes apply during focus phases of any session, strict or not. Breaks are free. */
    fun inFocus(timer: ActiveTimer?): Boolean = timer != null && timer.phase == TimerPhase.FOCUS

    fun youtubeActive(timer: ActiveTimer?, settings: StudyModeSettings): Boolean =
        settings.youtubeStudyMode && inFocus(timer)

    fun browserActive(timer: ActiveTimer?, settings: StudyModeSettings): Boolean =
        settings.browserStudyMode && inFocus(timer)
}

object StudyChannelPolicy {
    /** Matches a channel name or handle seen on screen against the list: handle first, then name. */
    fun isAllowed(seen: String, channels: List<StudyChannel>): Boolean {
        val wanted = normalize(seen)
        if (wanted.isEmpty()) return false
        return channels.any { channel ->
            (channel.handle != null && normalize(channel.handle) == wanted) || normalize(channel.displayName) == wanted
        }
    }

    /** Compact form for comparison: lower case, no `@`, letters and digits only ("Lofi Girl" == "@LofiGirl"). */
    fun normalize(name: String): String =
        name.trim().removePrefix("@").lowercase().filter { it.isLetterOrDigit() }

    /**
     * Turns what the user typed into a channel: a name ("Lofi Girl"), a handle ("@LofiGirl") or a
     * channel link ("youtube.com/@LofiGirl"). Returns null for blank input.
     */
    fun parseInput(text: String): StudyChannel? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val handle = HANDLE.find(trimmed)?.groupValues?.get(1)
        val looksLikeHandle = trimmed.startsWith("@") || trimmed.contains("youtube.com/", ignoreCase = true)
        return if (handle != null && looksLikeHandle) {
            StudyChannel(displayName = handle.removePrefix("@"), handle = handle)
        } else {
            StudyChannel(displayName = trimmed)
        }
    }

    private val HANDLE = Regex("(@[A-Za-z0-9._-]+)")
}
