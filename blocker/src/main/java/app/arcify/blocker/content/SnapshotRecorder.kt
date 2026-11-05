package app.arcify.blocker.content

import android.content.Context
import android.content.pm.ApplicationInfo
import app.arcify.blocker.NodeSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only calibration aid. When switched on, it saves redacted screen trees of monitored
 * apps into the app's private storage, so detectors can be checked against real screens.
 *
 * Files: `files/snapshots/<package>-<time>.json`, at most one per app every 2 s and the newest
 * [MAX_FILES] kept. Pull with:
 * `adb exec-out run-as app.arcify.debug tar c files/snapshots > snapshots.tar`
 */
@Singleton
class SnapshotRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val lastSaved = HashMap<String, Long>()

    /** Release builds never record. */
    val isAvailable: Boolean
        get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun save(packageName: String, snapshot: NodeSnapshot, now: Long) {
        if (!isAvailable) return
        if (now - (lastSaved[packageName] ?: 0L) < MIN_INTERVAL_MS) return
        lastSaved[packageName] = now
        val dir = File(context.filesDir, "snapshots").apply { mkdirs() }
        File(dir, "$packageName-$now.json").writeText(SnapshotJson.toJson(snapshot, redactText = true).toString())
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 2_000L
        const val MAX_FILES = 80
    }
}
