package app.arcify.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import app.arcify.blocker.InstalledApp

/**
 * Optional home-screen hiding (spec F02). Arcify's home component ships *disabled*, so installing
 * Arcify never makes Android ask which home app to use. It is enabled only when the user opts in.
 */
object HomeSetup {

    private fun proxyComponent(context: Context) = ComponentName(context, ProxyHomeActivity::class.java)

    fun setProxyEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            proxyComponent(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Whether pressing Home currently opens Arcify. */
    fun isArcifyDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** Every other home app installed, by name. */
    fun otherLaunchers(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(home, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { InstalledApp(it.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /** Starts the given launcher's home screen. Returns false if it can't be found. */
    fun startLauncher(context: Context, packageName: String): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val activity = context.packageManager.queryIntentActivities(home, 0)
            .firstOrNull { it.activityInfo.packageName == packageName }
            ?.activityInfo ?: return false
        return try {
            context.startActivity(
                Intent(home)
                    .setComponent(ComponentName(activity.packageName, activity.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /** The system "Default home app" screen. */
    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
