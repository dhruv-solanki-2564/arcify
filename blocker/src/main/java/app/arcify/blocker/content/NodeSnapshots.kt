package app.arcify.blocker.content

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import app.arcify.blocker.Bounds
import app.arcify.blocker.NodeSnapshot

/** Copies a live accessibility tree into an immutable [NodeSnapshot], with size limits. */
object NodeSnapshots {

    fun capture(root: AccessibilityNodeInfo, maxNodes: Int = 2_500, maxDepth: Int = 60): NodeSnapshot {
        val budget = intArrayOf(maxNodes)
        return copy(root, depth = 0, maxDepth = maxDepth, budget = budget)
    }

    private fun copy(node: AccessibilityNodeInfo, depth: Int, maxDepth: Int, budget: IntArray): NodeSnapshot {
        budget[0]--
        val rect = Rect().also(node::getBoundsInScreen)
        val children = ArrayList<NodeSnapshot>()
        if (depth < maxDepth) {
            for (i in 0 until node.childCount) {
                if (budget[0] <= 0) break
                val child = node.getChild(i) ?: continue
                children += copy(child, depth + 1, maxDepth, budget)
            }
        }
        return NodeSnapshot(
            className = node.className?.toString(),
            viewId = node.viewIdResourceName,
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            visible = node.isVisibleToUser,
            selected = node.isSelected,
            editable = node.isEditable,
            hint = node.hintText?.toString(),
            children = children,
        )
    }
}
