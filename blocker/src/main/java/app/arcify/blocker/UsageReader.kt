package app.arcify.blocker

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import app.arcify.core.timer.DayBounds
import app.arcify.core.timer.WallClock
import app.arcify.core.usage.AppEvent
import app.arcify.core.usage.AppUsage
import app.arcify.core.usage.UsageAggregator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Per-app usage and unlocks for one window. [unlocks] is null where Android can't report it (before 9). */
data class WindowUsage(val apps: Map<String, AppUsage>, val unlocks: Int?)

/** Foreground time per app, from Android's usage events. Call off the main thread. */
@Singleton
class UsageReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: WallClock,
) {
    // Both app-op check calls are marked deprecated in the API 36 SDK, and no single
    // replacement covers minSdk 26 to 36. They still work and are the documented way to check this.
    @Suppress("DEPRECATION")
    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    fun usedTodayMs(packageName: String): Long = usedToday()[packageName] ?: 0L

    /** Foreground time per package since local midnight. Empty without usage access. */
    fun usedToday(): Map<String, Long> {
        val now = clock.nowMs()
        val day = DayBounds.of(now, ZoneId.systemDefault())
        return usage(day.startMs, now).apps.mapValues { it.value.foregroundMs }.filterValues { it > 0 }
    }

    /** Usage and unlocks between [fromMs] and [toMs]. Empty without usage access. */
    fun usage(fromMs: Long, toMs: Long): WindowUsage {
        if (!hasAccess()) return WindowUsage(emptyMap(), null)
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return WindowUsage(emptyMap(), null)
        // Read a little earlier so an app already open at the window start is known to be open.
        val raw = usm.queryEvents(fromMs - LOOKBACK_MS, toMs)
        val events = ArrayList<AppEvent>()
        var unlocks = 0
        val event = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(event)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN &&
                event.timeStamp >= fromMs
            ) {
                unlocks++
                continue
            }
            @Suppress("DEPRECATION") // Same values as ACTIVITY_RESUMED/PAUSED (API 29+); minSdk is 26.
            val type = when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> AppEvent.Type.FOREGROUND
                UsageEvents.Event.MOVE_TO_BACKGROUND -> AppEvent.Type.BACKGROUND
                else -> null
            } ?: continue
            events += AppEvent(event.packageName, event.className, type, event.timeStamp)
        }
        val apps = UsageAggregator.stats(events, fromMs, toMs).filterValues { it.foregroundMs > 0 || it.launches > 0 }
        return WindowUsage(apps, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) unlocks else null)
    }

    private companion object {
        const val LOOKBACK_MS = 2 * 60 * 60 * 1000L
    }
}
