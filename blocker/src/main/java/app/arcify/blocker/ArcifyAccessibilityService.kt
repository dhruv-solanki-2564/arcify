package app.arcify.blocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import app.arcify.core.block.AppLimit
import app.arcify.core.block.BlockDecision
import app.arcify.core.block.BlockPolicy
import app.arcify.core.block.LimitRules
import app.arcify.core.block.LimitStatus
import app.arcify.core.data.BlockRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The blocking engine's entry point (spec: blocking-engine). Tracks which app is in front and
 * applies [BlockPolicy]: daily limits (F03) and strict focus (F02).
 *
 * All decisions are made by pure code in `:core`; this class only gathers inputs and acts.
 */
@AndroidEntryPoint
class ArcifyAccessibilityService : AccessibilityService() {

    @Inject lateinit var blockRepository: BlockRepository
    @Inject lateinit var timerController: TimerController
    @Inject lateinit var usageReader: UsageReader
    @Inject lateinit var appCatalog: AppCatalog
    @Inject lateinit var screens: BlockScreenLauncher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var limits: Map<String, AppLimit> = emptyMap()
    private var allowedApps: Set<String> = emptySet()
    private var alwaysAllowed: Set<String> = emptySet()
    private var launchers: Set<String> = emptySet()
    private var foreground: String? = null
    private var evaluation: Job? = null
    private val remindedToday = HashSet<String>()

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
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in launchers && !appCatalog.isActivity(pkg, event.className?.toString())) return
        if (pkg == foreground) return
        foreground = pkg
        evaluate()
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

    private fun evaluate() {
        val pkg = foreground ?: return
        evaluation?.cancel()
        evaluation = scope.launch {
            val limit = limits[pkg]?.takeIf { it.enabled }
            val usedMs = if (limit != null) withContext(Dispatchers.IO) { usageReader.usedTodayMs(pkg) } else 0L
            val focus = BlockPolicy.focusLock(timerController.timer.value, allowedApps)
            when (val decision = BlockPolicy.decide(pkg, alwaysAllowed, launchers, focus, limit, usedMs)) {
                BlockDecision.Allow -> if (limit != null) remindIfClose(limit, usedMs)
                BlockDecision.ReturnToFocus -> screens.returnToFocus()
                is BlockDecision.StrictFocus, is BlockDecision.LimitReached -> screens.showBlock(decision)
            }
        }
    }

    private fun remindIfClose(limit: AppLimit, usedMs: Long) {
        if (LimitRules.status(limit, usedMs) != LimitStatus.REMINDER) return
        val today = DayBounds.of(timerController.now(), ZoneId.systemDefault()).startMs
        if (remindedToday.add("${limit.packageName}@$today")) {
            screens.limitReminder(limit.packageName, usedMs, limit.dailyLimitMs)
        }
    }

    private companion object {
        const val RECHECK_MS = 15_000L
    }
}
