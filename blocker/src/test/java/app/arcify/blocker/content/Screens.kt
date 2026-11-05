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

    // ---- Instagram, modelled on recordings from a real phone (structure only) ----

    private const val IG = "com.instagram.android:id/"

    /** Instagram marks an off-screen top-bar "Reels" element as selected on every main screen. */
    private val strayReels = NodeSnapshot(contentDescription = "Reels", selected = true, bounds = Bounds(1345, 148, 1080, 240))

    /** The bottom tab bar with [selected] (feed_tab, clips_tab, direct_tab, search_tab) chosen. */
    private fun tabBar(selected: String) = NodeSnapshot(
        viewId = IG + "tab_bar",
        bounds = Bounds(0, 2178, 1080, 2300),
        children = listOf(
            "feed_tab" to "Home",
            "clips_tab" to "Reels",
            "direct_tab" to "Message",
            "search_tab" to "Search and explore",
        ).mapIndexed { i, (id, label) ->
            NodeSnapshot(viewId = IG + id, contentDescription = label, selected = id == selected, bounds = Bounds(i * 216, 2178, i * 216 + 216, 2300))
        },
    )

    private fun clipsViewer(caption: String) =
        NodeSnapshot(viewId = IG + "clips_viewer_view_pager", bounds = PORTRAIT, children = listOf(text(caption, 2050)))

    fun instagramHome() = NodeSnapshot(bounds = PORTRAIT, children = listOf(strayReels, tabBar("feed_tab")))

    fun instagramExplore() = NodeSnapshot(bounds = PORTRAIT, children = listOf(strayReels, tabBar("search_tab")))

    fun instagramDmThread() = NodeSnapshot(
        bounds = PORTRAIT,
        children = listOf(
            NodeSnapshot(viewId = IG + "direct_thread_header", bounds = Bounds(0, 100, 1080, 250)),
            NodeSnapshot(viewId = IG + "reel_share_item_view", bounds = Bounds(300, 800, 900, 1500)),
            NodeSnapshot(className = "android.widget.EditText", editable = true, hint = "Message...", bounds = Bounds(40, 2200, 900, 2280)),
        ),
    )

    /** Reels opened inside the main screens, with the bottom tab bar visible. */
    fun reelInMainScreen(caption: String, tab: String) =
        NodeSnapshot(bounds = PORTRAIT, children = listOf(strayReels, clipsViewer(caption), tabBar(tab)))

    fun reelsTabPlayer(caption: String) = reelInMainScreen(caption, "clips_tab")

    /** The full-screen viewer for a reel someone sent in a DM: no tab bar, a "Reply to…" box. */
    fun dmSharedReel(caption: String) = NodeSnapshot(
        bounds = PORTRAIT,
        children = listOf(
            clipsViewer(caption),
            NodeSnapshot(className = "android.widget.EditText", editable = true, hint = "Reply to a friend", bounds = Bounds(40, 2200, 900, 2280)),
        ),
    )

    /** The same viewer after swiping on: suggested reels, no reply box. */
    fun suggestedReel(caption: String) = NodeSnapshot(bounds = PORTRAIT, children = listOf(clipsViewer(caption)))

    fun browser(url: String, pkg: String = "com.android.chrome") = NodeSnapshot(
        bounds = PORTRAIT,
        children = listOf(NodeSnapshot(viewId = "$pkg:id/url_bar", text = url, bounds = Bounds(100, 60, 980, 160))),
    )
}
