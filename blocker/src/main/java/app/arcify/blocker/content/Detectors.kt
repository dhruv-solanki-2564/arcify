package app.arcify.blocker.content

import app.arcify.blocker.Bounds
import app.arcify.blocker.NodeSnapshot
import app.arcify.core.content.IgSurface
import app.arcify.core.content.MonitoredBrowser

/**
 * Tuning for [ShortFormDetector]. The defaults are Arcify's own starting heuristics and still
 * need checking against recorded screens from a real phone (see SnapshotRecorder).
 */
data class ShortFormHints(
    /** Lower-case words that label the controls stacked beside a short-form player. */
    val controlWords: Set<String>,
    /** View-ID fragments that mean a regular (long-form) player is showing. */
    val notShortFormViewIds: List<String> = emptyList(),
    /** How many of those controls must be stacked on the right edge. */
    val minControls: Int = 3,
    /**
     * View-ID prefixes that only exist inside a short-form player (e.g. YouTube's `reel_…`
     * controls). Any visible match counts as a player, even without the control column.
     */
    val playerViewIdPrefixes: List<String> = emptyList(),
)

data class DetectedShortForm(val itemKey: String)

/**
 * Recognises a full-screen vertical video player by its signature layout: a column of
 * interaction controls (like, comment, share…) stacked along the right edge of a portrait
 * screen. Regular feeds lay those controls out in a row, so they don't match.
 */
object ShortFormDetector {

    fun detect(root: NodeSnapshot, screen: Bounds, hints: ShortFormHints): DetectedShortForm? {
        if (screen.width <= 0 || screen.height <= screen.width) return null
        val nodes = root.walk().filter { it.visible }.toList()
        val regularPlayer = hints.notShortFormViewIds.isNotEmpty() && nodes.any { node ->
            node.viewId?.let { id -> hints.notShortFormViewIds.any { id.contains(it) } } == true
        }
        if (regularPlayer) return null

        val rightEdge = screen.left + screen.width * RIGHT_EDGE_SHARE
        val controls = nodes.filter { node ->
            node.bounds.centerX >= rightEdge && node.label?.let { isControlLabel(it, hints.controlWords) } == true
        }
        val hasMarker = hints.playerViewIdPrefixes.isNotEmpty() && nodes.any { node ->
            node.viewId?.let { id -> hints.playerViewIdPrefixes.any { id.startsWith(it) } } == true
        }
        if (!hasMarker && !isControlColumn(controls, screen, hints)) return null

        // An item's identity: caption/author text in the lower-left area. It changes when the
        // user swipes to the next item, but not while one item plays. Ticking progress text
        // ("0 minutes 6 seconds of …") and pure numbers (like counts) are left out.
        val controlSet = controls.toSet()
        val signature = nodes.asSequence()
            .filter { it !in controlSet && it.bounds.centerX < rightEdge && it.bounds.top > screen.top + screen.height / 2 }
            .mapNotNull { it.label?.trim() }
            .filter { it.length >= 2 && it.any(Char::isLetter) && !PROGRESS.containsMatchIn(it.lowercase()) }
            .take(SIGNATURE_PARTS)
            .joinToString("|")
        return DetectedShortForm(if (signature.isEmpty()) "unknown" else Integer.toHexString(signature.hashCode()))
    }

    private fun isControlColumn(controls: List<NodeSnapshot>, screen: Bounds, hints: ShortFormHints): Boolean {
        if (stackedRows(controls, screen) < hints.minControls) return false
        val span = controls.maxOf { it.bounds.bottom } - controls.minOf { it.bounds.top }
        return span >= screen.height * MIN_COLUMN_SPAN_SHARE
    }

    fun isControlLabel(label: String, words: Set<String>): Boolean =
        label.lowercase().split(NON_WORD).any { it.isNotEmpty() && it in words }

    /** Counts controls on distinct vertical positions, so a single row of buttons doesn't qualify. */
    private fun stackedRows(controls: List<NodeSnapshot>, screen: Bounds): Int {
        val gap = (screen.height * MIN_ROW_GAP_SHARE).toInt().coerceAtLeast(1)
        var rows = 0
        var lastTop = Int.MIN_VALUE
        for (top in controls.map { it.bounds.top }.sorted()) {
            if (lastTop == Int.MIN_VALUE || top - lastTop >= gap) {
                rows++
                lastTop = top
            }
        }
        return rows
    }

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val PROGRESS = Regex("\\d+ (minutes?|seconds?)\\b.*\\bof\\b")
    private const val RIGHT_EDGE_SHARE = 0.72
    private const val MIN_COLUMN_SPAN_SHARE = 0.15
    private const val MIN_ROW_GAP_SHARE = 0.02
    private const val SIGNATURE_PARTS = 6
}

