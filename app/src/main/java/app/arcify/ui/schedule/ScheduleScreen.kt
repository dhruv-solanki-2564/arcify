package app.arcify.ui.schedule

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.arcify.R
import app.arcify.core.alarm.WakeAlarm
import app.arcify.core.alarm.WakeAlarmRules
import app.arcify.core.schedule.OccurrenceStatus
import app.arcify.core.schedule.Repeat
import app.arcify.core.schedule.ScheduleRules
import app.arcify.core.schedule.ScheduledSession
import app.arcify.core.util.formatShortDuration
import app.arcify.ui.block.SectionTitle
import app.arcify.ui.block.ToggleRow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val GoalPresetsMin = listOf(30, 60, 90, 120, 180, 240)

/** A pending edit: a new session, or an existing one. */
private data class Editing(val session: ScheduledSession?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleRoute(viewModel: ScheduleViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Editing?>(null) }
    var editingAlarm by remember { mutableStateOf<AlarmEditing?>(null) }
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionTitle(R.string.schedule_goal_title) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalPresetsMin.forEach { minutes ->
                    FilterChip(
                        selected = ui.goalMinutes == minutes,
                        onClick = { viewModel.setGoal(minutes) },
                        label = { Text(formatShortDuration(minutes * 60_000L)) },
                    )
                }
            }
        }
        item {
            ToggleRow(
                title = stringResource(R.string.schedule_nudges),
                description = stringResource(R.string.schedule_nudges_desc),
                checked = ui.nudgesEnabled,
                onCheckedChange = viewModel::setNudges,
            )
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wake_section), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { editingAlarm = AlarmEditing(null) }) { Text(stringResource(R.string.wake_add)) }
            }
        }
        item { Text(stringResource(R.string.wake_section_desc), style = MaterialTheme.typography.bodySmall) }
        if (alarms.isEmpty()) {
            item { Text(stringResource(R.string.wake_none), style = MaterialTheme.typography.bodySmall) }
        }
        alarms.forEach { alarm ->
            item(key = "alarm/${alarm.id}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingAlarm = AlarmEditing(alarm) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            timeText(alarm.minuteOfDay) + " · " + daysText(alarm.daysMask, stringResource(R.string.wake_once)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(
                                R.string.wake_item_detail,
                                formatShortDuration(alarm.snoozeMinutes * 60_000L),
                                alarm.snoozesAllowed,
                                formatShortDuration(alarm.focusMinutes * 60_000L),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        nextRingText(alarm)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Switch(checked = alarm.enabled, onCheckedChange = { viewModel.saveAlarm(alarm.copy(enabled = it)) })
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.schedule_week), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { editing = Editing(null) }) { Text(stringResource(R.string.schedule_add)) }
            }
        }
        ui.week.forEach { day ->
            item(key = "day/${day.date}") {
                Text(
                    day.date.format(dayFormat) + if (day.date == ui.today) " · " + stringResource(R.string.schedule_today) else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (day.items.isEmpty()) {
                item(key = "empty/${day.date}") {
                    Text(stringResource(R.string.schedule_nothing), style = MaterialTheme.typography.bodySmall)
                }
            }
            day.items.forEach { item ->
                item(key = "occ/${item.occurrence.session.id}/${day.date}") {
                    val s = item.occurrence.session
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = Editing(s) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.schedule_item, timeText(s.startMinute), s.title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(
                                    R.string.schedule_item_detail,
                                    formatShortDuration(s.durationMin * 60_000L),
                                    stringResource(if (s.pomodoro) R.string.mode_pomodoro else R.string.mode_timer),
                                ) + if (s.strict) " · " + stringResource(R.string.schedule_strict) else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(stringResource(item.status.label()), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    editing?.let { edit ->
        SessionDialog(
            existing = edit.session,
            today = ui.today,
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = edit.session?.let { s ->
                {
                    viewModel.delete(s.id)
                    editing = null
                }
            },
            onDismiss = { editing = null },
        )
    }

    editingAlarm?.let { edit ->
        WakeAlarmDialog(
            existing = edit.alarm,
            onSave = {
                viewModel.saveAlarm(it)
                editingAlarm = null
                val now = System.currentTimeMillis()
                WakeAlarmRules.nextTrigger(it, now, ZoneId.systemDefault())?.let { at ->
                    // Rounded up, so an alarm 90 s away reads "2 min", never "1 min".
                    val inText = formatShortDuration(at - now + 59_999)
                    Toast.makeText(context, context.getString(R.string.wake_rings_in, inText), Toast.LENGTH_LONG).show()
                }
            },
            onDelete = edit.alarm?.let { a ->
                {
                    viewModel.deleteAlarm(a.id)
                    editingAlarm = null
                }
            },
            onDismiss = { editingAlarm = null },
        )
    }
}

/** A pending wake-alarm edit: a new alarm, or an existing one. */
private data class AlarmEditing(val alarm: WakeAlarm?)

private fun daysText(mask: Int, once: String): String {
    if (mask == 0) return once
    return DayOfWeek.entries
        .filter { mask and ScheduleRules.dayBit(it) != 0 }
        .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

private val SnoozeLengthsMin = listOf(3, 5, 10)
private val FocusLengthsMin = listOf(25, 50, 90)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WakeAlarmDialog(
    existing: WakeAlarm?,
    onSave: (WakeAlarm) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val time = rememberTimePickerState(
        initialHour = (existing?.minuteOfDay ?: (7 * 60)) / 60,
        initialMinute = (existing?.minuteOfDay ?: 0) % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    var days by remember { mutableIntStateOf(existing?.daysMask ?: 0) }
    var snoozes by remember { mutableIntStateOf(existing?.snoozesAllowed ?: WakeAlarm.DEFAULT_SNOOZES) }
    var snoozeMin by remember { mutableIntStateOf(existing?.snoozeMinutes ?: WakeAlarm.DEFAULT_SNOOZE_MIN) }
    var focusMin by remember { mutableIntStateOf(existing?.focusMinutes ?: WakeAlarm.DEFAULT_FOCUS_MIN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.wake_new else R.string.wake_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TimePicker(state = time)
                Text(stringResource(R.string.wake_days), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val bit = ScheduleRules.dayBit(d)
                        FilterChip(
                            selected = days and bit != 0,
                            onClick = { days = days xor bit },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                Text(stringResource(R.string.wake_snoozes), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..WakeAlarm.MAX_SNOOZES).forEach { n ->
                        FilterChip(selected = snoozes == n, onClick = { snoozes = n }, label = { Text(n.toString()) })
                    }
                }
                if (snoozes > 0) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SnoozeLengthsMin.forEach { m ->
                            FilterChip(selected = snoozeMin == m, onClick = { snoozeMin = m }, label = { Text(formatShortDuration(m * 60_000L)) })
                        }
                    }
                }
                Text(stringResource(R.string.wake_focus_length), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusLengthsMin.forEach { m ->
                        FilterChip(selected = focusMin == m, onClick = { focusMin = m }, label = { Text(formatShortDuration(m * 60_000L)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    WakeAlarm(
                        id = existing?.id ?: 0,
                        minuteOfDay = time.hour * 60 + time.minute,
                        daysMask = days,
                        // Saving re-enables an alarm that already rang once.
                        enabled = true,
                        snoozesAllowed = snoozes,
                        snoozeMinutes = snoozeMin,
                        focusMinutes = focusMin,
                    ),
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** A time of day in the phone's 12- or 24-hour format. */
@Composable
private fun timeText(minuteOfDay: Int): String {
    val pattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/** "Next: Fri 7:00 AM", or null when the alarm is off. */
@Composable
private fun nextRingText(alarm: WakeAlarm): String? {
    val zone = ZoneId.systemDefault()
    val at = WakeAlarmRules.nextTrigger(alarm, System.currentTimeMillis(), zone) ?: return null
    val time = Instant.ofEpochMilli(at).atZone(zone)
    val day = time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return stringResource(R.string.wake_next, day + " " + timeText(time.hour * 60 + time.minute))
}

private fun OccurrenceStatus.label(): Int = when (this) {
    OccurrenceStatus.UPCOMING -> R.string.status_upcoming
    OccurrenceStatus.NOW -> R.string.status_now
    OccurrenceStatus.DONE -> R.string.status_done
    OccurrenceStatus.MISSED -> R.string.status_missed
}

private val DurationPresetsMin = listOf(25, 50, 90, 120)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SessionDialog(
    existing: ScheduledSession?,
    today: LocalDate,
    onSave: (ScheduledSession) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    val time = rememberTimePickerState(
        initialHour = (existing?.startMinute ?: (18 * 60)) / 60,
        initialMinute = (existing?.startMinute ?: 0) % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    var duration by remember { mutableIntStateOf(existing?.durationMin ?: 50) }
    var pomodoro by remember { mutableStateOf(existing?.pomodoro ?: false) }
    var strict by remember { mutableStateOf(existing?.strict ?: false) }
    var repeat by remember { mutableStateOf(existing?.repeat ?: Repeat.WEEKDAYS) }
    var days by remember { mutableIntStateOf(existing?.daysMask ?: 0) }
    var tomorrow by remember {
        mutableStateOf(existing?.onceDate?.let { LocalDate.ofEpochDay(it) == today.plusDays(1) } ?: false)
    }
    val defaultTitle = stringResource(R.string.schedule_default_title)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.schedule_new else R.string.schedule_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.schedule_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TimePicker(state = time)
                Text(stringResource(R.string.schedule_length), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DurationPresetsMin.forEach { m ->
                        FilterChip(selected = duration == m, onClick = { duration = m }, label = { Text(formatShortDuration(m * 60_000L)) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !pomodoro, onClick = { pomodoro = false }, label = { Text(stringResource(R.string.mode_timer)) })
                    FilterChip(selected = pomodoro, onClick = { pomodoro = true }, label = { Text(stringResource(R.string.mode_pomodoro)) })
                    FilterChip(selected = strict, onClick = { strict = !strict }, label = { Text(stringResource(R.string.schedule_strict)) })
                }
                Text(stringResource(R.string.schedule_repeat), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Repeat.entries.forEach { r ->
                        FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(stringResource(r.label())) })
                    }
                }
                when (repeat) {
                    Repeat.CUSTOM -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { d ->
                            val bit = ScheduleRules.dayBit(d)
                            FilterChip(
                                selected = days and bit != 0,
                                onClick = { days = days xor bit },
                                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                            )
                        }
                    }

                    Repeat.ONCE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !tomorrow, onClick = { tomorrow = false }, label = { Text(stringResource(R.string.schedule_today)) })
                        FilterChip(selected = tomorrow, onClick = { tomorrow = true }, label = { Text(stringResource(R.string.schedule_tomorrow)) })
                    }

                    else -> Unit
                }
                if (strict) Text(stringResource(R.string.schedule_strict_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = repeat != Repeat.CUSTOM || days != 0,
                onClick = {
                    onSave(
                        ScheduledSession(
                            id = existing?.id ?: 0,
                            title = title.trim().ifEmpty { defaultTitle },
                            startMinute = time.hour * 60 + time.minute,
                            durationMin = duration,
                            pomodoro = pomodoro,
                            strict = strict,
                            repeat = repeat,
                            daysMask = if (repeat == Repeat.CUSTOM) days else 0,
                            onceDate = if (repeat == Repeat.ONCE) (if (tomorrow) today.plusDays(1) else today).toEpochDay() else null,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private fun Repeat.label(): Int = when (this) {
    Repeat.ONCE -> R.string.repeat_once
    Repeat.DAILY -> R.string.repeat_daily
    Repeat.WEEKDAYS -> R.string.repeat_weekdays
    Repeat.CUSTOM -> R.string.repeat_custom
}
