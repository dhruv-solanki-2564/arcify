package app.arcify.ui.block

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.arcify.R
import app.arcify.blocker.AppCatalog
import app.arcify.blocker.InstalledApp
import app.arcify.core.batching.BatchingSettings
import app.arcify.core.data.BatchingRepository
import app.arcify.core.util.formatShortDuration
import app.arcify.notify.ArcifyNotificationListener
import app.arcify.notify.BatchDelivery
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BatchingUiState(
    val listenerEnabled: Boolean = false,
    val settings: BatchingSettings = BatchingSettings(),
    val batchedApps: List<InstalledApp> = emptyList(),
    val heldCount: Int = 0,
    /** Null while the installed-app list is still loading. */
    val installed: List<InstalledApp>? = null,
)

/** Backs the notification batching screen (spec F13). */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: BatchingRepository,
    catalog: AppCatalog,
) : ViewModel() {

    private val refreshes = MutableStateFlow(0)
    private val listener = refreshes.map { ArcifyNotificationListener.isEnabled(context) }
    private val installed = flow<List<InstalledApp>?> {
        emit(null)
        emit(catalog.launchableApps())
    }.flowOn(Dispatchers.IO)

    val ui: StateFlow<BatchingUiState> =
        combine(listener, repository.settings, repository.observeHeldCount(), installed) { enabled, settings, held, apps ->
            val labels = apps?.associate { it.packageName to it.label }.orEmpty()
            BatchingUiState(
                listenerEnabled = enabled,
                settings = settings,
                batchedApps = settings.packages.map { InstalledApp(it, labels[it] ?: it) }.sortedBy { it.label.lowercase() },
                heldCount = held,
                installed = apps,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatchingUiState())

    fun refresh() = refreshes.update { it + 1 }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(enabled)
        if (enabled) {
            BatchDelivery.schedule(context, repository.settings.first().intervalMinutes)
            ArcifyNotificationListener.requestRebind(context)
        } else {
            // Switching off must never strand held notifications.
            BatchDelivery.deliver(context, repository)
        }
    }

    fun setInterval(minutes: Int) = viewModelScope.launch {
        repository.setInterval(minutes)
        if (repository.settings.first().enabled) BatchDelivery.schedule(context, minutes)
    }

    fun add(packageName: String) = viewModelScope.launch { repository.addPackage(packageName) }

    fun remove(packageName: String) = viewModelScope.launch { repository.removePackage(packageName) }

    fun reset() = viewModelScope.launch { repository.clearPackages() }

    fun deliverNow() = viewModelScope.launch { BatchDelivery.deliver(context, repository) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationsRoute(viewModel: NotificationsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PermissionRow(
                title = stringResource(R.string.perm_notification_access),
                description = stringResource(R.string.perm_notification_access_desc),
                granted = ui.listenerEnabled,
                onTurnOn = { context.startActivity(ArcifyNotificationListener.settingsIntent()) },
            )
        }
        item {
            ToggleRow(
                title = stringResource(R.string.batching_title),
                description = stringResource(R.string.batching_desc),
                checked = ui.settings.enabled,
                onCheckedChange = viewModel::setEnabled,
            )
        }
        item { Text(stringResource(R.string.batching_interval), style = MaterialTheme.typography.titleSmall) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BatchingSettings.INTERVAL_CHOICES.forEach { minutes ->
                    FilterChip(
                        selected = ui.settings.intervalMinutes == minutes,
                        onClick = { viewModel.setInterval(minutes) },
                        label = { Text(stringResource(R.string.batching_every, formatShortDuration(minutes * 60_000L))) },
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.batching_held, ui.heldCount),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = viewModel::deliverNow, enabled = ui.heldCount > 0) {
                    Text(stringResource(R.string.batching_deliver_now))
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(R.string.batching_apps) }
        item { Text(stringResource(R.string.batching_apps_desc), style = MaterialTheme.typography.bodySmall) }
        if (ui.batchedApps.isEmpty()) {
            item { Text(stringResource(R.string.batching_apps_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        items(ui.batchedApps, key = { it.packageName }) { app ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.remove(app.packageName) }) { Text(stringResource(R.string.remove)) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { picking = true }) { Text(stringResource(R.string.batching_add)) }
                OutlinedButton(onClick = viewModel::reset, enabled = ui.batchedApps.isNotEmpty()) {
                    Text(stringResource(R.string.reset_defaults))
                }
            }
        }
    }

    if (picking) {
        val existing = ui.settings.packages
        AppPickerDialog(
            apps = ui.installed?.filterNot { it.packageName in existing },
            onPick = { app ->
                picking = false
                viewModel.add(app.packageName)
            },
            onDismiss = { picking = false },
        )
    }
}