/**
 * Tuning for [IgSurfaceTracker], based on recordings from a real phone (Instagram, Nov 2026).
 */
data class InstagramHints(
    /** View-ID suffixes of the bottom tabs; the selected one tells the surface. */
    val tabViewIds: Map<String, IgSurface>,
    /** Fallback: lower-case labels of bottom tabs (only counted inside the bottom tab bar area). */
    val tabLabels: Map<IgSurface, List<String>>,
    /** View-ID suffix of the bottom tab bar: present on Instagram's main screens. */
    val tabBarViewId: String,
    /** View-ID fragments that only appear in direct-message threads. */
    val dmViewIdFragments: List<String>,
    /** Lower-case prefixes of the reply box under a reel someone sent in a DM. */
    val dmComposerPrefixes: List<String>,
)

/**
 * Works out which Instagram surface a reel belongs to (Arcify's per-surface toggles, spec F04).
 *
 * - On the main screens (bottom tab bar visible), the selected bottom tab decides.
 * - A reel opened from a DM chat plays in a full-screen viewer. Only the reel that was opened
 *   (the one sent in the chat) belongs to the DM surface; any other reel reached by swiping in
 *   that viewer is [IgPlacement.beyondSharedReel].
 * - Other full-screen viewers inherit the last main screen seen (feed, Explore).
 */
class IgSurfaceTracker(private val hints: InstagramHints) {
    private var lastContext: IgSurface = IgSurface.UNKNOWN

    /** The reel opened from a DM chat, while its viewer is open. */
    private var sharedReelKey: String? = null

    /** Feed every non-player screen through here. Leaving the viewer ends the DM reel. */
    fun observe(root: NodeSnapshot, screen: Bounds) {
        classify(root, screen)?.let { lastContext = it }
        sharedReelKey = null
    }

    fun placementForPlayer(root: NodeSnapshot, screen: Bounds, itemKey: String): IgPlacement {
        if (hasTabBar(root)) return IgPlacement(selectedTab(root, screen) ?: lastContext)
        if (lastContext == IgSurface.DM || hasDmReplyBox(root)) {
            val shared = sharedReelKey ?: itemKey.also { sharedReelKey = it }
            return IgPlacement(IgSurface.DM, beyondSharedReel = itemKey != shared)
        }
        return IgPlacement(lastContext)
    }

    fun reset() {
        lastContext = IgSurface.UNKNOWN
        sharedReelKey = null
    }

    internal fun classify(root: NodeSnapshot, screen: Bounds): IgSurface? =
        if (isDirectThread(root)) IgSurface.DM else selectedTab(root, screen)

    private fun hasTabBar(root: NodeSnapshot): Boolean =
        root.walk().any { it.visible && it.viewId?.endsWith(hints.tabBarViewId) == true }

    private fun selectedTab(root: NodeSnapshot, screen: Bounds): IgSurface? {
        val selected = root.walk().filter { it.selected && it.visible && it.bounds.isOnScreen(screen) }.toList()
        selected.firstNotNullOfOrNull { node ->
            node.viewId?.let { id -> hints.tabViewIds.entries.firstOrNull { (suffix, _) -> id.endsWith(suffix) }?.value }
        }?.let { return it }
        // Fallback by label, but only in the bottom tab bar area: Instagram also marks off-screen
        // and top-bar elements as selected.
        val barTop = screen.top + (screen.height * 0.85).toInt()
        return selected.filter { it.bounds.top >= barTop }.firstNotNullOfOrNull { node -> node.label?.let(::tabFor) }
    }

    private fun tabFor(label: String): IgSurface? {
        val lower = label.trim().lowercase()
        return hints.tabLabels.entries.firstOrNull { (_, labels) -> lower in labels }?.key
    }

    private fun isDirectThread(root: NodeSnapshot): Boolean =
        root.walk().any { node -> node.viewId?.let { id -> hints.dmViewIdFragments.any { id.contains(it) } } == true }

