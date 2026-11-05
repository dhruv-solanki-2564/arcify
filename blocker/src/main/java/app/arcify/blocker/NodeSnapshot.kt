package app.arcify.blocker

/**
 * Immutable copy of an accessibility node tree.
 *
 * Detectors work on snapshots instead of live `AccessibilityNodeInfo` objects, so they are
 * pure functions that can be unit-tested with recorded trees (see docs/spec/blocking-engine.md).
 */
data class NodeSnapshot(
    val className: String? = null,
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val bounds: Bounds = Bounds.EMPTY,
    val visible: Boolean = true,
    val children: List<NodeSnapshot> = emptyList(),
) {
    /** Depth-first traversal including this node. */
    fun walk(): Sequence<NodeSnapshot> = sequence {
        yield(this@NodeSnapshot)
        children.forEach { yieldAll(it.walk()) }
    }

    /** Visible label of the node: its text, or its content description when there is no text. */
    val label: String? get() = text?.takeIf { it.isNotBlank() } ?: contentDescription?.takeIf { it.isNotBlank() }
}

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2

    companion object {
        val EMPTY = Bounds(0, 0, 0, 0)
    }
}

/** What the accessibility service should do after a detector looked at a snapshot. */
sealed interface BlockAction {
    data object GoBack : BlockAction
    data object GoHome : BlockAction
    data class ShowOverlay(val reason: String) : BlockAction
    data class CountItem(val source: String, val itemHash: String) : BlockAction
}
