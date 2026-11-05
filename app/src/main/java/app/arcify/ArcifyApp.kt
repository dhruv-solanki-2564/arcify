package app.arcify

import android.app.Application
import app.arcify.block.BlockNotifications
import app.arcify.core.data.ContentRepository
import app.arcify.timer.TimerNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ArcifyApp : Application() {

    @Inject lateinit var contentRepository: ContentRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.createChannels(this)
        BlockNotifications.createChannel(this)
        // First run: the default study channels and browsers (spec F05/F06).
        appScope.launch { contentRepository.ensureDefaults() }
    }
}
