package app.arcify.blocker.content

import app.arcify.blocker.Bounds
import app.arcify.blocker.NodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotJsonTest {

    private val tree = NodeSnapshot(
        className = "android.widget.FrameLayout",
        bounds = Bounds(0, 0, 1080, 2400),
        children = listOf(
            NodeSnapshot(viewId = "a:id/caption", text = "Private message", bounds = Bounds(1, 2, 3, 4)),
            NodeSnapshot(contentDescription = "Like", selected = true, editable = true, hint = "Message...", visible = false),
        ),
    )

    @Test
    fun snapshotsRoundTrip() {
        assertEquals(tree, SnapshotJson.fromJson(SnapshotJson.toJson(tree, redactText = false)))
    }

    @Test
    fun redactionHidesTextButKeepsControlLabels() {
        val redacted = SnapshotJson.fromJson(SnapshotJson.toJson(tree, redactText = true))
        assertEquals("[text:15]", redacted.children[0].text)
        assertEquals("Like", redacted.children[1].contentDescription)
        assertEquals("Message...", redacted.children[1].hint)
    }
}
