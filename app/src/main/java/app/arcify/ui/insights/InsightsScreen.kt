package app.arcify.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.arcify.R
import app.arcify.core.insights.AppCategory
import app.arcify.core.insights.CategorySplit
import app.arcify.core.util.formatShortDuration
import app.arcify.ui.block.SectionTitle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsRoute(viewModel: InsightsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!ui.hasUsageAccess) {
            item { Text(stringResource(R.string.insights_no_access), style = MaterialTheme.typography.bodyMedium) }
        }

        item { SectionTitle(R.string.insights_today) }
        item {
            Text(
                stringResource(R.string.insights_screen_time, formatShortDuration(ui.today.totalMs)),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item { SplitBar(ui.today) }
        item { Legend() }
        item {
            Text(
                stringResource(R.string.insights_focus, formatShortDuration(ui.focusTodayMs), formatShortDuration(ui.goalMs)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item {
            val unlocks = ui.unlocksToday?.toString() ?: stringResource(R.string.insights_unknown)
            Text(stringResource(R.string.insights_unlocks, unlocks), style = MaterialTheme.typography.bodyLarge)
        }
        item { Text(stringResource(R.string.insights_shortform, ui.shortFormToday), style = MaterialTheme.typography.bodyLarge) }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(R.string.insights_week) }
        item { WeekChart(ui.week) }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(R.string.insights_streaks) }
        items(ui.streaks) { row ->
            Text(
                stringResource(R.string.insights_streak_row, stringResource(row.label), row.streak.current, row.streak.best),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(R.string.insights_top_apps) }
        if (ui.topApps.isEmpty()) {
            item { Text(stringResource(R.string.insights_no_usage), style = MaterialTheme.typography.bodyMedium) }
        }
        items(ui.topApps, key = { it.packageName }) { app ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    Text(formatShortDuration(app.ms), style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(onClick = { viewModel.cycleCategory(app) }, label = { Text(stringResource(app.category.label())) })
            }
        }
        item { Text(stringResource(R.string.insights_category_hint), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun categoryColor(category: AppCategory): Color = when (category) {
    AppCategory.PRODUCTIVE -> MaterialTheme.colorScheme.primary
    AppCategory.NEUTRAL -> MaterialTheme.colorScheme.outlineVariant
    AppCategory.DISTRACTING -> MaterialTheme.colorScheme.error
}

private fun AppCategory.label(): Int = when (this) {
    AppCategory.PRODUCTIVE -> R.string.category_productive
    AppCategory.NEUTRAL -> R.string.category_neutral
    AppCategory.DISTRACTING -> R.string.category_distracting
}

/** A horizontal bar split into productive / neutral / distracting time. */
@Composable
private fun SplitBar(split: CategorySplit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (split.totalMs > 0) {
            listOf(
                AppCategory.PRODUCTIVE to split.productiveMs,
                AppCategory.NEUTRAL to split.neutralMs,
                AppCategory.DISTRACTING to split.distractingMs,
            ).filter { it.second > 0 }.forEach { (category, ms) ->
                Box(
                    Modifier
                        .weight(ms.toFloat())
                        .height(14.dp)
                        .background(categoryColor(category)),
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCategory.entries.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(categoryColor(category)))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(category.label()), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Seven stacked bars (screen time by category), with focus minutes underneath. */
@Composable
private fun WeekChart(days: List<DayBar>) {
    val maxMs = days.maxOfOrNull { it.split.totalMs }?.takeIf { it > 0 } ?: 1L
    val chartHeight = 120.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.height(chartHeight).width(22.dp), verticalArrangement = Arrangement.Bottom) {
                    listOf(
                        AppCategory.DISTRACTING to day.split.distractingMs,
                        AppCategory.NEUTRAL to day.split.neutralMs,
                        AppCategory.PRODUCTIVE to day.split.productiveMs,
                    ).forEach { (category, ms) ->
                        if (ms > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(chartHeight * (ms.toFloat() / maxMs))
                                    .background(categoryColor(category)),
                            )
                        }
                    }
                }
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(formatShortDuration(day.split.totalMs), style = MaterialTheme.typography.labelSmall)
                if (day.focusMs > 0) {
                    Text(
                        stringResource(R.string.insights_week_focus, formatShortDuration(day.focusMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
    Text(stringResource(R.string.insights_week_hint), style = MaterialTheme.typography.bodySmall)
}
