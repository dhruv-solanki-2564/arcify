package app.arcify.blocker.protection

import app.arcify.blocker.NodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hand-built screens modelling the layouts the detector looks for (not recordings of real phones). */
class ProtectionDetectorTest {

    private val hints = ProtectionDetector.DEFAULT_HINTS
    private val settings = "com.android.settings"
    private val installer = "com.google.android.packageinstaller"

    private fun screen(vararg labels: String) = NodeSnapshot(children = labels.map { NodeSnapshot(text = it) })

    private fun detect(pkg: String, root: NodeSnapshot) =
        ProtectionDetector.detect(pkg, root, appLabel = "Arcify", serviceLabel = "Arcify blocker", hints = hints)

    @Test
    fun uninstallDialogForArcifyIsCaught() {
        assertEquals(Threat.UNINSTALL, detect(installer, screen("Arcify", "Do you want to uninstall this app?", "OK")))
    }

    @Test
    fun uninstallDialogsForOtherAppsAreLeftAlone() {
        assertNull(detect(installer, screen("Some Game", "Do you want to uninstall this app?")))
    }

    @Test
    fun arcifyAppInfoWithStopButtonsIsCaught() {
        assertEquals(Threat.SETTINGS, detect(settings, screen("App info", "Arcify", "Uninstall", "Force stop")))
    }

    @Test
    fun accessibilityPageForArcifyIsCaught() {
        assertEquals(Threat.SETTINGS, detect(settings, screen("Accessibility", "Downloaded apps", "Arcify blocker")))
    }

    @Test
    fun appListsThatMerelyIncludeArcifyAreLeftAlone() {
        assertNull(detect(settings, screen("Apps", "Arcify", "Calculator", "Camera")))
    }

    @Test
    fun appNameMustMatchAsAWholeWord() {
        assertNull(detect(settings, screen("Arcifyx", "Force stop")))
    }

    @Test
    fun playStorePageWithUninstallIsCaught() {
        assertEquals(Threat.STORE, detect("com.android.vending", screen("Arcify", "Uninstall", "Open")))
    }

    @Test
    fun onlyGuardedPackagesAreInspected() {
        assertTrue(ProtectionDetector.guards(settings, hints))
        assertFalse(ProtectionDetector.guards("com.example.notes", hints))
        assertNull(detect("com.example.notes", screen("Arcify", "Uninstall")))
    }
}
