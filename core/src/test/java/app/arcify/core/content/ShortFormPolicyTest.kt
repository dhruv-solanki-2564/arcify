package app.arcify.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortFormPolicyTest {

    private fun sighting(
        platform: Platform = Platform.INSTAGRAM,
        surface: IgSurface? = IgSurface.REELS_TAB,
        item: String = "a",
        at: Long = 0,
        via: String = "com.instagram.android",
    ) = ShortFormSighting(platform, via, surface, item, at)

    private val none = ShortFormCounts { _, _ -> 0 }

    @Test
    fun noRuleMeansAllowed() {
        assertEquals(ShortFormDecision.Allow, ShortFormPolicy.decide(sighting(), emptyList(), none))
    }

    @Test
    fun surfaceRuleOverridesThePlatformRule() {
        val rules = listOf(
            ShortFormRule(Platform.INSTAGRAM, null, ShortFormMode.BLOCK),
            ShortFormRule(Platform.INSTAGRAM, IgSurface.DM, ShortFormMode.ALLOW),
        )
        assertEquals(ShortFormDecision.Allow, ShortFormPolicy.decide(sighting(surface = IgSurface.DM), rules, none))
        assertEquals(
            ShortFormDecision.Blocked(Platform.INSTAGRAM, IgSurface.EXPLORE),
            ShortFormPolicy.decide(sighting(surface = IgSurface.EXPLORE), rules, none),
        )
    }

    @Test
    fun unknownSurfaceUsesTheStrictestInstagramRule() {
        val rules = listOf(
            ShortFormRule(Platform.INSTAGRAM, IgSurface.DM, ShortFormMode.ALLOW),
            ShortFormRule(Platform.INSTAGRAM, IgSurface.FEED, ShortFormMode.LIMIT, dailyLimit = 10),
            ShortFormRule(Platform.INSTAGRAM, IgSurface.REELS_TAB, ShortFormMode.BLOCK),
        )
        assertEquals(
            ShortFormMode.BLOCK,
            ShortFormPolicy.ruleFor(Platform.INSTAGRAM, IgSurface.UNKNOWN, rules)?.mode,
        )
    }

    @Test
    fun limitBlocksOnceTheCountReachesN() {
        val rules = listOf(ShortFormRule(Platform.YOUTUBE, mode = ShortFormMode.LIMIT, dailyLimit = 3))
        val s = sighting(platform = Platform.YOUTUBE, surface = null)
        assertEquals(ShortFormDecision.Allow, ShortFormPolicy.decide(s, rules) { _, _ -> 2 })
        assertEquals(
            ShortFormDecision.LimitReached(Platform.YOUTUBE, null, 3, 3),
            ShortFormPolicy.decide(s, rules) { _, _ -> 3 },
        )
    }

    @Test
    fun strictRulesCanOnlyGetStricter() {
        val limit5 = ShortFormRule(Platform.YOUTUBE, mode = ShortFormMode.LIMIT, dailyLimit = 5, strictUntil = 1_000)
        assertTrue(ShortFormPolicy.canChange(limit5, limit5.copy(dailyLimit = 3), now = 10))
        assertTrue(ShortFormPolicy.canChange(limit5, limit5.copy(mode = ShortFormMode.BLOCK), now = 10))
        assertFalse(ShortFormPolicy.canChange(limit5, limit5.copy(dailyLimit = 8), now = 10))
        assertFalse(ShortFormPolicy.canChange(limit5, limit5.copy(mode = ShortFormMode.ALLOW), now = 10))
        assertFalse(ShortFormPolicy.canChange(limit5, null, now = 10))
        assertTrue(ShortFormPolicy.canChange(limit5, null, now = 1_000))
    }

    @Test
    fun deduperCountsEachItemOnceWhileItPlays() {
        val deduper = ShortFormDeduper(windowMs = 10_000)
        assertTrue(deduper.isNew(sighting(item = "a", at = 0)))
        assertFalse(deduper.isNew(sighting(item = "a", at = 500)))
        assertFalse(deduper.isNew(sighting(item = "a", at = 4_000)))
        assertTrue(deduper.isNew(sighting(item = "b", at = 5_000)))
        // Swiping straight back to "a" (last seen 1 s earlier) doesn't count again...
        assertFalse(deduper.isNew(sighting(item = "a", at = 5_000)))
        // ...but coming back to it much later does.
        assertTrue(deduper.isNew(sighting(item = "b", at = 30_000)))
    }

    @Test
    fun deduperTracksEachAppSeparately() {
        val deduper = ShortFormDeduper()
        assertTrue(deduper.isNew(sighting(item = "x", via = "com.instagram.android")))
        assertTrue(deduper.isNew(sighting(item = "x", via = "com.android.chrome")))
    }

    @Test
    fun forgettingMakesTheNextItemNew() {
        val deduper = ShortFormDeduper(windowMs = 0)
        assertTrue(deduper.isNew(sighting(item = "a", at = 0)))
        deduper.forget("com.instagram.android")
        assertTrue(deduper.isNew(sighting(item = "a", at = 1)))
    }
}
