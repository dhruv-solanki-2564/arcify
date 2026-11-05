package app.arcify.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.arcify.MainActivity
import app.arcify.R
import app.arcify.core.alarm.WakeAlarm
import app.arcify.core.alarm.WakeAlarmRules
import app.arcify.core.alarm.WakeTask
import app.arcify.core.data.WakeAlarmRepository
import app.arcify.core.timer.MINUTE_MS
import app.arcify.core.timer.TimerController
import app.arcify.core.timer.TimerMode
import app.arcify.timer.FocusTimerService
import app.arcify.ui.theme.ArcifyTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The ringing alarm's screen (spec F14), shown over the lock screen. Back does nothing; the
 * alarm stops only by starting a focus session, snoozing (if any are left) or solving the task.
 */
@AndroidEntryPoint
class WakeAlarmActivity : ComponentActivity() {

    @Inject lateinit var repository: WakeAlarmRepository
    @Inject lateinit var timerController: TimerController

    private var alarm by mutableStateOf<WakeAlarm?>(null)
    private var snoozesUsed by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        load(intent)
        setContent {
            ArcifyTheme {
                BackHandler { /* The alarm can't be dismissed with Back. */ }
                alarm?.let { current ->
                    AlarmScreen(
                        alarm = current,
                        snoozesUsed = snoozesUsed,
                        onFocus = { startFocus(current) },
                        onSnooze = { snooze(current) },
                        onSolved = { stopAndClose() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent) {
        val id = intent.getLongExtra(WakeAlarms.EXTRA_ALARM_ID, -1L)
        snoozesUsed = intent.getIntExtra(WakeAlarms.EXTRA_SNOOZES_USED, 0)
        lifecycleScope.launch {
            // If the alarm was deleted meanwhile, still let the user stop the sound.
            alarm = repository.get(id) ?: WakeAlarm(minuteOfDay = 0, snoozesAllowed = 0)
        }
    }

    private fun startFocus(alarm: WakeAlarm) {
        lifecycleScope.launch {
            if (timerController.start(TimerMode.Countdown(alarm.focusMinutes * MINUTE_MS), strict = false)) {
                FocusTimerService.start(this@WakeAlarmActivity)
            }
            startActivity(Intent(this@WakeAlarmActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            stopAndClose()
        }
    }

    private fun snooze(alarm: WakeAlarm) {
        if (!WakeAlarmRules.canSnooze(alarm, snoozesUsed)) return
        WakeAlarms.snooze(this, alarm, snoozesUsed)
        stopAndClose()
    }

    private fun stopAndClose() {
        WakeAlarmService.stop(this)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        fun intent(context: Context, alarmId: Long, snoozesUsed: Int): Intent =
            Intent(context, WakeAlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(WakeAlarms.EXTRA_ALARM_ID, alarmId)
                .putExtra(WakeAlarms.EXTRA_SNOOZES_USED, snoozesUsed)
    }
}

@Composable
private fun AlarmScreen(
    alarm: WakeAlarm,
    snoozesUsed: Int,
    onFocus: () -> Unit,
    onSnooze: () -> Unit,
    onSolved: () -> Unit,
) {
    var solving by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            Text(
                String.format(Locale.ROOT, "%02d:%02d", alarm.minuteOfDay / 60, alarm.minuteOfDay % 60),
                style = MaterialTheme.typography.displayLarge,
            )
            Text(stringResource(R.string.wake_title), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onFocus) { Text(stringResource(R.string.wake_start_focus, alarm.focusMinutes)) }
            val left = alarm.snoozesAllowed - snoozesUsed
            OutlinedButton(onClick = onSnooze, enabled = WakeAlarmRules.canSnooze(alarm, snoozesUsed)) {
                Text(stringResource(R.string.wake_snooze, alarm.snoozeMinutes, left.coerceAtLeast(0)))
            }
            if (solving) {
                SolveTask(onSolved)
            } else {
                TextButton(onClick = { solving = true }) { Text(stringResource(R.string.wake_solve_instead)) }
            }
        }
    }
}

/** Three sums; all must be right to turn the alarm off without focusing. */
@Composable
private fun SolveTask(onSolved: () -> Unit) {
    val task = remember { WakeTask.newTask() }
    val answers = remember { mutableStateListOf(*Array(task.size) { "" }) }
    var wrong by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.wake_solve_body), style = MaterialTheme.typography.bodyMedium)
        task.forEachIndexed { i, problem ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${problem.a} + ${problem.b} =", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = answers[i],
                    onValueChange = {
                        answers[i] = it.filter(Char::isDigit).take(3)
                        wrong = false
                    },
                    singleLine = true,
                    isError = wrong,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                )
            }
        }
        if (wrong) Text(stringResource(R.string.wake_solve_wrong), color = MaterialTheme.colorScheme.error)
        Button(onClick = { if (WakeTask.isSolved(task, answers.toList())) onSolved() else wrong = true }) {
            Text(stringResource(R.string.wake_turn_off))
        }
    }
}
