package app.arcify.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsitesTest {

    private fun url(raw: String) = UrlNormalizer.normalize(raw)!!

    @Test
    fun addressBarTextIsNormalised() {
        assertEquals(NormalizedUrl("youtube.com", "/shorts/abc"), url("m.youtube.com/shorts/abc"))
        assertEquals(NormalizedUrl("instagram.com", "/reels/xyz/"), url("https://www.instagram.com/reels/xyz/"))
        assertEquals("example.com", url("HTTP://Example.COM").host)
    }

    @Test
    fun searchTermsAndInternalPagesAreNotUrls() {
        assertNull(UrlNormalizer.normalize("lofi study music"))
        assertNull(UrlNormalizer.normalize("chrome://newtab"))
        assertNull(UrlNormalizer.normalize(""))
        assertNull(UrlNormalizer.normalize("localhost"))
    }

    @Test
    fun shortFormUrlsAreRecognised() {
        assertEquals(ShortFormUrlHit(Platform.YOUTUBE, "abc"), ShortFormUrls.classify(url("youtube.com/shorts/abc")))
        assertEquals(ShortFormUrlHit(Platform.INSTAGRAM, "xyz"), ShortFormUrls.classify(url("instagram.com/reel/xyz/")))
        assertEquals(ShortFormUrlHit(Platform.INSTAGRAM, "feed"), ShortFormUrls.classify(url("instagram.com/reels/")))
        assertNull(ShortFormUrls.classify(url("youtube.com/watch?v=abc")))
        assertNull(ShortFormUrls.classify(url("instagram.com/somebody")))
    }

    @Test
    fun patternsMatchSubdomainsAndPathPrefixes() {
        assertTrue(WebsitePolicy.matches("reddit.com", url("old.reddit.com/r/all")))
        assertTrue(WebsitePolicy.matches("https://www.reddit.com/", url("reddit.com")))
        assertFalse(WebsitePolicy.matches("reddit.com", url("notreddit.com")))
        assertTrue(WebsitePolicy.matches("youtube.com/shorts", url("youtube.com/shorts/abc")))
        assertFalse(WebsitePolicy.matches("youtube.com/shorts", url("youtube.com/shortsville")))
        assertFalse(WebsitePolicy.matches("  ", url("youtube.com")))
    }

    @Test
    fun blockRulesBlockOutsideStudyMode() {
        val rules = listOf(WebsiteRule("reddit.com", WebsiteRuleKind.BLOCK), WebsiteRule("wikipedia.org", WebsiteRuleKind.ALLOW))
        assertEquals(WebsiteDecision.Blocked("reddit.com", "reddit.com"), WebsitePolicy.decide(url("reddit.com"), rules, studyMode = false))
        assertEquals(WebsiteDecision.Allow, WebsitePolicy.decide(url("news.example.com"), rules, studyMode = false))
    }

    @Test
    fun studyModeOnlyAllowsAllowedSites() {
        val rules = listOf(WebsiteRule("wikipedia.org", WebsiteRuleKind.ALLOW))
        assertEquals(WebsiteDecision.Allow, WebsitePolicy.decide(url("en.wikipedia.org/wiki/Cell"), rules, studyMode = true))
        assertEquals(WebsiteDecision.Blocked("news.example.com", null), WebsitePolicy.decide(url("news.example.com"), rules, studyMode = true))
    }

    @Test
    fun studyChannelsMatchByHandleOrName() {
        val channels = listOf(StudyChannel("Lofi Girl", "@LofiGirl"), StudyChannel("Homework  Radio"))
        assertTrue(StudyChannelPolicy.isAllowed("@lofigirl", channels))
        assertTrue(StudyChannelPolicy.isAllowed("lofi girl", channels))
        assertTrue(StudyChannelPolicy.isAllowed("Homework Radio", channels))
        assertFalse(StudyChannelPolicy.isAllowed("Random Vlogs", channels))
        assertFalse(StudyChannelPolicy.isAllowed("  ", channels))
    }

    @Test
    fun handleOnlyEntriesMatchTheDisplayedName() {
        val channels = listOf(StudyChannel("LofiGirl", "@LofiGirl"))
        assertTrue(StudyChannelPolicy.isAllowed("Lofi Girl", channels))
    }

    @Test
    fun userInputIsParsedAsNameHandleOrLink() {
        assertEquals(StudyChannel("Lofi Girl"), StudyChannelPolicy.parseInput("  Lofi Girl "))
        assertEquals(StudyChannel("LofiGirl", "@LofiGirl"), StudyChannelPolicy.parseInput("@LofiGirl"))
        assertEquals(
            StudyChannel("ChillhopMusic", "@ChillhopMusic"),
            StudyChannelPolicy.parseInput("https://www.youtube.com/@ChillhopMusic/videos"),
        )
        assertNull(StudyChannelPolicy.parseInput("   "))
    }
}
