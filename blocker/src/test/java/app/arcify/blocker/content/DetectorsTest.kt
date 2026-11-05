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

    @Test
    fun youtubeChannelIsReadFromItsLabel() {
        val page = NodeSnapshot(children = listOf(NodeSnapshot(contentDescription = "Go to channel Lofi Girl")))
        assertEquals("Lofi Girl", YouTubeChannelDetector.channelOnWatchPage(page, DetectorDefaults.youtube))
        assertNull(YouTubeChannelDetector.channelOnWatchPage(NodeSnapshot(), DetectorDefaults.youtube))
    }
}
