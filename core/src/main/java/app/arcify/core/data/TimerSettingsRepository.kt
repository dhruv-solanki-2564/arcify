package app.arcify.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import app.arcify.core.timer.MINUTE_MS
import app.arcify.core.timer.PomodoroConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Pomodoro defaults, stored in whole minutes. */
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

    private fun Preferences.minutes(key: Preferences.Key<Int>, defaultMs: Long): Long =
        this[key]?.coerceAtLeast(1)?.times(MINUTE_MS) ?: defaultMs

    private companion object {
        val FOCUS = intPreferencesKey("pomodoro_focus_min")
        val SHORT_BREAK = intPreferencesKey("pomodoro_short_break_min")
        val LONG_BREAK = intPreferencesKey("pomodoro_long_break_min")
        val CYCLES = intPreferencesKey("pomodoro_cycles_before_long")
    }
}
