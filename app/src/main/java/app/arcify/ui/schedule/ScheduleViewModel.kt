package app.arcify.ui.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.arcify.core.data.InsightsRepository
import app.arcify.core.data.ScheduleRepository
import app.arcify.core.data.TimerSettingsRepository
import app.arcify.core.schedule.Occurrence
import app.arcify.core.schedule.OccurrenceStatus
import app.arcify.core.schedule.ScheduleRules
import app.arcify.core.schedule.ScheduledSession
import app.arcify.core.timer.WallClock
import app.arcify.schedule.ScheduleAlarms
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlanItem(val occurrence: Occurrence, val status: OccurrenceStatus)

data class DayPlan(val date: LocalDate, val items: List<PlanItem>)

data class ScheduleUiState(
    val week: List<DayPlan> = emptyList(),
    val sessions: List<ScheduledSession> = emptyList(),
    val goalMinutes: Int = TimerSettingsRepository.DEFAULT_GOAL_MIN,
    val nudgesEnabled: Boolean = true,
    val today: LocalDate = LocalDate.now(),
)

/** Backs the Schedule tab (spec F09), the daily goal and the nudges switch (F10). */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ScheduleRepository,
    insights: InsightsRepository,
    private val settings: TimerSettingsRepository,
    private val clock: WallClock,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val weekStart: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val weekStartMs = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()

    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    val ui: StateFlow<ScheduleUiState> = combine(
        repository.observe(),
        insights.observeFocusSince(weekStartMs - DAY_MS),
        settings.dailyGoalMinutes,
        settings.nudgesEnabled,
        ticks,
    ) { sessions, focus, goal, nudges, _ ->
        val starts = focus.map { it.startedAt }
        val now = clock.nowMs()
        val occurrences = ScheduleRules.between(sessions, weekStart, weekStart.plusDays(6), zone)
        ScheduleUiState(
            week = (0L..6L).map { offset ->
                val date = weekStart.plusDays(offset)
                DayPlan(
                    date,
                    occurrences.filter { it.date == date }.map { PlanItem(it, ScheduleRules.status(it, starts, now)) },
                )
            },
            sessions = sessions,
            goalMinutes = goal,
            nudgesEnabled = nudges,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun save(session: ScheduledSession) = viewModelScope.launch {
        repository.save(session)
        ScheduleAlarms.rearm(context, repository)
    }

    fun delete(id: Long) = viewModelScope.launch {
        repository.delete(id)
        ScheduleAlarms.rearm(context, repository)
    }

    fun setGoal(minutes: Int) = viewModelScope.launch { settings.setDailyGoalMinutes(minutes) }

    fun setNudges(enabled: Boolean) = viewModelScope.launch { settings.setNudgesEnabled(enabled) }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
