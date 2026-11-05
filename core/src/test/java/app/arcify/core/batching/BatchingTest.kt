package app.arcify.core.batching

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchingTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val own = "app.arcify"
    private val settings = BatchingSettings(enabled = true, packages = setOf("com.example.social"))

    private fun ms(hour: Int, minute: Int, second: Int = 0) =
        LocalDateTime.of(2026, 11, 5, hour, minute, second).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun onlyChosenAppsAreHeld() {
        assertTrue(BatchingRules.shouldHold("com.example.social", settings, ongoing = false, category = "social", ownPackage = own))
        assertFalse(BatchingRules.shouldHold("com.example.bank", settings, ongoing = false, category = null, ownPackage = own))
        assertFalse(BatchingRules.shouldHold("com.example.social", settings.copy(enabled = false), ongoing = false, category = null, ownPackage = own))
    }

    @Test
    fun callsAlarmsAndOngoingNotificationsAreNeverHeld() {
        assertFalse(BatchingRules.shouldHold("com.example.social", settings, ongoing = true, category = null, ownPackage = own))
        assertFalse(BatchingRules.shouldHold("com.example.social", settings, ongoing = false, category = "call", ownPackage = own))
        assertFalse(BatchingRules.shouldHold("com.example.social", settings, ongoing = false, category = "alarm", ownPackage = own))
        val ownToo = settings.copy(packages = setOf(own))
        assertFalse(BatchingRules.shouldHold(own, ownToo, ongoing = false, category = null, ownPackage = own))
    }

    @Test
    fun deliveriesAreAlignedToTheClock() {
        assertEquals(ms(11, 0), BatchingRules.nextDelivery(ms(10, 17), 60, zone))
        assertEquals(ms(11, 0), BatchingRules.nextDelivery(ms(10, 30), 30, zone))
        assertEquals(ms(12, 0), BatchingRules.nextDelivery(ms(10, 0), 120, zone))
        // Exactly on a slot: the next one, never "now".
        assertEquals(ms(11, 0), BatchingRules.nextDelivery(ms(10, 0), 60, zone))
    }
}
