package app.arcify.blocker.content

import app.arcify.blocker.Bounds
import app.arcify.blocker.NodeSnapshot
import app.arcify.core.content.ContentDefaults
import app.arcify.core.content.IgSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorsTest {

    private val hints = DetectorDefaults.shortForm

    @Test
    fun verticalPlayerIsDetected() {
        assertNotNull(ShortFormDetector.detect(Screens.shortFormPlayer("A cat"), Screens.PORTRAIT, hints))
    }

    @Test
    fun itemKeyFollowsTheCaptionNotTime() {
        val a1 = ShortFormDetector.detect(Screens.shortFormPlayer("A cat"), Screens.PORTRAIT, hints)!!
        val a2 = ShortFormDetector.detect(Screens.shortFormPlayer("A cat"), Screens.PORTRAIT, hints)!!
        val b = ShortFormDetector.detect(Screens.shortFormPlayer("A dog"), Screens.PORTRAIT, hints)!!
        assertEquals(a1.itemKey, a2.itemKey)
        assertNotEquals(a1.itemKey, b.itemKey)
    }

    @Test
    fun feedPostsWithARowOfButtonsAreNotShortForm() {
        assertNull(ShortFormDetector.detect(Screens.feedPost(), Screens.PORTRAIT, hints))
    }

    @Test
    fun landscapeScreensAreIgnored() {
        assertNull(ShortFormDetector.detect(Screens.shortFormPlayer("A cat"), Bounds(0, 0, 2400, 1080), hints))
    }

    @Test
    fun regularPlayerMarkerSuppressesDetection() {
        val withMarker = Screens.shortFormPlayer("A cat", extra = listOf(NodeSnapshot(viewId = "app:id/watch_player")))
        val strict = hints.copy(notShortFormViewIds = listOf("watch_player"))
        assertNull(ShortFormDetector.detect(withMarker, Screens.PORTRAIT, strict))
    }

    @Test
    fun controlLabelsMatchWholeWords() {
        assertEquals(true, ShortFormDetector.isControlLabel("Like this video", hints.controlWords))
        assertEquals(true, ShortFormDetector.isControlLabel("12K likes", hints.controlWords))
        assertEquals(false, ShortFormDetector.isControlLabel("Unlikely story", hints.controlWords))
    }

    @Test
    fun instagramSurfaceFollowsWhereTheUserCameFrom() {
        val tracker = IgSurfaceTracker(DetectorDefaults.instagram)
        val player = Screens.shortFormPlayer("x")
        assertEquals(IgSurface.UNKNOWN, tracker.surfaceForPlayer(player))

        tracker.observe(Screens.instagramDmThread())
        assertEquals(IgSurface.DM, tracker.surfaceForPlayer(player))

        tracker.observe(Screens.instagramExplore())
        assertEquals(IgSurface.EXPLORE, tracker.surfaceForPlayer(player))

        tracker.observe(Screens.instagramHome())
        assertEquals(IgSurface.FEED, tracker.surfaceForPlayer(player))

        assertEquals(IgSurface.REELS_TAB, tracker.surfaceForPlayer(Screens.reelsTabPlayer("y")))
    }

    @Test
    fun addressBarIsReadFromTheConfiguredId() {
        val chrome = ContentDefaults.browsers.first { it.packageName == "com.android.chrome" }
        assertEquals("youtube.com/shorts/abc", BrowserUrlDetector.read(Screens.browser("youtube.com/shorts/abc"), chrome))
        assertNull(BrowserUrlDetector.read(Screens.browser("x.com", pkg = "com.other"), chrome))
    }

    /** Mirrors the YouTube Shorts layout seen on a real phone: `reel_…` controls, no visible like column. */
    private fun youtubeShorts(progress: String) = NodeSnapshot(
        bounds = Screens.PORTRAIT,
        children = listOf(
            NodeSnapshot(viewId = "com.google.android.youtube:id/reel_play_pause_button", contentDescription = "Pause video", bounds = Bounds(479, 1084, 601, 1206)),
            NodeSnapshot(viewId = "com.google.android.youtube:id/reel_next_reel_button", contentDescription = "Next Video", bounds = Bounds(479, 1206, 601, 1328)),
            NodeSnapshot(contentDescription = "Shorts", selected = true, bounds = Bounds(270, 2178, 540, 2300)),
            NodeSnapshot(contentDescription = progress, bounds = Bounds(0, 2116, 1080, 2238)),
            NodeSnapshot(text = "Cooking a quick pasta", bounds = Bounds(40, 1900, 700, 1960)),
        ),
    )

    @Test
    fun youtubeShortsAreRecognisedByTheirReelControls() {
        val early = ShortFormDetector.detect(youtubeShorts("0 minutes 6 seconds of 0 minutes 23 seconds"), Screens.PORTRAIT, hints)
        val later = ShortFormDetector.detect(youtubeShorts("0 minutes 9 seconds of 0 minutes 23 seconds"), Screens.PORTRAIT, hints)
        assertNotNull(early)
        // The ticking progress label must not make one Short look like a new one.
        assertEquals(early!!.itemKey, later!!.itemKey)
    }

    @Test
    fun aRegularYoutubeVideoIsNotShortForm() {
        val watch = NodeSnapshot(
            bounds = Screens.PORTRAIT,
            children = listOf(
                NodeSnapshot(viewId = "com.google.android.youtube:id/watch_player", contentDescription = "Video player", bounds = Bounds(0, 112, 1080, 720)),
                NodeSnapshot(contentDescription = "like this video along with 3,481,707 other people", bounds = Bounds(555, 910, 660, 1032)),
                NodeSnapshot(contentDescription = "Dislike this video", bounds = Bounds(660, 910, 765, 1032)),
                NodeSnapshot(contentDescription = "Share", bounds = Bounds(765, 910, 870, 1032)),
            ),
        )
        assertNull(ShortFormDetector.detect(watch, Screens.PORTRAIT, hints))
    }

    @Test
    fun youtubeChannelIsReadFromTheSubscribeButton() {
        val page = NodeSnapshot(
            children = listOf(
                NodeSnapshot(contentDescription = "Lofi Girl, Official Artist Channel 15.8m subscribers"),
                NodeSnapshot(contentDescription = "Subscribe to Lofi Girl."),
            ),
        )
        assertEquals("Lofi Girl", YouTubeChannelDetector.channelOnWatchPage(page, DetectorDefaults.youtube))
    }

    @Test
    fun youtubeChannelIsReadFromItsLabel() {
        val page = NodeSnapshot(children = listOf(NodeSnapshot(contentDescription = "Go to channel Lofi Girl")))
        assertEquals("Lofi Girl", YouTubeChannelDetector.channelOnWatchPage(page, DetectorDefaults.youtube))
        assertNull(YouTubeChannelDetector.channelOnWatchPage(NodeSnapshot(), DetectorDefaults.youtube))
    }
}
