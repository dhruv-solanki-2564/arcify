package app.arcify

import android.app.Application
import app.arcify.timer.TimerNotifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ArcifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TimerNotifications.createChannels(this)
    }
}
