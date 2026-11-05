package app.arcify.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.arcify.R
import app.arcify.core.data.ProtectionRepository
import app.arcify.ui.theme.ArcifyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Lets the user pick the launcher Arcify forwards Home to. Shown if none is chosen or it was removed. */
@AndroidEntryPoint
class LauncherChooserActivity : ComponentActivity() {

    @Inject lateinit var protection: ProtectionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launchers = HomeSetup.otherLaunchers(this)
        setContent {
            ArcifyTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.launcher_chooser_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.launcher_chooser_body), style = MaterialTheme.typography.bodyMedium)
                        LazyColumn(Modifier.weight(1f)) {
                            items(launchers, key = { it.packageName }) { app ->
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            lifecycleScope.launch {
                                                protection.setHomeLauncher(app.packageName)
                                                HomeSetup.startLauncher(this@LauncherChooserActivity, app.packageName)
                                                finish()
                                            }
                                        }
                                        .padding(vertical = 14.dp),
                                )
                            }
                        }
                        OutlinedButton(onClick = { startActivity(HomeSetup.homeSettingsIntent()) }) {
                            Text(stringResource(R.string.launcher_open_home_settings))
                        }
                    }
                }
            }
        }
    }
}
