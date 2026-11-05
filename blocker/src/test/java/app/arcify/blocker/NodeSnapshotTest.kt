package app.arcify.blocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeSnapshotTest {

    @Test
    fun walkVisitsEveryNodeDepthFirst() {
        val tree = NodeSnapshot(
            viewId = "root",
            children = listOf(
                NodeSnapshot(viewId = "a", children = listOf(NodeSnapshot(viewId = "a1"))),
                NodeSnapshot(viewId = "b"),
            ),
        )
        assertEquals(listOf("root", "a", "a1", "b"), tree.walk().map { it.viewId }.toList())
    }

    @Test
    fun labelPrefersTextThenDescription() {
        assertEquals("Hi", NodeSnapshot(text = "Hi", contentDescription = "desc").label)
        assertEquals("desc", NodeSnapshot(text = " ", contentDescription = "desc").label)
        assertNull(NodeSnapshot().label)
    }
}
