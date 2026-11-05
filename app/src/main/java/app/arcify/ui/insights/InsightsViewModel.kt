package app.arcify.ui.insights

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.arcify.R
import app.arcify.blocker.AppCatalog
import app.arcify.blocker.UsageHistorySync
import app.arcify.blocker.UsageReader
import app.arcify.core.data.BlockRepository
import app.arcify.core.data.InsightsRepository
import app.arcify.core.data.TimerSettingsRepository
import app.arcify.core.insights.AppCategory
import app.arcify.core.insights.CategorySplit
import app.arcify.core.insights.InsightRules
import app.arcify.core.insights.Streak
import app.arcify.core.insights.StreakRules
import app.arcify.core.timer.MINUTE_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TopApp(val packageName: String, val label: String, val ms: Long, val category: AppCategory)

data class DayBar(val date: LocalDate, val split: CategorySplit, val focusMs: Long)

data class StreakRow(@param:StringRes val label: Int, val streak: Streak)

data class InsightsUiState(
    val hasUsageAccess: Boolean = true,
    val today: CategorySplit = CategorySplit(),
    val unlocksToday: Int? = null,
    val focusTodayMs: Long = 0,
    val goalMs: Long = 0,
    val shortFormToday: Int = 0,
    val topApps: List<TopApp> = emptyList(),
    val week: List<DayBar> = emptyList(),
    val streaks: List<StreakRow> = emptyList(),
)

/** Backs the Insights tab (spec F08) and its streaks (F03/F09/F10). */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val insights: InsightsRepository,
    settings: TimerSettingsRepository,
    blocks: BlockRepository,
    private val catalog: AppCatalog,
    private val usageReader: UsageReader,
    private val sync: UsageHistorySync,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val historyFrom: LocalDate = today.minusDays(HISTORY_DAYS)
    private val historyFromKey = InsightRules.dayKey(historyFrom, zone)

    init {
        // Today's numbers come from Android's usage events; refresh them while the screen is open.
        viewModelScope.launch {
            sync.sync(days = 7)
            while (isActive) {
                delay(60_000)
                sync.sync(days = 1)
            }
        }
    }

    private val labels = flow {
        emit(emptyMap())
        emit(catalog.launchableApps().associate { it.packageName to it.label })
    }.flowOn(Dispatchers.IO)

    private val usageInputs = combine(
        insights.observeUsageSince(historyFromKey),
        insights.observeCategories(),
        insights.observeUnlocksSince(historyFromKey),
        insights.observeFirstUsageDay(),
    ) { usage, categories, unlocks, first -> UsageInputs(usage.map { Triple(it.day, it.packageName, it.foregroundMs) }, categories, unlocks, first) }

    private val otherInputs = combine(
        insights.observeFocusSince(historyFromKey),
        insights.observeShortFormDaysSince(historyFromKey),
        settings.dailyGoalMinutes,
        blocks.observeLimits(),
    ) { focus, shortForm, goal, limits ->
        OtherInputs(
            focus = focus.map { it.startedAt to it.focusedMs },
            shortFormByDay = shortForm,
            goalMs = goal * MINUTE_MS,
            limits = limits.filter { it.enabled }.associate { it.packageName to it.dailyLimitMs },
        )
    }

    val ui: StateFlow<InsightsUiState> = combine(usageInputs, otherInputs, labels) { u, o, names ->
        build(u, o, names)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    /** Cycles an app's category: productive → neutral → distracting → productive. */
    fun cycleCategory(app: TopApp) = viewModelScope.launch {
        val next = when (app.category) {
            AppCategory.PRODUCTIVE -> AppCategory.NEUTRAL
            AppCategory.NEUTRAL -> AppCategory.DISTRACTING
            AppCategory.DISTRACTING -> AppCategory.PRODUCTIVE
        }
        insights.setCategory(app.packageName, if (next == catalog.defaultCategory(app.packageName)) null else next)
    }

    private data class UsageInputs(
        val rows: List<Triple<Long, String, Long>>,
        val categories: Map<String, AppCategory>,
        val unlocks: Map<Long, Int>,
        val firstDay: Long?,
    )

    private data class OtherInputs(
        val focus: List<Pair<Long, Long>>,
        val shortFormByDay: Map<Long, Int>,
        val goalMs: Long,
        val limits: Map<String, Long>,
    )

    private fun build(u: UsageInputs, o: OtherInputs, names: Map<String, String>): InsightsUiState {
        val categoryOf = { pkg: String -> u.categories[pkg] ?: catalog.defaultCategory(pkg) }
        val usageByDay: Map<LocalDate, Map<String, Long>> = u.rows
            .groupBy { InsightRules.dateOf(it.first, zone) }
            .mapValues { (_, rows) -> rows.associate { it.second to it.third } }
        val focusByDay = InsightRules.focusByDay(o.focus, zone).mapKeys { InsightRules.dateOf(it.key, zone) }
        val shortFormByDay = o.shortFormByDay.mapKeys { InsightRules.dateOf(it.key, zone) }
        val todayUsage = usageByDay[today].orEmpty()

        val firstUsage = u.firstDay?.let { InsightRules.dateOf(it, zone) }
        val firstFocus = focusByDay.keys.minOrNull()
        val firstAny = listOfNotNull(firstUsage, firstFocus).minOrNull() ?: today

        val streaks = buildList {
            val focusDays = focusByDay.filterValues { it >= o.goalMs }.keys
            add(StreakRow(R.string.streak_focus, StreakRules.streak(focusDays, today, firstAny)))
            if (o.limits.isNotEmpty() && firstUsage != null) {
                add(StreakRow(R.string.streak_limits, StreakRules.streak(StreakRules.limitDays(usageByDay, o.limits), today, firstUsage)))
            }
            if (firstUsage != null) {
                val shortsFree = usageByDay.keys.filter { (shortFormByDay[it] ?: 0) == 0 }.toSet()
                add(StreakRow(R.string.streak_shorts_free, StreakRules.streak(shortsFree, today, firstUsage)))
            }
        }

        return InsightsUiState(
            hasUsageAccess = usageReader.hasAccess(),
            today = InsightRules.split(todayUsage, categoryOf),
            unlocksToday = u.unlocks[InsightRules.dayKey(today, zone)],
            focusTodayMs = focusByDay[today] ?: 0L,
            goalMs = o.goalMs,
            shortFormToday = shortFormByDay[today] ?: 0,
            topApps = todayUsage.entries
                .sortedByDescending { it.value }
                .take(TOP_APPS)
                .map { (pkg, ms) -> TopApp(pkg, names[pkg] ?: catalog.label(pkg), ms, categoryOf(pkg)) },
            week = (6 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                DayBar(date, InsightRules.split(usageByDay[date].orEmpty(), categoryOf), focusByDay[date] ?: 0L)
            },
            streaks = streaks,
        )
    }

    private companion object {
        const val HISTORY_DAYS = 90L
        const val TOP_APPS = 8
    }
}
