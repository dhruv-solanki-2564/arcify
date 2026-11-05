package app.arcify.ui.block

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.arcify.R
import app.arcify.blocker.InstalledApp
import app.arcify.blocker.protection.DeviceAdmin
import app.arcify.core.data.CommitmentTracker
import app.arcify.core.data.ProtectionRepository
import app.arcify.core.protection.Commitments
import app.arcify.core.protection.ProtectionLevel
import app.arcify.core.protection.ProtectionRules
import app.arcify.core.protection.ProtectionSettings
import app.arcify.home.HomeSetup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProtectionUiState(
    val settings: ProtectionSettings = ProtectionSettings(),
    val commitments: Commitments = Commitments(strictSession = false, lockedRules = 0),
    val deviceAdminActive: Boolean = false,
    val arcifyIsHome: Boolean = false,
    val launchers: List<InstalledApp> = emptyList(),
) {
    val active: Boolean get() = ProtectionRules.isActive(settings.level, commitments)
    val launcherLabel: String? get() = launchers.firstOrNull { it.packageName == settings.homeLauncher }?.label
}

private data class SystemState(val deviceAdmin: Boolean, val arcifyIsHome: Boolean, val launchers: List<InstalledApp>)

/** Backs the Protection screen (spec F07, plus home hiding and the emergency exit from F02). */
@HiltViewModel
class ProtectionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ProtectionRepository,
    tracker: CommitmentTracker,
) : ViewModel() {

    private val refreshes = MutableStateFlow(0)
    private val system = refreshes.map {
        SystemState(
            deviceAdmin = DeviceAdmin.isActive(context),
            arcifyIsHome = HomeSetup.isArcifyDefaultHome(context),
            launchers = HomeSetup.otherLaunchers(context),
        )
    }.flowOn(Dispatchers.IO)

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    val ui: StateFlow<ProtectionUiState> = combine(repository.settings, tracker.commitments, system) { settings, commitments, sys ->
        ProtectionUiState(settings, commitments, sys.deviceAdmin, sys.arcifyIsHome, sys.launchers)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProtectionUiState())

    /** Call when the screen becomes visible, e.g. back from system settings. */
    fun refresh() = refreshes.update { it + 1 }

    fun setLevel(level: ProtectionLevel) = launchChecked { repository.setLevel(level) }

    fun setHardExit(enabled: Boolean) = launchChecked { repository.setHardExitEnabled(enabled) }

    fun chooseLauncher(packageName: String) = viewModelScope.launch { repository.setHomeLauncher(packageName) }

    fun setHomeHiding(enabled: Boolean) = viewModelScope.launch {
        if (enabled && repository.settings.first().homeLauncher == null) {
            message(R.string.home_choose_first)
            return@launch
        }
        if (repository.setHomeHidingEnabled(enabled)) {
            HomeSetup.setProxyEnabled(context, enabled)
            refresh()
        } else {
            message(R.string.protection_locked_message)
        }
    }

    fun disableDeviceAdmin() = viewModelScope.launch {
        if (!repository.canWeaken()) {
            message(R.string.protection_locked_message)
            return@launch
        }
        DeviceAdmin.deactivate(context)
        delay(300)
        refresh()
    }

    private fun launchChecked(action: suspend () -> Boolean) = viewModelScope.launch {
        if (!action()) message(R.string.protection_locked_message)
    }

    private fun message(@StringRes text: Int) {
        _messages.tryEmit(text)
    }
}
