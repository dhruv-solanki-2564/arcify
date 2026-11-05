package app.arcify.blocker.protection

import app.arcify.blocker.NodeSnapshot

/** What the user was about to do to Arcify. */
enum class Threat { UNINSTALL, SETTINGS, STORE }

/**
 * Where Arcify can be switched off or removed, and the button words that do it. Arcify's own
 * starting guesses: OEM settings packages and wording vary, so these need checking on real phones.
 */
data class ProtectionHints(
    val installerPackages: Set<String>,
    val settingsPackages: Set<String>,
    val storePackages: Set<String>,
    /** Lower-case fragments of buttons that would stop or remove Arcify. */
    val dangerWords: List<String>,
)

/**
 * Recognises screens that would disable or uninstall Arcify while protection is on (spec F07).
 *
 * A screen only counts if it is about Arcify: it names Arcify, or shows Arcify's accessibility
 * service. App lists that merely include Arcify, with no stop/remove button, are left alone.
 */
object ProtectionDetector {

    fun guards(packageName: String, hints: ProtectionHints): Boolean =
        packageName in hints.installerPackages || packageName in hints.settingsPackages || packageName in hints.storePackages

    fun detect(
        packageName: String,
        root: NodeSnapshot,
        appLabel: String,
        serviceLabel: String,
        hints: ProtectionHints,
    ): Threat? {
        val labels = root.walk()
            .filter { it.visible }
            .mapNotNull { it.label?.trim()?.lowercase() }
            .toList()
        val appWord = Regex("\\b${Regex.escape(appLabel.trim().lowercase())}\\b")
        val mentionsApp = labels.any { appWord.containsMatchIn(it) }
        val service = serviceLabel.trim().lowercase()
        val mentionsService = service.isNotEmpty() && labels.any { it.contains(service) }
        val dangerous = labels.any { label -> hints.dangerWords.any { label.contains(it) } }

        return when {
            packageName in hints.installerPackages && mentionsApp -> Threat.UNINSTALL
            packageName in hints.settingsPackages && (mentionsService || (mentionsApp && dangerous)) -> Threat.SETTINGS
            packageName in hints.storePackages && mentionsApp && dangerous -> Threat.STORE
            else -> null
        }
    }

    val DEFAULT_HINTS = ProtectionHints(
        installerPackages = setOf("com.android.packageinstaller", "com.google.android.packageinstaller"),
        settingsPackages = setOf(
            "com.android.settings",
            // OEM app-info screens (unverified).
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
        ),
        storePackages = setOf("com.android.vending"),
        dangerWords = listOf("uninstall", "force stop", "deactivate", "disable", "clear storage", "clear data"),
    )
}
