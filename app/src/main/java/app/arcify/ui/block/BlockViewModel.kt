package app.arcify.ui.block

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.arcify.R
import app.arcify.blocker.AppCatalog
import app.arcify.blocker.BlockerPermissions
import app.arcify.blocker.InstalledApp
import app.arcify.blocker.UsageReader
import app.arcify.core.block.AppLimit
import app.arcify.core.block.LimitRules
import app.arcify.core.data.BlockRepository
import app.arcify.core.timer.MINUTE_MS
import app.arcify.core.timer.WallClock
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionState(val accessibility: Boolean = false, val usageAccess: Boolean = false)

data class LimitRow(val limit: AppLimit, val label: String, val usedMs: Long, val locked: Boolean)

data class BlockUiState(
    val permissions: PermissionState = PermissionState(),
    val limits: List<LimitRow> = emptyList(),
    val allowed: List<InstalledApp> = emptyList(),
    /** Null while the installed-app list is still loading. */
    val installed: List<InstalledApp>? = null,
)

@HiltViewModel
class BlockViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: BlockRepository,
    private val catalog: AppCatalog,
    private val usage: UsageReader,
    private val clock: WallClock,
) : ViewModel() {

    private val refreshes = MutableStateFlow(0)
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }
    private val triggers = combine(refreshes, ticks) { count, _ -> count }

    private val permissions = triggers
        .map { PermissionState(BlockerPermissions.isAccessibilityEnabled(context), usage.hasAccess()) }
        .flowOn(Dispatchers.IO)

    private val usedToday = triggers.map { usage.usedToday() }.flowOn(Dispatchers.IO)

    private val installed = flow<List<InstalledApp>?> {
        emit(null)
        emit(catalog.launchableApps())
    }.flowOn(Dispatchers.IO)

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    val ui: StateFlow<BlockUiState> = combine(
        permissions,
        repository.observeLimits(),
        repository.observeAllowedApps(),
        usedToday,
        installed,
    ) { perms, limits, allowedPackages, used, apps ->
        val labels = apps?.associate { it.packageName to it.label }.orEmpty()
        val now = clock.nowMs()
        BlockUiState(
            permissions = perms,
            limits = limits.map { limit ->
                LimitRow(
                    limit = limit,
                    label = labels[limit.packageName] ?: limit.packageName,
                    usedMs = used[limit.packageName] ?: 0L,
                    locked = LimitRules.isStrict(limit, now),
                )
            }.sortedBy { it.label.lowercase() },
            allowed = allowedPackages
                .map { InstalledApp(it, labels[it] ?: it) }
                .sortedBy { it.label.lowercase() },
            installed = apps,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockUiState())

    /** Call when the screen becomes visible, e.g. after returning from system settings. */
    fun refresh() = refreshes.update { it + 1 }

    fun addLimit(packageName: String, minutes: Int) = save(AppLimit(packageName, minutes * MINUTE_MS))

    fun setEnabled(row: LimitRow, enabled: Boolean) = save(row.limit.copy(enabled = enabled))

    fun delete(row: LimitRow) = viewModelScope.launch {
        if (!repository.deleteLimit(row.limit.packageName)) message(R.string.limit_locked_message)
    }

    fun lockForToday(row: LimitRow) = viewModelScope.launch {
        if (!repository.lockForToday(row.limit.packageName)) message(R.string.limit_locked_message)
    }

    fun allow(packageName: String) = viewModelScope.launch { repository.allowApp(packageName) }

    fun disallow(packageName: String) = viewModelScope.launch { repository.disallowApp(packageName) }

    fun resetAllowed() = viewModelScope.launch { repository.resetAllowedApps() }

    private fun save(limit: AppLimit) = viewModelScope.launch {
        if (!repository.saveLimit(limit)) message(R.string.limit_locked_message)
    }

    private fun message(@StringRes text: Int) {
        _messages.tryEmit(text)
    }
}
