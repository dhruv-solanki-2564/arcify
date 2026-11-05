package app.arcify

import android.app.Application
import app.arcify.block.BlockNotifications
import app.arcify.core.data.ContentRepository
import app.arcify.core.data.ScheduleRepository
import app.arcify.nudge.NudgeNotifications
import app.arcify.schedule.ScheduleAlarms
import app.arcify.schedule.ScheduleNotifications
import app.arcify.timer.TimerNotifications
import app.arcify.work.ArcifyWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ArcifyApp : Application() {

    @Inject lateinit var contentRepository: ContentRepository
    @Inject lateinit var scheduleRepository: ScheduleRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.createChannels(this)
        BlockNotifications.createChannel(this)
        NudgeNotifications.createChannel(this)
        ScheduleNotifications.createChannel(this)
        // Usage history, nudges and the widget refresh about once an hour (spec F08/F10).
        ArcifyWorker.schedule(this)
        appScope.launch {
            // First run: the default study channels and browsers (spec F05/F06).
            contentRepository.ensureDefaults()
            // Keep the next scheduled session's alarm armed (spec F09).
            ScheduleAlarms.rearm(this@ArcifyApp, scheduleRepository)
        }
    }
}
