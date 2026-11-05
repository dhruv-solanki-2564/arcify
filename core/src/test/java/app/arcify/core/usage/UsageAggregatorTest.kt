package app.arcify.core.usage

import app.arcify.core.usage.AppEvent.Type.BACKGROUND
import app.arcify.core.usage.AppEvent.Type.FOREGROUND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAggregatorTest {

    private fun fg(pkg: String, t: Long, cls: String = "Main") = AppEvent(pkg, cls, FOREGROUND, t)
    private fun bg(pkg: String, t: Long, cls: String = "Main") = AppEvent(pkg, cls, BACKGROUND, t)

    @Test
    fun simpleSessionsAddUp() {
        val events = listOf(fg("a", 100), bg("a", 400), fg("a", 1_000), bg("a", 1_100))
        assertEquals(mapOf("a" to 400L), UsageAggregator.foregroundTime(events, 0, 2_000))
    }

    @Test
    fun appStillOpenIsCountedUntilNow() {
        val events = listOf(fg("a", 500))
        assertEquals(mapOf("a" to 1_500L), UsageAggregator.foregroundTime(events, 0, 2_000))
    }

    @Test
    fun appOpenBeforeTheWindowIsCountedFromTheStart() {
        val events = listOf(bg("a", 300))
        assertEquals(mapOf("a" to 300L), UsageAggregator.foregroundTime(events, 0, 2_000))
    }

    @Test
    fun switchingBetweenOwnScreensIsOneContinuousSession() {
        // Screen B resumes before screen A pauses, as Android often reports it.
        val events = listOf(
            fg("a", 100, "A"),
            fg("a", 200, "B"),
            bg("a", 210, "A"),
            bg("a", 900, "B"),
        )
        assertEquals(mapOf("a" to 800L), UsageAggregator.foregroundTime(events, 0, 2_000))
    }

    @Test
    fun unmatchedBackgroundAfterOtherEventsIsIgnored() {
        val events = listOf(fg("a", 100, "A"), bg("a", 200, "A"), bg("a", 300, "Ghost"))
        assertEquals(mapOf("a" to 100L), UsageAggregator.foregroundTime(events, 0, 2_000))
    }

    @Test
    fun launchesCountEachTimeTheAppIsOpenedInTheWindow() {
        val events = listOf(
            bg("a", 50), // already open at the window start: time counts, launch doesn't
            fg("a", 100, "A"),
            fg("a", 150, "B"), // second screen of the same visit
            bg("a", 160, "A"),
            bg("a", 300, "B"),
            fg("a", 1_000),
            bg("a", 1_200),
        )
        val stats = UsageAggregator.stats(events, 0, 2_000)
        assertEquals(AppUsage(foregroundMs = 50 + 200 + 200, launches = 2), stats["a"])
    }

    @Test
    fun sessionsAreClippedToTheWindowAndUnsortedInputIsFine() {
        val events = listOf(bg("b", 2_500), fg("b", 1_500), fg("c", 3_000))
        val totals = UsageAggregator.foregroundTime(events, 0, 2_000)
        assertEquals(mapOf("b" to 500L), totals)
        assertTrue("c" !in totals)
    }
}
