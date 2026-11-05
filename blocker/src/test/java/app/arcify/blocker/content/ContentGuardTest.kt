package app.arcify.blocker.content

import app.arcify.core.content.ContentDefaults
import app.arcify.core.content.IgSurface
import app.arcify.core.content.Platform
import app.arcify.core.content.ShortFormCounts
import app.arcify.core.content.ShortFormMode
import app.arcify.core.content.ShortFormRule
import app.arcify.core.content.ShortFormSighting
import app.arcify.core.content.StudyModeSettings
import app.arcify.core.content.WebsiteRule
import app.arcify.core.content.WebsiteRuleKind
import app.arcify.core.timer.FocusTimer
import app.arcify.core.timer.MINUTE_MS
import app.arcify.core.timer.TimerMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentGuardTest {

    private class FakeStore : ShortFormStore {
        val recorded = mutableListOf<ShortFormSighting>()
        override suspend fun countsToday(platform: Platform) = ShortFormCounts { p, surface ->
            recorded.count { it.platform == p && (surface == null || it.surface == surface) }
        }
        override suspend fun record(sighting: ShortFormSighting) {
            recorded += sighting
        }
    }

    private val chrome = "com.android.chrome"
    private val instagram = "com.instagram.android"
    private val youtube = ContentDefaults.YOUTUBE_PACKAGE
    private val baseState = ContentState(browsers = ContentDefaults.browsers)
    private val focusing = FocusTimer.start(TimerMode.Countdown(25 * MINUTE_MS), 0)

    @Test
    fun browserShortsAreCountedOncePerItem() = runTest {
        val store = FakeStore()
        val guard = ContentGuard(store)
        assertNull(guard.inspect(chrome, Screens.browser("youtube.com/shorts/a"), Screens.PORTRAIT, 0, baseState))
        assertNull(guard.inspect(chrome, Screens.browser("youtube.com/shorts/a"), Screens.PORTRAIT, 300, baseState))
        assertNull(guard.inspect(chrome, Screens.browser("youtube.com/shorts/b"), Screens.PORTRAIT, 600, baseState))
        assertEquals(listOf("a", "b"), store.recorded.map { it.itemKey })
        assertTrue(store.recorded.all { it.viaPackage == chrome && it.platform == Platform.YOUTUBE })
    }

    @Test
    fun blockedWebsitesAreBlocked() = runTest {
        val state = baseState.copy(websiteRules = listOf(WebsiteRule("reddit.com", WebsiteRuleKind.BLOCK)))
        val block = ContentGuard(FakeStore()).inspect(chrome, Screens.browser("www.reddit.com/r/all"), Screens.PORTRAIT, 0, state)
        assertEquals(ContentBlock.Website("reddit.com", studyMode = false), block)
    }

    @Test
    fun browserStudyModeOnlyAllowsAllowedSitesDuringFocus() = runTest {
        val state = baseState.copy(
            websiteRules = listOf(WebsiteRule("wikipedia.org", WebsiteRuleKind.ALLOW)),
            settings = StudyModeSettings(browserStudyMode = true),
            timer = focusing,
        )
        val guard = ContentGuard(FakeStore())
        assertNull(guard.inspect(chrome, Screens.browser("en.wikipedia.org/wiki/Cell"), Screens.PORTRAIT, 0, state))
        assertEquals(
            ContentBlock.Website("news.example.com", studyMode = true),
            guard.inspect(chrome, Screens.browser("news.example.com"), Screens.PORTRAIT, 0, state),
        )
        // Outside a focus session study mode doesn't apply.
        assertNull(guard.inspect(chrome, Screens.browser("news.example.com"), Screens.PORTRAIT, 0, state.copy(timer = null)))
    }

    @Test
    fun instagramDmReelsCanBeAllowedWhileOtherSurfacesAreBlocked() = runTest {
        val store = FakeStore()
        val guard = ContentGuard(store)
        val state = baseState.copy(
            shortFormRules = listOf(
                ShortFormRule(Platform.INSTAGRAM, null, ShortFormMode.BLOCK),
                ShortFormRule(Platform.INSTAGRAM, IgSurface.DM, ShortFormMode.ALLOW),
            ),
        )
        guard.inspect(instagram, Screens.instagramDmThread(), Screens.PORTRAIT, 0, state)
        assertNull(guard.inspect(instagram, Screens.shortFormPlayer("friend's reel"), Screens.PORTRAIT, 100, state))
        assertEquals(IgSurface.DM, store.recorded.single().surface)

        guard.inspect(instagram, Screens.instagramExplore(), Screens.PORTRAIT, 200, state)
        assertEquals(
            ContentBlock.ShortForm(Platform.INSTAGRAM, IgSurface.EXPLORE),
            guard.inspect(instagram, Screens.shortFormPlayer("explore reel"), Screens.PORTRAIT, 300, state),
        )
    }

    @Test
    fun limitBlocksTheItemAfterN() = runTest {
        val store = FakeStore()
        val guard = ContentGuard(store)
        val state = baseState.copy(shortFormRules = listOf(ShortFormRule(Platform.YOUTUBE, mode = ShortFormMode.LIMIT, dailyLimit = 2)))
        assertNull(guard.inspect(youtube, Screens.shortFormPlayer("one"), Screens.PORTRAIT, 0, state))
        assertNull(guard.inspect(youtube, Screens.shortFormPlayer("one"), Screens.PORTRAIT, 500, state)) // still playing
        assertNull(guard.inspect(youtube, Screens.shortFormPlayer("two"), Screens.PORTRAIT, 1_000, state))
        assertEquals(
            ContentBlock.ShortFormLimit(Platform.YOUTUBE, null, watched = 2, limit = 2),
            guard.inspect(youtube, Screens.shortFormPlayer("three"), Screens.PORTRAIT, 2_000, state),
        )
        assertEquals(2, store.recorded.size)
    }

    @Test
    fun youtubeStudyModeBlocksShortsDuringFocus() = runTest {
        val state = baseState.copy(settings = StudyModeSettings(youtubeStudyMode = true), timer = focusing)
        assertEquals(
            ContentBlock.ShortForm(Platform.YOUTUBE, null),
            ContentGuard(FakeStore()).inspect(youtube, Screens.shortFormPlayer("x"), Screens.PORTRAIT, 0, state),
        )
    }

    @Test
    fun youtubeStudyModeBlocksChannelsNotOnTheList() = runTest {
        val state = baseState.copy(
            settings = StudyModeSettings(youtubeStudyMode = true),
            timer = focusing,
            studyChannels = ContentDefaults.studyChannels,
        )
        val guard = ContentGuard(FakeStore())
        val study = app.arcify.blocker.NodeSnapshot(children = listOf(app.arcify.blocker.NodeSnapshot(contentDescription = "Go to channel Lofi Girl")))
        val other = app.arcify.blocker.NodeSnapshot(children = listOf(app.arcify.blocker.NodeSnapshot(contentDescription = "Go to channel Prank Central")))
        assertNull(guard.inspect(youtube, study, Screens.PORTRAIT, 0, state))
        assertEquals(ContentBlock.Channel("Prank Central"), guard.inspect(youtube, other, Screens.PORTRAIT, 0, state))
    }

    @Test
    fun unmonitoredAppsAreIgnored() = runTest {
        val guard = ContentGuard(FakeStore())
        assertNull(guard.inspect("com.example.notes", Screens.shortFormPlayer("x"), Screens.PORTRAIT, 0, baseState))
        assertTrue(!guard.monitors("com.example.notes", baseState))
        assertTrue(guard.monitors(chrome, baseState))
    }
}
