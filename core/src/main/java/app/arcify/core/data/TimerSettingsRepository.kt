package app.arcify.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import app.arcify.core.nudge.NudgeKind
import app.arcify.core.timer.MINUTE_MS
import app.arcify.core.timer.PomodoroConfig
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Pomodoro defaults (in whole minutes), the daily focus goal and nudge settings. */
@Singleton
class TimerSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val pomodoro: Flow<PomodoroConfig> = store.data.map { prefs ->
        val defaults = PomodoroConfig()
        PomodoroConfig(
            focusMs = prefs.minutes(FOCUS, defaults.focusMs),
            shortBreakMs = prefs.minutes(SHORT_BREAK, defaults.shortBreakMs),
            longBreakMs = prefs.minutes(LONG_BREAK, defaults.longBreakMs),
            cyclesBeforeLongBreak = (prefs[CYCLES] ?: defaults.cyclesBeforeLongBreak).coerceAtLeast(1),
        )
    }

    suspend fun update(config: PomodoroConfig) {
        store.edit { prefs ->
            prefs[FOCUS] = (config.focusMs / MINUTE_MS).toInt()
            prefs[SHORT_BREAK] = (config.shortBreakMs / MINUTE_MS).toInt()
            prefs[LONG_BREAK] = (config.longBreakMs / MINUTE_MS).toInt()
            prefs[CYCLES] = config.cyclesBeforeLongBreak
        }
    }

    /** Daily focus goal in minutes (spec F09). */
    val dailyGoalMinutes: Flow<Int> = store.data.map { (it[GOAL] ?: DEFAULT_GOAL_MIN).coerceAtLeast(1) }

    suspend fun setDailyGoalMinutes(minutes: Int) = store.edit { it[GOAL] = minutes.coerceAtLeast(1) }

    val nudgesEnabled: Flow<Boolean> = store.data.map { it[NUDGES] ?: true }

    suspend fun setNudgesEnabled(enabled: Boolean) = store.edit { it[NUDGES] = enabled }

    suspend fun lastNudges(): Map<NudgeKind, LocalDate> {
        val prefs = store.data.first()
        return NudgeKind.entries.mapNotNull { kind -> prefs[nudgeKey(kind)]?.let { kind to LocalDate.ofEpochDay(it) } }.toMap()
    }

    suspend fun markNudged(kind: NudgeKind, day: LocalDate) = store.edit { it[nudgeKey(kind)] = day.toEpochDay() }

    private fun Preferences.minutes(key: Preferences.Key<Int>, defaultMs: Long): Long =
        this[key]?.coerceAtLeast(1)?.times(MINUTE_MS) ?: defaultMs

    private fun nudgeKey(kind: NudgeKind) = longPreferencesKey("nudge_last_${kind.name}")

    companion object {
        const val DEFAULT_GOAL_MIN = 120
        private val FOCUS = intPreferencesKey("pomodoro_focus_min")
        private val SHORT_BREAK = intPreferencesKey("pomodoro_short_break_min")
        private val LONG_BREAK = intPreferencesKey("pomodoro_long_break_min")
        private val CYCLES = intPreferencesKey("pomodoro_cycles_before_long")
        private val GOAL = intPreferencesKey("daily_goal_min")
        private val NUDGES = booleanPreferencesKey("nudges_enabled")
    }
}
