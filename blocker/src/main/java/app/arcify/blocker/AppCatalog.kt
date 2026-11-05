package app.arcify.blocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import app.arcify.core.insights.AppCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(val packageName: String, val label: String)

/** What's installed, and which apps are special (home screens, phone, keyboards). */
@Singleton
class AppCatalog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val pm: PackageManager get() = context.packageManager
    private val activityCache = ConcurrentHashMap<String, Boolean>()
    private val categoryCache = ConcurrentHashMap<String, AppCategory>()

    /** Apps with a launcher icon, except Arcify, sorted by name. */
    fun launchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { InstalledApp(it, label(it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(packageName: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    /**
     * A starting category from the app's own Play category (spec F08): social, video, games and
     * news count as distracting, productivity apps as productive, everything else as neutral.
     * The user can change any app.
     */
    fun defaultCategory(packageName: String): AppCategory = categoryCache.getOrPut(packageName) {
        val category = try {
            pm.getApplicationInfo(packageName, 0).category
        } catch (_: PackageManager.NameNotFoundException) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        when (category) {
            ApplicationInfo.CATEGORY_SOCIAL,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.DISTRACTING
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVE
            else -> AppCategory.NEUTRAL
        }
    }

    /** Every installed home-screen app. */
    fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    /**
     * Apps that are never blocked, so the phone stays usable for calls, emergencies and typing:
     * Arcify, system UI, the default dialer and SMS apps, telephony and emergency apps, keyboards.
     */
    fun alwaysAllowedPackages(): Set<String> {
        val allowed = mutableSetOf(context.packageName, "android", "com.android.systemui")
        allowed += EMERGENCY_PACKAGES
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { allowed += it }
        Telephony.Sms.getDefaultSmsPackage(context)?.let { allowed += it }
        context.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.forEach { allowed += it.packageName }
        return allowed
    }

    /**
     * Whether a window event came from a real app screen rather than a dialog, toast, keyboard or
     * system overlay. Only screens change which app counts as "open".
     */
    fun isActivity(packageName: String, className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        val key = "$packageName/$className"
        return activityCache.getOrPut(key) {
            try {
                pm.getActivityInfo(ComponentName(packageName, className), 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private companion object {
        val EMERGENCY_PACKAGES = setOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
        )
    }
}
