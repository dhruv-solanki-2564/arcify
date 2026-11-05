package app.arcify.blocker

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import app.arcify.blocker.content.ContentGuard
import app.arcify.blocker.content.ContentState
import app.arcify.blocker.content.NodeSnapshots
import app.arcify.blocker.content.ShortFormStore
import app.arcify.blocker.content.SnapshotRecorder
import app.arcify.blocker.protection.ProtectionDetector
import app.arcify.core.block.AppLimit
import app.arcify.core.block.BlockDecision
import app.arcify.core.block.BlockPolicy
import app.arcify.core.block.LimitRules
import app.arcify.core.block.LimitStatus
import app.arcify.core.content.ContentDefaults
import app.arcify.core.content.Platform
import app.arcify.core.content.ShortFormSighting
import app.arcify.core.data.BlockRepository
import app.arcify.core.data.CommitmentTracker
import app.arcify.core.data.ContentRepository
import app.arcify.core.data.ProtectionRepository
import app.arcify.core.protection.ProtectionRules
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
 *   Every app window on screen is checked, so split screen and floating windows can't get around it (F07).
 * - Content level: for monitored apps and browsers, snapshots the screen and lets
 *   [ContentGuard] apply Shorts/Reels rules, counters, study modes and website rules (F04–F06).
 * - Protection: while it is active, screens that would disable or uninstall Arcify are closed (F07).
 *
 * All decisions are made by pure code in `:core`, `content` and `protection`; this class gathers inputs and acts.
 */
@AndroidEntryPoint
class ArcifyAccessibilityService : AccessibilityService() {

    @Inject lateinit var blockRepository: BlockRepository
    @Inject lateinit var contentRepository: ContentRepository
    @Inject lateinit var protectionRepository: ProtectionRepository
    @Inject lateinit var commitmentTracker: CommitmentTracker
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
    private var protectionActive = false
    private var screenJob: Job? = null
    private var lastScreenCheck = 0L
    private var windowJob: Job? = null
    private val appLabel: String by lazy { applicationInfo.loadLabel(packageManager).toString() }
    private val serviceLabel: String by lazy { getString(R.string.accessibility_service_label) }
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
            combine(protectionRepository.settings, commitmentTracker.commitments) { settings, commitments ->
                ProtectionRules.isActive(settings.level, commitments)
            }.collect { protectionActive = it }
        }
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
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            scheduleWindowCheck()
            return
        }
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            (pkg in launchers || appCatalog.isActivity(pkg, event.className?.toString())) &&
            pkg != foreground
        ) {
            foreground = pkg
            evaluate()
        }
        if (pkg == foreground && needsScreenCheck(pkg)) scheduleScreenCheck(pkg)
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
            val (decision, limit, usedMs) = decide(pkg)
            when (decision) {
                BlockDecision.Allow -> if (limit != null) remindIfClose(limit, usedMs)
                BlockDecision.ReturnToFocus -> screens.returnToFocus()
                is BlockDecision.StrictFocus, is BlockDecision.LimitReached -> screens.showBlock(decision)
            }
        }
    }

    private data class Verdict(val decision: BlockDecision, val limit: AppLimit?, val usedMs: Long)

    private suspend fun decide(pkg: String): Verdict {
        val limit = limits[pkg]?.takeIf { it.enabled }
        val usedMs = if (limit != null) withContext(Dispatchers.IO) { usageReader.usedTodayMs(pkg) } else 0L
        val focus = BlockPolicy.focusLock(timerController.timer.value, allowedApps + studyModeApps())
        return Verdict(BlockPolicy.decide(pkg, alwaysAllowed, launchers, focus, limit, usedMs), limit, usedMs)
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

    // ---- Other windows (split screen, floating windows) ----

    private fun scheduleWindowCheck() {
        if (windowJob?.isActive == true) return
        windowJob = scope.launch {
            delay(WINDOW_CHECK_DELAY_MS)
            checkOtherWindows()
        }
    }

    /** A blocked app in a second window is treated as if it were in front. */
    private suspend fun checkOtherWindows() {
        val packages = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root?.packageName?.toString() }
            .distinct()
        if (packages.size < 2) return
        for (pkg in packages) {
            if (pkg == foreground || pkg in alwaysAllowed || pkg in launchers) continue
            val decision = decide(pkg).decision
            if (decision is BlockDecision.StrictFocus || decision is BlockDecision.LimitReached) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                screens.showBlock(decision)
                return
            }
        }
    }

    // ---- Screen content ----

    private fun needsScreenCheck(pkg: String): Boolean =
        guard.monitors(pkg, contentState) ||
            (protectionActive && ProtectionDetector.guards(pkg, ProtectionDetector.DEFAULT_HINTS))

    /** Throttles screen inspection to one per [SCREEN_INTERVAL_MS], always running the latest. */
    private fun scheduleScreenCheck(pkg: String) {
        if (screenJob?.isActive == true) return
        screenJob = scope.launch {
            val wait = SCREEN_INTERVAL_MS - (SystemClock.uptimeMillis() - lastScreenCheck)
            if (wait > 0) delay(wait)
            lastScreenCheck = SystemClock.uptimeMillis()
            inspectScreen(pkg)
        }
    }

    private suspend fun inspectScreen(pkg: String) {
        if (pkg != foreground) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val snapshot = NodeSnapshots.capture(root)
        val now = timerController.now()

        if (protectionActive && ProtectionDetector.guards(pkg, ProtectionDetector.DEFAULT_HINTS)) {
            val threat = ProtectionDetector.detect(pkg, snapshot, appLabel, serviceLabel, ProtectionDetector.DEFAULT_HINTS)
            if (threat != null) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                screens.showProtection(threat)
            }
            return
        }

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
        const val SCREEN_INTERVAL_MS = 300L
        const val WINDOW_CHECK_DELAY_MS = 400L
    }
}
