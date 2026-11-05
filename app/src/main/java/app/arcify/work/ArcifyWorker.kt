package app.arcify.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.arcify.blocker.UsageHistorySync
import app.arcify.nudge.NudgeRunner
import app.arcify.schedule.ScheduledStarter
import app.arcify.widget.TodayWidget
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** App-wide dependencies for components Hilt can't inject directly (workers, receivers, widgets). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun usageSync(): UsageHistorySync
    fun nudges(): NudgeRunner
    fun scheduledStarter(): ScheduledStarter
    fun scheduleRepository(): app.arcify.core.data.ScheduleRepository
    fun sessionRepository(): app.arcify.core.data.SessionRepository
    fun insightsRepository(): app.arcify.core.data.InsightsRepository
    fun timerSettings(): app.arcify.core.data.TimerSettingsRepository
    fun timerController(): app.arcify.core.timer.TimerController
}

fun Context.appEntryPoint(): AppEntryPoint = EntryPointAccessors.fromApplication(applicationContext, AppEntryPoint::class.java)

/**
 * Runs about once an hour: saves usage history (spec F08), posts due nudges (F10) and
 * refreshes the home-screen widget. Each step is independent, so one failing doesn't stop the others.
 */
class ArcifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = applicationContext.appEntryPoint()
        runCatching { deps.usageSync().sync(days = 7) }
        runCatching { deps.nudges().run() }
        runCatching { TodayWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "arcify-hourly"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ArcifyWorker>(1, TimeUnit.HOURS).build(),
            )
        }
    }
}
