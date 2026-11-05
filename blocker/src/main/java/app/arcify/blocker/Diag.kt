package app.arcify.blocker

import android.util.Log

/**
 * Engine diagnostics, readable with `adb logcat -s Arcify`. Logs package names and decisions
 * only, never screen text.
 */
object Diag {
    private const val TAG = "Arcify"

    fun log(message: String) {
        Log.i(TAG, message)
    }

    fun error(message: String, e: Throwable) {
        Log.e(TAG, message, e)
    }
}
