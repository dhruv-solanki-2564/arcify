package app.arcify.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.arcify.MainActivity
import app.arcify.R
import app.arcify.core.insights.InsightRules
import app.arcify.core.timer.MINUTE_MS
import app.arcify.core.util.formatShortDuration
import app.arcify.work.appEntryPoint
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

private data class TodayData(
    val focusedMs: Long,
    val goalMs: Long,
    val screenMs: Long,
    val unlocks: Int?,
    val running: Boolean,
)

/** Home-screen widget: today's focus against the goal, screen time, unlocks, and a start button (spec F08). */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent {
            GlanceTheme {
                Content(context, data)
            }
        }
    }

    private suspend fun load(context: Context): TodayData {
        val deps = context.appEntryPoint()
        val zone = ZoneId.systemDefault()
        val today = InsightRules.dayKey(LocalDate.now(zone), zone)
        val usage = deps.insightsRepository().observeUsageSince(today).first().filter { it.day == today }
        return TodayData(
            focusedMs = deps.sessionRepository().observeFocusedToday().first(),
            goalMs = deps.timerSettings().dailyGoalMinutes.first() * MINUTE_MS,
            screenMs = usage.sumOf { it.foregroundMs },
            unlocks = deps.insightsRepository().observeUnlocksSince(today).first()[today],
            running = deps.timerController().timer.value != null,
        )
    }

    @Composable
    private fun Content(context: Context, data: TodayData) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                context.getString(R.string.app_name),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
            )
            Text(
                context.getString(R.string.widget_focus, formatShortDuration(data.focusedMs), formatShortDuration(data.goalMs)),
                style = TextStyle(color = GlanceTheme.colors.onSurface),
            )
            Text(
                if (data.unlocks != null) {
                    context.getString(R.string.widget_screen_unlocks, formatShortDuration(data.screenMs), data.unlocks)
                } else {
                    context.getString(R.string.widget_screen, formatShortDuration(data.screenMs))
                },
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            )
            Spacer(GlanceModifier.height(8.dp))
            Button(
                text = context.getString(if (data.running) R.string.widget_open_timer else R.string.widget_start_focus),
                onClick = if (data.running) actionStartActivity<MainActivity>() else actionRunCallback<StartFocusAction>(),
            )
        }
    }
}

class StartFocusAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appEntryPoint().scheduledStarter().startQuick()
        TodayWidget().update(context, glanceId)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
