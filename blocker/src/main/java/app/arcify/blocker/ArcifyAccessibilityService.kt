package app.arcify.blocker

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import app.arcify.blocker.content.ContentGuard
import app.arcify.blocker.content.ContentState
import app.arcify.blocker.content.NodeSnapshots
import app.arcify.blocker.content.ShortFormStore
import app.arcify.blocker.content.SnapshotRecorder
import app.arcify.core.block.AppLimit
import app.arcify.core.block.BlockDecision
import app.arcify.core.block.BlockPolicy
import app.arcify.core.block.LimitRules
import app.arcify.core.block.LimitStatus
import app.arcify.core.content.ContentDefaults
import app.arcify.core.content.Platform
import app.arcify.core.content.ShortFormSighting
import app.arcify.core.data.BlockRepository
import app.arcify.core.data.ContentRepository
import app.arcify.core.timer.DayBounds
import app.arcify.core.timer.TimerController
import dagger.hilt.android.AndroidEntryPoint
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The blocking engine's entry point (spec: blocking-engine).
 * - App level: tracks which app is in front and applies [BlockPolicy] (limits F03, strict focus F02).
 * - Content level: for monitored apps and browsers, snapshots the screen and lets
 *   [ContentGuard] apply Shorts/Reels rules, counters, study modes and website rules (F04–F06).
 *
 * All decisions are made by pure code in `:core` and `content`; this class gathers inputs and acts.
 */
@AndroidEntryPoint
class ArcifyAccessibilityService : AccessibilityService() {

    @Inject lateinit var blockRepository: BlockRepository
    @Inject lateinit var contentRepository: ContentRepository
    @Inject lateinit var timerController: TimerController
    @Inject lateinit var usageReader: UsageReader
    @Inject lateinit var appCatalog: AppCatalog
    @Inject lateinit var screens: BlockScreenLauncher
    @Inject lateinit var recorder: SnapshotRecorder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var limits: Map<String, AppLimit> = emptyMap()
    private var allowedApps: Set<String> = emptySet()
    private var alwaysAllowed: Set<String> = emptySet()
    private var launchers: Set<String> = emptySet()
    private var foreground: String? = null
    private var evaluation: Job? = null
    private val remindedToday = HashSet<String>()

    private var contentState = ContentState()
    private var recorderOn = false
    private var contentJob: Job? = null
    private var lastContentCheck = 0L
    private val guard: ContentGuard by lazy {
        ContentGuard(
            object : ShortFormStore {
                override suspend fun countsToday(platform: Platform) = contentRepository.countsToday(platform)
                override suspend fun record(sighting: ShortFormSighting) = contentRepository.record(sighting)
            },
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshSpecialApps()
        scope.launch {
            blockRepository.observeLimits().collect { list ->
                limits = list.associateBy { it.packageName }
                evaluate()
            }
        }
        scope.launch {
            blockRepository.observeAllowedApps().collect {
                allowedApps = it
                evaluate()
            }
        }
        scope.launch {
            combine(
                contentRepository.observeShortFormRules(),
                contentRepository.observeWebsiteRules(),
                contentRepository.observeStudyChannels().map { items -> items.map { it.channel } },
                contentRepository.studySettings,
                contentRepository.observeBrowsers(),
            ) { shortForm, websites, channels, settings, browsers ->
                ContentState(shortForm, websites, channels, settings, browsers)
            }.collect {
                contentState = it
                evaluate()
            }
        }
        scope.launch { contentRepository.recorderEnabled.collect { recorderOn = it } }
        scope.launch {
            // Re-check the moment a strict focus phase starts or ends.
            timerController.timer
                .map { BlockPolicy.focusLock(it, emptySet()) != null }
                .distinctUntilChanged()
                .collect { evaluate() }
        }
        scope.launch {
            // Limits can run out while the user stays in one app, so check periodically too.
            while (isActive) {
                delay(RECHECK_MS)
                refreshSpecialApps()
                evaluate()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            (pkg in launchers || appCatalog.isActivity(pkg, event.className?.toString())) &&
            pkg != foreground
        ) {
            foreground = pkg
            evaluate()
        }
        if (pkg == foreground && guard.monitors(pkg, contentState)) scheduleContentCheck(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshSpecialApps() {
        launchers = appCatalog.homePackages()
        alwaysAllowed = appCatalog.alwaysAllowedPackages()
    }

    // ---- App level ----

    private fun evaluate() {
        val pkg = foreground ?: return
        evaluation?.cancel()
        evaluation = scope.launch {
            val limit = limits[pkg]?.takeIf { it.enabled }
            val usedMs = if (limit != null) withContext(Dispatchers.IO) { usageReader.usedTodayMs(pkg) } else 0L
            val focus = BlockPolicy.focusLock(timerController.timer.value, allowedApps + studyModeApps())
            when (val decision = BlockPolicy.decide(pkg, alwaysAllowed, launchers, focus, limit, usedMs)) {
                BlockDecision.Allow -> if (limit != null) remindIfClose(limit, usedMs)
                BlockDecision.ReturnToFocus -> screens.returnToFocus()
                is BlockDecision.StrictFocus, is BlockDecision.LimitReached -> screens.showBlock(decision)
            }
        }
    }

    /** With a study mode on, YouTube or the browsers stay usable during strict focus; their content is policed instead. */
    private fun studyModeApps(): Set<String> = buildSet {
        if (contentState.settings.youtubeStudyMode) add(ContentDefaults.YOUTUBE_PACKAGE)
        if (contentState.settings.browserStudyMode) addAll(contentState.browsers.map { it.packageName })
    }

    private fun remindIfClose(limit: AppLimit, usedMs: Long) {
        if (LimitRules.status(limit, usedMs) != LimitStatus.REMINDER) return
        val today = DayBounds.of(timerController.now(), ZoneId.systemDefault()).startMs
        if (remindedToday.add("${limit.packageName}@$today")) {
            screens.limitReminder(limit.packageName, usedMs, limit.dailyLimitMs)
        }
    }

    // ---- Content level ----

    /** Throttles screen inspection to one per [CONTENT_INTERVAL_MS], always running the latest. */
    private fun scheduleContentCheck(pkg: String) {
        if (contentJob?.isActive == true) return
        contentJob = scope.launch {
            val wait = CONTENT_INTERVAL_MS - (SystemClock.uptimeMillis() - lastContentCheck)
            if (wait > 0) delay(wait)
            lastContentCheck = SystemClock.uptimeMillis()
            inspectContent(pkg)
        }
    }

    private suspend fun inspectContent(pkg: String) {
        if (pkg != foreground) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val snapshot = NodeSnapshots.capture(root)
        val now = timerController.now()
        if (recorderOn) withContext(Dispatchers.IO) { recorder.save(pkg, snapshot, now) }
        val metrics = resources.displayMetrics
        val screen = Bounds(0, 0, metrics.widthPixels, metrics.heightPixels)
        val state = contentState.copy(timer = timerController.timer.value)
        val block = guard.inspect(pkg, snapshot, screen, now, state) ?: return
        performGlobalAction(GLOBAL_ACTION_BACK)
        screens.showContentBlock(block, pkg)
    }

    private companion object {
        const val RECHECK_MS = 15_000L
        const val CONTENT_INTERVAL_MS = 300L
    }
}
