package app.arcify.ui.focus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.arcify.core.data.SessionRepository
import app.arcify.core.data.TimerSettingsRepository
import app.arcify.core.timer.ActiveTimer
import app.arcify.core.timer.PomodoroConfig
import app.arcify.core.timer.TimerController
import app.arcify.core.timer.TimerMode
import app.arcify.timer.FocusTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FocusUiState(
    val timer: ActiveTimer? = null,
    val now: Long = 0,
    val focusedTodayMs: Long = 0,
)

@HiltViewModel
class FocusViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val controller: TimerController,
    settings: TimerSettingsRepository,
    sessions: SessionRepository,
) : ViewModel() {

    /** Re-renders the clock twice a second; the time itself comes from timestamps. */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(500)
        }
    }

    val ui: StateFlow<FocusUiState> =
        combine(controller.timer, ticker, sessions.observeFocusedToday()) { timer, _, today ->
            FocusUiState(timer = timer, now = controller.now(), focusedTodayMs = today)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    val pomodoro: StateFlow<PomodoroConfig> =
        settings.pomodoro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PomodoroConfig())

    fun start(mode: TimerMode, strict: Boolean) {
        viewModelScope.launch {
            if (controller.start(mode, strict)) FocusTimerService.start(appContext)
        }
    }

    fun pause() {
        viewModelScope.launch { controller.pause() }
    }

    fun resume() {
        viewModelScope.launch { controller.resume() }
    }

    fun stop() {
        viewModelScope.launch { controller.stop() }
    }
}
