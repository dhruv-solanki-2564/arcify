package app.arcify.blocker.content

import app.arcify.core.content.ContentDefaults
import app.arcify.core.content.IgSurface
import app.arcify.core.content.Platform
import app.arcify.core.content.ShortFormCounts
import app.arcify.core.content.ShortFormMode
import app.arcify.core.content.ShortFormRule
import app.arcify.core.content.ShortFormSighting
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Developer tool: replays screens saved by the debug SnapshotRecorder through [ContentGuard] in
 * time order and prints each decision. Recordings stay on the developer's machine (they come
 * from a real phone) and are never committed.
 *
 * Run: `ARCIFY_RECORDINGS=<folder> gradlew :blocker:testDebugUnitTest --tests '*RecordedScreensTest*' -i`
 */
class RecordedScreensTest {

    @Test
    fun replayRecordings() = runTest {
        val dir = System.getenv("ARCIFY_RECORDINGS")?.let(::File)
        assumeTrue("set ARCIFY_RECORDINGS to a folder of recordings", dir?.isDirectory == true)

        val store = object : ShortFormStore {
            override suspend fun countsToday(platform: Platform) = ShortFormCounts { _, _ -> 0 }
            override suspend fun record(sighting: ShortFormSighting) = Unit
        }
        val guard = ContentGuard(store)
        // The phone test's intent: block Instagram reels, but allow reels friends send in DMs.
        val state = ContentState(
            browsers = ContentDefaults.browsers,
            shortFormRules = listOf(
                ShortFormRule(Platform.INSTAGRAM, null, ShortFormMode.BLOCK),
                ShortFormRule(Platform.INSTAGRAM, IgSurface.DM, ShortFormMode.ALLOW),
            ),
        )
        val files = dir!!.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name.substringAfterLast('-') }
        for (file in files) {
            val pkg = file.name.substringBeforeLast('-')
            val root = SnapshotJson.fromJson(JSONObject(file.readText()))
            val decision = guard.inspect(pkg, root, root.bounds, file.name.substringAfterLast('-').removeSuffix(".json").toLong(), state)
            println("${file.name}: ${decision ?: "allowed / not a reel"}")
        }
    }
}
