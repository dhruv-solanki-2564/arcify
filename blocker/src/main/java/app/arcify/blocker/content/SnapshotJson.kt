package app.arcify.blocker.content

import app.arcify.blocker.Bounds
import app.arcify.blocker.NodeSnapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON form of [NodeSnapshot], for the debug recorder and for test fixtures.
 *
 * With [redactText], visible text is replaced by a length marker so recordings don't carry
 * messages or captions. Control labels (content descriptions) and hints are kept, because
 * detectors depend on them. Review recordings before committing them as fixtures.
 */
object SnapshotJson {

    fun toJson(node: NodeSnapshot, redactText: Boolean): JSONObject = JSONObject().apply {
        node.className?.let { put("class", it) }
        node.viewId?.let { put("id", it) }
        node.text?.let { put("text", if (redactText) "[text:${it.length}]" else it) }
        node.contentDescription?.let { put("desc", if (redactText) redactLabel(it) else it) }
        node.hint?.let { put("hint", if (redactText) redactLabel(it) else it) }
        put("bounds", JSONArray(listOf(node.bounds.left, node.bounds.top, node.bounds.right, node.bounds.bottom)))
        if (!node.visible) put("visible", false)
        if (node.selected) put("selected", true)
        if (node.editable) put("editable", true)
        if (node.children.isNotEmpty()) {
            put("children", JSONArray(node.children.map { toJson(it, redactText) }))
        }
    }

    fun fromJson(json: JSONObject): NodeSnapshot {
        val b = json.optJSONArray("bounds")
        val bounds = if (b != null && b.length() == 4) Bounds(b.getInt(0), b.getInt(1), b.getInt(2), b.getInt(3)) else Bounds.EMPTY
        val children = json.optJSONArray("children")?.let { array -> (0 until array.length()).map { fromJson(array.getJSONObject(it)) } }
        return NodeSnapshot(
            className = json.optStringOrNull("class"),
            viewId = json.optStringOrNull("id"),
            text = json.optStringOrNull("text"),
            contentDescription = json.optStringOrNull("desc"),
            bounds = bounds,
            visible = json.optBoolean("visible", true),
            selected = json.optBoolean("selected", false),
            editable = json.optBoolean("editable", false),
            hint = json.optStringOrNull("hint"),
            children = children.orEmpty(),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    /**
     * Short control labels ("Like", "Search and explore") are kept because detectors need them.
     * Longer ones can carry names ("Reply to <friend>", profile captions), so only their first
     * word survives.
     */
    internal fun redactLabel(label: String): String {
        val words = label.trim().split(Regex("\\s+"))
        return if (words.size <= MAX_KEPT_WORDS) label else "${words.first()} [${label.length}]"
    }

    private const val MAX_KEPT_WORDS = 3
}
