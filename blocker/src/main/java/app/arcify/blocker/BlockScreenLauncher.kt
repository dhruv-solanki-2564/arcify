package app.arcify.blocker

import app.arcify.blocker.content.ContentBlock
import app.arcify.core.block.BlockDecision

/**
 * How the blocking engine talks to the UI. Implemented by the app module, so `:blocker` stays
 * free of screens and notification styling.
 */
interface BlockScreenLauncher {
    /** Covers the current app. Called with [BlockDecision.StrictFocus] or [BlockDecision.LimitReached]. */
    fun showBlock(decision: BlockDecision)

    /** Brings the focus screen back (the home screen was opened during strict focus). */
    fun returnToFocus()

    /** Once-a-day heads-up that an app is close to its limit. */
    fun limitReminder(packageName: String, usedMs: Long, limitMs: Long)

    /** Content inside [viaPackage] was blocked; the engine has already navigated back. */
    fun showContentBlock(block: ContentBlock, viaPackage: String)
}
