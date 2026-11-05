package app.arcify.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.arcify.MainActivity
import app.arcify.R
import app.arcify.core.timer.ActiveTimer
import app.arcify.core.timer.TimerController
import app.arcify.core.util.formatDuration
import app.arcify.core.util.formatShortDuration
import app.arcify.ui.theme.ArcifyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

/** Full-screen cover shown over a blocked app. Leaving it never returns to the blocked app. */
@AndroidEntryPoint
class BlockOverlayActivity : ComponentActivity() {

    @Inject lateinit var timerController: TimerController

    private var content by mutableStateOf<BlockContent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        content = BlockContent.from(intent)
        setContent {
            ArcifyTheme {
                val current = content ?: return@ArcifyTheme
                val timer by timerController.timer.collectAsStateWithLifecycle()
                BackHandler { leave(current) }
                BlockOverlayScreen(current, timer, now = { timerController.now() }, onLeave = { leave(current) })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        content = BlockContent.from(intent)
    }

    private fun leave(content: BlockContent) {
        when (content.reason) {
            BlockReason.LIMIT -> startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

            BlockReason.STRICT_FOCUS -> startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_USED = "used_ms"
        private const val EXTRA_LIMIT = "limit_ms"

        fun strictFocus(context: Context, packageName: String, label: String): Intent =
            base(context, BlockReason.STRICT_FOCUS, packageName, label)

        fun limitReached(context: Context, packageName: String, label: String, usedMs: Long, limitMs: Long): Intent =
            base(context, BlockReason.LIMIT, packageName, label)
                .putExtra(EXTRA_USED, usedMs)
                .putExtra(EXTRA_LIMIT, limitMs)

        private fun base(context: Context, reason: BlockReason, packageName: String, label: String): Intent =
            Intent(context, BlockOverlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_REASON, reason.name)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_LABEL, label)

        internal fun read(intent: Intent?): BlockContent? {
            val reason = intent?.getStringExtra(EXTRA_REASON)
                ?.let { name -> BlockReason.entries.firstOrNull { it.name == name } } ?: return null
            return BlockContent(
                reason = reason,
                packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty(),
                label = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
                usedMs = intent.getLongExtra(EXTRA_USED, 0),
                limitMs = intent.getLongExtra(EXTRA_LIMIT, 0),
            )
        }
    }
}

enum class BlockReason { LIMIT, STRICT_FOCUS }

data class BlockContent(
    val reason: BlockReason,
    val packageName: String,
    val label: String,
    val usedMs: Long,
    val limitMs: Long,
) {
    companion object {
        fun from(intent: Intent?): BlockContent? = BlockOverlayActivity.read(intent)
    }
}

@Composable
private fun BlockOverlayScreen(content: BlockContent, timer: ActiveTimer?, now: () -> Long, onLeave: () -> Unit) {
    var clock by remember { mutableLongStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = now()
            delay(1_000)
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            when (content.reason) {
                BlockReason.LIMIT -> {
                    Text(
                        stringResource(R.string.block_limit_title, content.label),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(
                            R.string.block_limit_body,
                            formatShortDuration(content.usedMs),
                            formatShortDuration(content.limitMs),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onLeave) { Text(stringResource(R.string.block_close_app)) }
                }

                BlockReason.STRICT_FOCUS -> {
                    Text(
                        stringResource(R.string.block_strict_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.block_strict_body, content.label),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    timer?.remainingMs(clock)?.let { remaining ->
                        Text(
                            stringResource(R.string.block_strict_remaining, formatDuration(remaining)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Button(onClick = onLeave) { Text(stringResource(R.string.block_back_to_focus)) }
                }
            }
        }
    }
}
