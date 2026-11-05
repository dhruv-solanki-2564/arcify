package app.arcify.ui.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.arcify.R
import app.arcify.blocker.protection.DeviceAdmin
import app.arcify.core.protection.HardExit
import app.arcify.core.protection.ProtectionLevel
import app.arcify.home.HomeSetup

/** Protection level, device admin, home-screen hiding and the emergency exit (F07, F02). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProtectionRoute(viewModel: ProtectionViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickingLauncher by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    MessageToasts(viewModel.messages)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(if (ui.active) R.string.protection_status_active else R.string.protection_status_inactive),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (ui.commitments.any) {
            item { Text(stringResource(R.string.protection_committed_note), style = MaterialTheme.typography.bodySmall) }
        }

        item { SectionTitle(R.string.protection_level_title) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtectionLevel.entries.forEach { level ->
                    FilterChip(
                        selected = ui.settings.level == level,
                        onClick = { viewModel.setLevel(level) },
                        label = { Text(stringResource(level.label())) },
                    )
                }
            }
        }
        item { Text(stringResource(ui.settings.level.description()), style = MaterialTheme.typography.bodySmall) }
        item { Text(stringResource(R.string.protection_guards_note), style = MaterialTheme.typography.bodySmall) }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(R.string.device_admin_title) }
        item { Text(stringResource(R.string.device_admin_desc), style = MaterialTheme.typography.bodySmall) }
        item {
            if (ui.deviceAdminActive) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.perm_on), style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = viewModel::disableDeviceAdmin) { Text(stringResource(R.string.turn_off)) }
                }
            } else {
                Button(onClick = { context.startActivity(DeviceAdmin.activationIntent(context)) }) {
                    Text(stringResource(R.string.perm_turn_on))
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(R.string.home_title) }
        item { Text(stringResource(R.string.home_desc), style = MaterialTheme.typography.bodySmall) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_launcher, ui.launcherLabel ?: stringResource(R.string.home_launcher_none)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { pickingLauncher = true }) { Text(stringResource(R.string.home_choose)) }
            }
        }
        item {
            ToggleRow(
                title = stringResource(R.string.home_toggle),
                description = stringResource(R.string.home_toggle_desc),
                checked = ui.settings.homeHidingEnabled,
                onCheckedChange = viewModel::setHomeHiding,
            )
        }
        if (ui.settings.homeHidingEnabled) {
            item {
                if (ui.arcifyIsHome) {
                    Text(stringResource(R.string.home_is_default), style = MaterialTheme.typography.labelLarge)
                } else {
                    Button(onClick = { context.startActivity(HomeSetup.homeSettingsIntent()) }) {
                        Text(stringResource(R.string.home_make_default))
                    }
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            ToggleRow(
                title = stringResource(R.string.hard_exit_title),
                description = stringResource(R.string.hard_exit_desc, HardExit.PHRASE),
                checked = ui.settings.hardExitEnabled,
                onCheckedChange = viewModel::setHardExit,
            )
        }
    }

    if (pickingLauncher) {
        AppPickerDialog(
            apps = ui.launchers,
            onPick = { app ->
                pickingLauncher = false
                viewModel.chooseLauncher(app.packageName)
            },
            onDismiss = { pickingLauncher = false },
        )
    }
}

private fun ProtectionLevel.label(): Int = when (this) {
    ProtectionLevel.OFF -> R.string.protection_off
    ProtectionLevel.DURING_COMMITMENTS -> R.string.protection_during
    ProtectionLevel.ALWAYS -> R.string.protection_always
}

private fun ProtectionLevel.description(): Int = when (this) {
    ProtectionLevel.OFF -> R.string.protection_off_desc
    ProtectionLevel.DURING_COMMITMENTS -> R.string.protection_during_desc
    ProtectionLevel.ALWAYS -> R.string.protection_always_desc
}
