package app.arcify.ui.block

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.arcify.R
import app.arcify.blocker.InstalledApp
import app.arcify.core.util.formatShortDuration

/** Daily app limits and the strict-focus allowed list (F02/F03). */
@Composable
fun LimitsRoute(viewModel: BlockViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    MessageToasts(viewModel.messages)
    LimitsScreen(
        ui = ui,
        onAddLimit = { pkg, minutes -> viewModel.addLimit(pkg, minutes) },
        onToggleLimit = { row, enabled -> viewModel.setEnabled(row, enabled) },
        onDeleteLimit = { viewModel.delete(it) },
        onLockLimit = { viewModel.lockForToday(it) },
        onAllow = { viewModel.allow(it) },
        onDisallow = { viewModel.disallow(it) },
        onResetAllowed = { viewModel.resetAllowed() },
    )
}

private enum class PickTarget { LIMIT, ALLOWED }

private val LimitPresetsMin = listOf(15, 30, 45, 60, 90, 120)

@Composable
fun LimitsScreen(
    ui: BlockUiState,
    onAddLimit: (String, Int) -> Unit,
    onToggleLimit: (LimitRow, Boolean) -> Unit,
    onDeleteLimit: (LimitRow) -> Unit,
    onLockLimit: (LimitRow) -> Unit,
    onAllow: (String) -> Unit,
    onDisallow: (String) -> Unit,
    onResetAllowed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    var limitFor by remember { mutableStateOf<InstalledApp?>(null) }
    var confirmLock by remember { mutableStateOf<LimitRow?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionTitle(R.string.limits_title) }
        if (ui.limits.isEmpty()) {
            item { Text(stringResource(R.string.limits_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        items(ui.limits, key = { it.limit.packageName }) { row ->
            LimitItem(
                row = row,
                onToggle = { onToggleLimit(row, it) },
                onDelete = { onDeleteLimit(row) },
                onLock = { confirmLock = row },
            )
        }
        item {
            Button(onClick = { picking = PickTarget.LIMIT }) { Text(stringResource(R.string.limits_add)) }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(R.string.allowed_title) }
        item { Text(stringResource(R.string.allowed_desc), style = MaterialTheme.typography.bodyMedium) }
        if (ui.allowed.isEmpty()) {
            item { Text(stringResource(R.string.allowed_empty), style = MaterialTheme.typography.bodySmall) }
        }
        items(ui.allowed, key = { "allowed:" + it.packageName }) { app ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { onDisallow(app.packageName) }) { Text(stringResource(R.string.remove)) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { picking = PickTarget.ALLOWED }) { Text(stringResource(R.string.allowed_add)) }
                OutlinedButton(onClick = onResetAllowed, enabled = ui.allowed.isNotEmpty()) {
                    Text(stringResource(R.string.allowed_reset))
                }
            }
        }
    }

    picking?.let { target ->
        val exclude = when (target) {
            PickTarget.LIMIT -> ui.limits.map { it.limit.packageName }.toSet()
            PickTarget.ALLOWED -> ui.allowed.map { it.packageName }.toSet()
        }
        AppPickerDialog(
            apps = ui.installed?.filterNot { it.packageName in exclude },
            onPick = { app ->
                picking = null
                when (target) {
                    PickTarget.LIMIT -> limitFor = app
                    PickTarget.ALLOWED -> onAllow(app.packageName)
                }
            },
            onDismiss = { picking = null },
        )
    }

    limitFor?.let { app ->
        LimitLengthDialog(
            app = app,
            onPick = { minutes ->
                limitFor = null
                onAddLimit(app.packageName, minutes)
            },
            onDismiss = { limitFor = null },
        )
    }

    confirmLock?.let { row ->
        ConfirmLockDialog(
            body = stringResource(R.string.limit_lock_confirm_body, row.label),
            onConfirm = {
                confirmLock = null
                onLockLimit(row)
            },
            onDismiss = { confirmLock = null },
        )
    }
}

@Composable
private fun LimitItem(row: LimitRow, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onLock: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.limit_usage,
                        formatShortDuration(row.usedMs),
                        formatShortDuration(row.limit.dailyLimitMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = row.limit.enabled, onCheckedChange = onToggle, enabled = !row.locked)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LockControl(locked = row.locked, onLock = onLock)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete, enabled = !row.locked) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

/** "Lock for today" button, or the locked label once locked. */
@Composable
internal fun LockControl(locked: Boolean, onLock: () -> Unit) {
    if (locked) {
        Text(stringResource(R.string.limit_locked_until_midnight), style = MaterialTheme.typography.labelMedium)
    } else {
        TextButton(onClick = onLock) { Text(stringResource(R.string.limit_lock)) }
    }
}

@Composable
internal fun ConfirmLockDialog(body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.limit_lock_confirm_title)) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm_lock)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun AppPickerDialog(apps: List<InstalledApp>?, onPick: (InstalledApp) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_app_title)) },
        text = {
            if (apps == null) {
                Text(stringResource(R.string.loading_apps))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimitLengthDialog(app: InstalledApp, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_limit_title, app.label)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LimitPresetsMin.forEach { minutes ->
                    FilterChip(
                        selected = false,
                        onClick = { onPick(minutes) },
                        label = { Text(formatShortDuration(minutes * 60_000L)) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
