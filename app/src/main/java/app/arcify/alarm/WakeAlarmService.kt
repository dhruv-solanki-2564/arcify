package app.arcify.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.arcify.R

/**
 * Rings a study wake-up alarm (spec F14): plays the phone's alarm sound with vibration and
 * shows [WakeAlarmActivity] over the lock screen. Only that screen can stop it, by starting a
 * focus session, snoozing (limited) or solving the task. As a safety net it stops by itself
 * after [MAX_RING_MS].
 */
class WakeAlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopRinging() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
                return START_NOT_STICKY
            }

            else -> {
                val alarmId = intent?.getLongExtra(WakeAlarms.EXTRA_ALARM_ID, -1L) ?: -1L
                val snoozesUsed = intent?.getIntExtra(WakeAlarms.EXTRA_SNOOZES_USED, 0) ?: 0
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(alarmId, snoozesUsed), type)
                startRinging()
                handler.removeCallbacks(timeout)
                handler.postDelayed(timeout, MAX_RING_MS)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseSound()
        super.onDestroy()
    }

    private fun startRinging() {
        if (player != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@WakeAlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            null // Vibration still wakes the user.
        }
        vibrator = (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            )?.also { it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)) }
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        releaseSound()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseSound() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun notification(alarmId: Long, snoozesUsed: Int): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_wake_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // The service plays the sound itself, on the alarm stream.
            },
        )
        val screen = PendingIntent.getActivity(
            this,
            0,
            WakeAlarmActivity.intent(this, alarmId, snoozesUsed),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_arcify)
            .setContentTitle(getString(R.string.wake_title))
            .setContentText(getString(R.string.wake_notification_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .build()
    }

    companion object {
        private const val CHANNEL = "wake_alarm"
        private const val NOTIFICATION_ID = 7100
        private const val ACTION_STOP = "app.arcify.alarm.STOP"
        private const val MAX_RING_MS = 10 * 60 * 1000L

        fun ringIntent(context: Context, alarmId: Long, snoozesUsed: Int): Intent =
            Intent(context, WakeAlarmService::class.java)
                .putExtra(WakeAlarms.EXTRA_ALARM_ID, alarmId)
                .putExtra(WakeAlarms.EXTRA_SNOOZES_USED, snoozesUsed)

        fun stop(context: Context) {
            context.startService(Intent(context, WakeAlarmService::class.java).setAction(ACTION_STOP))
        }
    }
}
