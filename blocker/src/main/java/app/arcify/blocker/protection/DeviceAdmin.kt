package app.arcify.blocker.protection

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.arcify.blocker.R

/**
 * Optional device admin (spec F07). Arcify asks only for the force-lock policy and never uses
 * it. Being an active admin just means Android won't uninstall Arcify until admin is switched off.
 */
class ArcifyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.device_admin_disable_warning)
}

object DeviceAdmin {
    fun component(context: Context) = ComponentName(context, ArcifyDeviceAdminReceiver::class.java)

    fun isActive(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(component(context)) == true

    /** Opens the system screen that asks the user to activate Arcify as device admin. */
    fun activationIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.device_admin_explanation))

    fun deactivate(context: Context) {
        context.getSystemService(DevicePolicyManager::class.java)?.removeActiveAdmin(component(context))
    }
}