    private fun hasDmReplyBox(root: NodeSnapshot): Boolean = root.walk().any { node ->
        node.editable && (node.hint ?: node.text)?.trim()?.lowercase()
            ?.let { text -> hints.dmComposerPrefixes.any { text.startsWith(it) } } == true
    }
}

/**
 * Where an Instagram reel is. [beyondSharedReel] means the user opened a reel from a DM chat
 * and then swiped on to a different one.
 */
data class IgPlacement(val surface: IgSurface, val beyondSharedReel: Boolean = false)

/** True for bounds that are really on the screen (Instagram reports some off-screen nodes as selected). */
internal fun Bounds.isOnScreen(screen: Bounds): Boolean =
    right > left && bottom > top && left >= screen.left && right <= screen.right && top >= screen.top && bottom <= screen.bottom

/** Reads the address bar of a supported browser. */
object BrowserUrlDetector {
    fun read(root: NodeSnapshot, browser: MonitoredBrowser): String? =
        root.walk().firstOrNull { it.viewId != null && it.viewId in browser.urlBarIds }?.text
}

/** Tuning for [YouTubeChannelDetector]. Defaults are unverified starting points. */
data class YouTubeHints(
    val channelViewIdFragments: List<String>,
    /** Lower-case prefixes of content descriptions that name the channel. */
    val channelDescriptionPrefixes: List<String>,
)

/** Finds the channel name on a YouTube watch page. Returns null if it can't tell. */
object YouTubeChannelDetector {
    fun channelOnWatchPage(root: NodeSnapshot, hints: YouTubeHints): String? {
        for (node in root.walk()) {
            if (!node.visible) continue
            val id = node.viewId
            if (id != null && hints.channelViewIdFragments.any { id.contains(it) }) {
                node.label?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            val description = node.contentDescription?.trim() ?: continue
            val prefix = hints.channelDescriptionPrefixes.firstOrNull { description.lowercase().startsWith(it) }
            if (prefix != null) {
                description.substring(prefix.length).trim().trimStart(',', ':').trimEnd('.').trim()
                    .takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return null
    }
}

/** Arcify's bundled starting hints. Editable defaults; to be calibrated on a device. */
object DetectorDefaults {
    val shortForm = ShortFormHints(
        controlWords = setOf(
            "like", "likes", "liked", "dislike", "comment", "comments",
            "share", "shares", "send", "remix", "save", "favorite",
        ),
        // Seen on a real phone (Nov 2026). Only the player's own views count: Instagram's
        // bottom-bar Reels button is `clips_tab`, so a bare `clips_` prefix would match every
        // main screen. The viewer is kept alive but hidden on other tabs; hidden nodes are ignored.
        playerViewIdPrefixes = listOf(
            "com.google.android.youtube:id/reel_play_pause_button",
            "com.google.android.youtube:id/reel_next_reel_button",
            "com.google.android.youtube:id/reel_player",
            "com.instagram.android:id/clips_viewer",
        ),
        // The long-form YouTube player.
        notShortFormViewIds = listOf("com.google.android.youtube:id/watch_player"),
    )

    // From recordings on a real phone (Instagram, Nov 2026).
    val instagram = InstagramHints(
        tabViewIds = mapOf(
            ":id/feed_tab" to IgSurface.FEED,
            ":id/search_tab" to IgSurface.EXPLORE,
            ":id/clips_tab" to IgSurface.REELS_TAB,
            ":id/direct_tab" to IgSurface.DM,
        ),
        tabLabels = mapOf(
            IgSurface.FEED to listOf("home"),
            IgSurface.EXPLORE to listOf("search and explore", "search", "explore"),
            IgSurface.REELS_TAB to listOf("reels"),
            IgSurface.DM to listOf("message", "messages"),
        ),
        tabBarViewId = ":id/tab_bar",
        dmViewIdFragments = listOf(
            ":id/direct_thread",
            ":id/thread_view_root",
            ":id/message_thread_container",
            ":id/reel_share_item_view",
        ),
        // Only the reply box under a reel sent in a chat. Other viewers have comment boxes, so
        // generic words like "message" are not used.
        dmComposerPrefixes = listOf("reply to"),
    )

    val youtube = YouTubeHints(
        channelViewIdFragments = listOf(":id/channel_name", ":id/owner_name"),
        // Seen on a real phone: a watch page has a "Subscribe to <channel>." button.
        channelDescriptionPrefixes = listOf("subscribe to ", "unsubscribe from ", "go to channel"),
    )
}
