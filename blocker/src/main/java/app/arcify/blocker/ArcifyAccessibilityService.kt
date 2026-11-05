package app.arcify.blocker

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import app.arcify.blocker.content.BlockBanner
import app.arcify.blocker.content.ContentBlock
import app.arcify.blocker.content.ContentGuard
import app.arcify.core.content.IgSurface
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
import app.arcify.blocker.protection.Threat
import kotlinx.coroutines.CoroutineExceptionHandler
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

    // A failure in one check is logged and must never take the whole service down.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> Diag.error("engine error", e) },
    )
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
    private val cooldown = ActionCooldown()
    private val banner: BlockBanner by lazy { BlockBanner(this) }
    private var lastNote: String? = null
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
        Diag.log("service connected; launchers=$launchers")
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
            refreshForeground()
            scheduleWindowCheck()
            return
        }
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) refreshForeground()
        // Content is checked for whichever monitored app owns the active window; the
        // active-window root is verified again before inspecting.
        if (needsScreenCheck(pkg)) scheduleScreenCheck(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        banner.remove()
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshSpecialApps() {
        launchers = appCatalog.homePackages()
        alwaysAllowed = appCatalog.alwaysAllowedPackages()
    }

    // ---- App level ----

    /**
     * The app in front is whoever owns the *active application window*. Asking the system is
     * reliable for every app; guessing from each app's own window events was not (some apps never
     * report their screens as activities, so Arcify kept thinking the home screen was open).
     * Keyboards, the notification shade and system overlays are not application windows, so they
     * never count.
     */
    private fun activeAppPackage(): String? {
        val active = windows.firstOrNull { it.isActive }
        if (active != null && active.type != AccessibilityWindowInfo.TYPE_APPLICATION) return null
        return (active?.root ?: rootInActiveWindow)?.packageName?.toString()
    }

    private fun refreshForeground() {
        val pkg = activeAppPackage() ?: return
        if (pkg == foreground) return
        Diag.log("foreground $foreground -> $pkg")
        foreground = pkg
        evaluate()
    }

    private fun evaluate() {
        val pkg = foreground ?: return
        evaluation?.cancel()
        evaluation = scope.launch {
            val (decision, limit, usedMs) = decide(pkg)
            if (decision == BlockDecision.Allow) {
                if (limit != null) remindIfClose(limit, usedMs)
                return@launch
            }
            // Never act on a stale guess: the app must still own the active window right now.
            val active = activeAppPackage()
            if (active != null && active != pkg) {
                Diag.log("not acting on $pkg: $active is in front")
                refreshForeground()
                return@launch
            }
            // Periodic re-checks and flow updates must not repeat an action that is still taking effect.
            if (!cooldown.tryAct("app:$pkg:${decision::class.simpleName}", SystemClock.uptimeMillis())) return@launch
            Diag.log("decision for $pkg: $decision")
            when (decision) {
                BlockDecision.Allow -> Unit
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
        val decision = BlockPolicy.decide(
            foreground = pkg,
            alwaysAllowed = alwaysAllowed,
            launcherPackages = launchers,
            focus = focus,
            limit = limit,
            usedMs = usedMs,
            userFacing = appCatalog.isUserFacing(pkg),
        )
        return Verdict(decision, limit, usedMs)
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
                if (!cooldown.tryAct("window:$pkg", SystemClock.uptimeMillis())) return
                Diag.log("second window $pkg: $decision")
                performGlobalAction(GLOBAL_ACTION_HOME)
                screens.showBlock(decision)
                return
            }
        }
    }

    // ---- Screen content ----

    /** Arcify's own screens are never inspected: they talk about uninstalling and blocking all the time. */
    private fun needsScreenCheck(pkg: String): Boolean =
        pkg != packageName &&
            !cooldown.isCooling(pkg, SystemClock.uptimeMillis()) &&
            (guard.monitors(pkg, contentState) || (protectionActive && guardsForProtection(pkg)))

    /** Installer, settings and store screens, plus home-screen apps (their own uninstall dialogs). */
    private fun guardsForProtection(pkg: String): Boolean =
        pkg in launchers || ProtectionDetector.guards(pkg, ProtectionDetector.DEFAULT_HINTS)

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
        val root = rootInActiveWindow
        val rootPkg = root?.packageName?.toString()
        if (root == null || rootPkg != pkg) {
            Diag.log("skip $pkg: active window is ${rootPkg ?: "none"}")
            return
        }
        val snapshot = NodeSnapshots.capture(root)
        val now = timerController.now()

        if (protectionActive && guardsForProtection(pkg)) {
            val threat = ProtectionDetector.detect(
                pkg,
                snapshot,
                appLabel,
                serviceLabel,
                ProtectionDetector.DEFAULT_HINTS,
                launcherPackages = launchers,
            )
            if (threat != null) {
                cooldown.start(pkg, SystemClock.uptimeMillis())
                Diag.log("protection: $threat in $pkg")
                // Back cancels an uninstall dialog; Home leaves a settings or store page.
                performGlobalAction(if (threat == Threat.UNINSTALL) GLOBAL_ACTION_BACK else GLOBAL_ACTION_HOME)
                screens.showProtection(threat)
                return
            }
            if (!guard.monitors(pkg, contentState)) return
        }

        if (recorderOn) withContext(Dispatchers.IO) { recorder.save(pkg, snapshot, now) }
        val metrics = resources.displayMetrics
        val screen = Bounds(0, 0, metrics.widthPixels, metrics.heightPixels)
        val state = contentState.copy(timer = timerController.timer.value)
        val block = guard.inspect(pkg, snapshot, screen, now, state)
        val note = guard.lastNote
        if (note != null && note != lastNote) {
            Diag.log("$pkg: $note")
            lastNote = note
        }
        if (block == null) return
        // One action per block: the events that follow while it takes effect are ignored for a moment.
        cooldown.start(pkg, SystemClock.uptimeMillis())
        Diag.log("content block in $pkg: $block")
        leaveBlockedContent(root, block)
        banner.show(block)
    }

    /**
     * Leaves the blocked reel, Short or page while staying inside the app. On Instagram's Reels
     * tab, tapping the Home tab is gentler than Back; everywhere else Back returns to where the
     * user came from.
     */
    private fun leaveBlockedContent(root: android.view.accessibility.AccessibilityNodeInfo, block: ContentBlock) {
        if (block is ContentBlock.ShortForm && block.platform == Platform.INSTAGRAM && block.surface == IgSurface.REELS_TAB) {
            val home = root.findAccessibilityNodeInfosByViewId(INSTAGRAM_HOME_TAB).firstOrNull()
            if (home != null && home.isVisibleToUser && home.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private companion object {
        const val INSTAGRAM_HOME_TAB = "com.instagram.android:id/feed_tab"
        const val RECHECK_MS = 15_000L
        const val SCREEN_INTERVAL_MS = 300L
        const val WINDOW_CHECK_DELAY_MS = 400L
    }
}
