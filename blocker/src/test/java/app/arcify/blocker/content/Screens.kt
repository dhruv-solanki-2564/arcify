package app.arcify.blocker.content

import app.arcify.blocker.Bounds
import app.arcify.blocker.NodeSnapshot

/**
 * Hand-built screen trees for detector tests. They model the *layouts* the detectors look for.
 * They are not recordings of real apps; recorded fixtures will be added once a phone is available.
 */
object Screens {
    val PORTRAIT = Bounds(0, 0, 1080, 2400)

    private fun control(label: String, top: Int) =
        NodeSnapshot(className = "android.widget.Button", contentDescription = label, bounds = Bounds(960, top, 1060, top + 100))

    private fun text(value: String, top: Int, left: Int = 40, right: Int = 700) =
        NodeSnapshot(className = "android.widget.TextView", text = value, bounds = Bounds(left, top, right, top + 60))

    /** A vertical player: like/comment/share stacked on the right, caption and author bottom-left. */
    fun shortFormPlayer(caption: String, author: String = "@creator", extra: List<NodeSnapshot> = emptyList()) = NodeSnapshot(
        className = "android.widget.FrameLayout",
        bounds = PORTRAIT,
        children = listOf(
            control("Like", 1300),
            control("Comment", 1500),
            control("Share", 1700),
            text(author, 1950),
            text(caption, 2050),
        ) + extra,
    )

    /** A feed post: the same controls, but laid out in one row under the post. */
    fun feedPost() = NodeSnapshot(
        bounds = PORTRAIT,
        children = listOf(
            NodeSnapshot(contentDescription = "Like", bounds = Bounds(40, 1400, 140, 1500)),
            NodeSnapshot(contentDescription = "Comment", bounds = Bounds(170, 1400, 270, 1500)),
            NodeSnapshot(contentDescription = "Share", bounds = Bounds(300, 1400, 400, 1500)),
        ),
    )

    fun tab(label: String, selected: Boolean) =
        NodeSnapshot(contentDescription = label, selected = selected, bounds = Bounds(0, 2300, 200, 2400))

    fun instagramHome() = NodeSnapshot(bounds = PORTRAIT, children = listOf(tab("Home", true), tab("Reels", false)))

    fun instagramExplore() = NodeSnapshot(bounds = PORTRAIT, children = listOf(tab("Home", false), tab("Search and explore", true)))

    fun instagramDmThread() = NodeSnapshot(
        bounds = PORTRAIT,
        children = listOf(NodeSnapshot(className = "android.widget.EditText", editable = true, hint = "Message...", bounds = Bounds(40, 2200, 900, 2280))),
    )

    fun reelsTabPlayer(caption: String) = shortFormPlayer(caption, extra = listOf(tab("Reels", true)))

    fun browser(url: String, pkg: String = "com.android.chrome") = NodeSnapshot(
        bounds = PORTRAIT,
        children = listOf(NodeSnapshot(viewId = "$pkg:id/url_bar", text = url, bounds = Bounds(100, 60, 980, 160))),
    )
}
