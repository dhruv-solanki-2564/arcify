package app.arcify.blocker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionCooldownTest {

    @Test
    fun repeatsWithinTheWindowAreSuppressed() {
        val cooldown = ActionCooldown(windowMs = 2_000)
        assertTrue(cooldown.tryAct("instagram", now = 0))
        assertFalse(cooldown.tryAct("instagram", now = 500))
        assertTrue(cooldown.isCooling("instagram", now = 1_999))
        assertTrue(cooldown.tryAct("instagram", now = 2_000))
    }

    @Test
    fun keysAreIndependent() {
        val cooldown = ActionCooldown(windowMs = 2_000)
        assertTrue(cooldown.tryAct("instagram", now = 0))
        assertTrue(cooldown.tryAct("youtube", now = 1))
        assertFalse(cooldown.isCooling("chrome", now = 1))
    }
}
