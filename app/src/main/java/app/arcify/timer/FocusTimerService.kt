package app.arcify.timer

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import app.arcify.widget.TodayWidget
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.arcify.R
import app.arcify.core.timer.TimerController
import app.arcify.core.timer.TimerEvent
import app.arcify.core.timer.TimerPhase
import app.arcify.core.util.formatDuration
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while a session runs, ticks the [TimerController] once a second and
 * mirrors the session in an ongoing notification with pause/resume/stop actions.
 */
@AndroidEntryPoint
class FocusTimerService : LifecycleService() {

    @Inject lateinit var controller: TimerController

    private var tickLoop: Job? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch {
            controller.events.collect(::announce)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        enterForeground()
        when (intent?.action) {
            ACTION_PAUSE -> lifecycleScope.launch { controller.pause() }
            ACTION_RESUME -> lifecycleScope.launch { controller.resume() }
            ACTION_STOP -> lifecycleScope.launch { controller.stop() }
        }
        if (tickLoop == null) {
            tickLoop = lifecycleScope.launch { runTicks() }
        }
        return START_NOT_STICKY
    }

    private suspend fun runTicks() {
        while (true) {
            controller.tick()
            val timer = controller.timer.value ?: break
            TimerNotifications.post(this, TimerNotifications.ONGOING_ID, TimerNotifications.ongoing(this, timer, controller.now()))
            delay(TICK_MS)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enterForeground() {
        val timer = controller.timer.value
        val notification = if (timer != null) {
            TimerNotifications.ongoing(this, timer, controller.now())
        } else {
            TimerNotifications.starting(this)
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, TimerNotifications.ONGOING_ID, notification, type)
    }

    private fun announce(event: TimerEvent) {
        when (event) {
            is TimerEvent.PhaseCompleted -> {
                val text = when (event.next) {
                    TimerPhase.SHORT_BREAK -> R.string.alert_short_break
                    TimerPhase.LONG_BREAK -> R.string.alert_long_break
                    TimerPhase.FOCUS -> R.string.alert_back_to_focus
                }
                TimerNotifications.postAlert(this, getString(text), null)
            }

            is TimerEvent.SessionFinished -> {
                if (event.summary.completed) {
                    TimerNotifications.postAlert(
                        this,
                        getString(R.string.alert_session_done),
                        getString(R.string.alert_session_done_text, formatDuration(event.summary.focusedMs)),
                    )
                }
                // The widget shows today's focus total.
                lifecycleScope.launch { runCatching { TodayWidget().updateAll(applicationContext) } }
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "app.arcify.timer.PAUSE"
        const val ACTION_RESUME = "app.arcify.timer.RESUME"
        const val ACTION_STOP = "app.arcify.timer.STOP"
        private const val TICK_MS = 1_000L

        /** Call after a session has been started on the controller. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FocusTimerService::class.java))
        }
    }
}
