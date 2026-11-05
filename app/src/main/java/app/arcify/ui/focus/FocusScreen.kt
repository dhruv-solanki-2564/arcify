package app.arcify.ui.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.arcify.R
import app.arcify.core.timer.ActiveTimer
import app.arcify.core.timer.FocusTimer
import app.arcify.core.timer.MINUTE_MS
import app.arcify.core.timer.PomodoroConfig
import app.arcify.core.protection.HardExit
import app.arcify.core.timer.TimerMode
import app.arcify.core.util.formatDuration
import app.arcify.core.util.formatShortDuration
import app.arcify.timer.TimerText

@Composable
fun FocusRoute(viewModel: FocusViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pomodoro by viewModel.pomodoro.collectAsStateWithLifecycle()
    val hardExitEnabled by viewModel.hardExitEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    FocusScreen(
        ui = ui,
        pomodoro = pomodoro,
        onStart = { mode, strict ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                // The timer works without it; the notification just stays hidden.
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.start(mode, strict)
        },
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onStop = viewModel::stop,
        hardExitEnabled = hardExitEnabled,
        onEmergencyExit = viewModel::emergencyExit,
    )
}

@Composable
fun FocusScreen(
    ui: FocusUiState,
    pomodoro: PomodoroConfig,
    onStart: (TimerMode, Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    hardExitEnabled: Boolean = false,
    onEmergencyExit: (String, String) -> Boolean = { _, _ -> false },
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.focus_today, formatShortDuration(ui.focusedTodayMs), formatShortDuration(ui.goalMs)),
            style = MaterialTheme.typography.labelLarge,
        )
        val timer = ui.timer
        if (timer == null) {
            SetupPanel(pomodoro, onStart)
        } else {
            RunningPanel(timer, ui.now, onPause, onResume, onStop, hardExitEnabled, onEmergencyExit)
        }
    }
}

private enum class ModeChoice(@param:StringRes val label: Int) {
    TIMER(R.string.mode_timer),
    STOPWATCH(R.string.mode_stopwatch),
    POMODORO(R.string.mode_pomodoro),
}

private val CountdownPresetsMin = listOf(15, 25, 45, 60, 90)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupPanel(pomodoro: PomodoroConfig, onStart: (TimerMode, Boolean) -> Unit) {
    var choice by rememberSaveable { mutableStateOf(ModeChoice.POMODORO) }
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var strict by rememberSaveable { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChoice.entries.forEach { option ->
            FilterChip(
                selected = choice == option,
                onClick = { choice = option },
                label = { Text(stringResource(option.label)) },
            )
        }
    }

    when (choice) {
        ModeChoice.TIMER -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountdownPresetsMin.forEach { preset ->
                FilterChip(
                    selected = minutes == preset,
                    onClick = { minutes = preset },
                    label = { Text(stringResource(R.string.minutes_short, preset)) },
                )
            }
        }

        ModeChoice.POMODORO -> Text(
            stringResource(
                R.string.pomodoro_summary,
                pomodoro.focusMs / MINUTE_MS,
                pomodoro.shortBreakMs / MINUTE_MS,
                pomodoro.longBreakMs / MINUTE_MS,
                pomodoro.cyclesBeforeLongBreak,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        ModeChoice.STOPWATCH -> Text(stringResource(R.string.stopwatch_hint), style = MaterialTheme.typography.bodyMedium)
    }

    if (choice != ModeChoice.STOPWATCH) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = strict, onCheckedChange = { strict = it })
            Text(stringResource(R.string.strict_mode), style = MaterialTheme.typography.bodyMedium)
        }
    }

    Button(onClick = {
        val mode = when (choice) {
            ModeChoice.TIMER -> TimerMode.Countdown(minutes * MINUTE_MS)
            ModeChoice.STOPWATCH -> TimerMode.Stopwatch
            ModeChoice.POMODORO -> TimerMode.Pomodoro(pomodoro)
        }
        onStart(mode, strict && choice != ModeChoice.STOPWATCH)
    }) {
        Text(stringResource(R.string.start))
    }
}

@Composable
private fun RunningPanel(
    timer: ActiveTimer,
    now: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    hardExitEnabled: Boolean,
    onEmergencyExit: (String, String) -> Boolean,
) {
    var exiting by rememberSaveable { mutableStateOf(false) }
    val phase = stringResource(TimerText.phaseLabel(timer))
    Text(
        if (timer.isRunning) phase else "$phase · ${stringResource(R.string.paused)}",
        style = MaterialTheme.typography.titleLarge,
    )
    Text(formatDuration(TimerText.displayMs(timer, now)), style = MaterialTheme.typography.displayLarge)
    if (timer.mode is TimerMode.Pomodoro) {
        Text(stringResource(R.string.cycles_done, timer.completedFocusCycles), style = MaterialTheme.typography.bodyMedium)
    }
    if (timer.strict) {
        Text(stringResource(R.string.strict_active), style = MaterialTheme.typography.bodyMedium)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (timer.isRunning) {
            OutlinedButton(onClick = onPause, enabled = FocusTimer.canPause(timer)) { Text(stringResource(R.string.pause)) }
        } else {
            OutlinedButton(onClick = onResume) { Text(stringResource(R.string.resume)) }
        }
        Button(onClick = onStop, enabled = FocusTimer.canStop(timer)) { Text(stringResource(R.string.stop)) }
    }
    if (timer.strict && hardExitEnabled) {
        TextButton(onClick = { exiting = true }) { Text(stringResource(R.string.hard_exit_button)) }
    }
    if (exiting) {
        EmergencyExitDialog(onConfirm = onEmergencyExit, onDismiss = { exiting = false })
    }
}

/**
 * Asks for a fresh random code before ending a strict session (spec F02). The code is shown as
 * plain, non-selectable text, so it has to be typed character by character.
 */
@Composable
private fun EmergencyExitDialog(onConfirm: (input: String, code: String) -> Boolean, onDismiss: () -> Unit) {
    val code by rememberSaveable { mutableStateOf(HardExit.newCode()) }
    var input by rememberSaveable { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hard_exit_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.hard_exit_dialog_body, HardExit.CODE_LENGTH))
                Text(
                    code.chunked(4).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it.filterNot(Char::isWhitespace)
                        wrong = false
                    },
                    isError = wrong,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (wrong) Text(stringResource(R.string.hard_exit_wrong), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (onConfirm(input, code)) onDismiss() else wrong = true
            }) { Text(stringResource(R.string.hard_exit_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
