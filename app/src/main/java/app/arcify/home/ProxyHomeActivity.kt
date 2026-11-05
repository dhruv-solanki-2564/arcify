package app.arcify.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.arcify.MainActivity
import app.arcify.core.data.ProtectionRepository
import app.arcify.core.protection.HomeRoute
import app.arcify.core.protection.HomeRouting
import app.arcify.core.timer.TimerController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Arcify's home-screen entry, used only when the user makes Arcify the home app. It shows
 * nothing itself: Home goes to the focus screen during strict focus, otherwise to the user's own
 * launcher, or to a chooser if that launcher is gone. Home therefore always leads somewhere.
 */
@AndroidEntryPoint
class ProxyHomeActivity : ComponentActivity() {

    @Inject lateinit var timerController: TimerController
    @Inject lateinit var protection: ProtectionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route()
    }

    private fun route() {
        lifecycleScope.launch {
            val chosen = protection.settings.first().homeLauncher
            val installed = HomeSetup.otherLaunchers(this@ProxyHomeActivity).map { it.packageName }.toSet()
            when (val route = HomeRouting.route(timerController.timer.value, chosen, installed)) {
                HomeRoute.Focus -> startActivity(
                    Intent(this@ProxyHomeActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )

                is HomeRoute.Launcher -> if (!HomeSetup.startLauncher(this@ProxyHomeActivity, route.packageName)) openChooser()
                HomeRoute.Chooser -> openChooser()
            }
            finish()
        }
    }

    private fun openChooser() {
        startActivity(Intent(this, LauncherChooserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
