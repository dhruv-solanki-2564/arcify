package app.arcify.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
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
import app.arcify.blocker.protection.Threat
import app.arcify.core.content.IgSurface
import app.arcify.core.content.Platform
import app.arcify.core.timer.ActiveTimer
import app.arcify.core.timer.TimerController
import app.arcify.core.util.formatDuration
import app.arcify.core.util.formatShortDuration
import app.arcify.ui.theme.ArcifyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * Full-screen cover shown when something is blocked. App-level blocks leave to the home screen
 * or the focus screen; content blocks just close, because the engine already navigated away
 * from the blocked content.
 */
@AndroidEntryPoint
class BlockOverlayActivity : ComponentActivity() {

    @Inject lateinit var timerController: TimerController

    private var content by mutableStateOf<BlockContent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        content = read(intent)
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
        content = read(intent)
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

            BlockReason.SHORT_FORM, BlockReason.SHORT_FORM_LIMIT, BlockReason.CHANNEL, BlockReason.WEBSITE,
            BlockReason.PROTECTION -> Unit
        }
        finish()
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_USED = "used_ms"
        private const val EXTRA_LIMIT = "limit_ms"
        private const val EXTRA_PLATFORM = "platform"
        private const val EXTRA_SURFACE = "surface"
        private const val EXTRA_WATCHED = "watched"
        private const val EXTRA_ITEM_LIMIT = "item_limit"
        private const val EXTRA_DETAIL = "detail"
        private const val EXTRA_STUDY = "study_mode"

        fun strictFocus(context: Context, packageName: String, label: String): Intent =
            base(context, BlockReason.STRICT_FOCUS, packageName).putExtra(EXTRA_LABEL, label)

        fun limitReached(context: Context, packageName: String, label: String, usedMs: Long, limitMs: Long): Intent =
            base(context, BlockReason.LIMIT, packageName)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_USED, usedMs)
                .putExtra(EXTRA_LIMIT, limitMs)

        fun shortForm(context: Context, packageName: String, platform: Platform, surface: IgSurface?): Intent =
            base(context, BlockReason.SHORT_FORM, packageName)
                .putExtra(EXTRA_PLATFORM, platform.name)
                .putExtra(EXTRA_SURFACE, surface?.name)

        fun shortFormLimit(
            context: Context,
            packageName: String,
            platform: Platform,
            surface: IgSurface?,
            watched: Int,
            limit: Int,
        ): Intent = base(context, BlockReason.SHORT_FORM_LIMIT, packageName)
            .putExtra(EXTRA_PLATFORM, platform.name)
            .putExtra(EXTRA_SURFACE, surface?.name)
            .putExtra(EXTRA_WATCHED, watched)
            .putExtra(EXTRA_ITEM_LIMIT, limit)

        fun channel(context: Context, packageName: String, channel: String): Intent =
            base(context, BlockReason.CHANNEL, packageName).putExtra(EXTRA_DETAIL, channel)

        fun website(context: Context, packageName: String, host: String, studyMode: Boolean): Intent =
            base(context, BlockReason.WEBSITE, packageName)
                .putExtra(EXTRA_DETAIL, host)
                .putExtra(EXTRA_STUDY, studyMode)

        fun protection(context: Context, threat: Threat): Intent =
            base(context, BlockReason.PROTECTION, context.packageName).putExtra(EXTRA_DETAIL, threat.name)

        private fun base(context: Context, reason: BlockReason, packageName: String): Intent =
            Intent(context, BlockOverlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_REASON, reason.name)
                .putExtra(EXTRA_PACKAGE, packageName)

        private fun read(intent: Intent?): BlockContent? {
            val reason = intent?.getStringExtra(EXTRA_REASON)
                ?.let { name -> BlockReason.entries.firstOrNull { it.name == name } } ?: return null
            return BlockContent(
                reason = reason,
                packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty(),
                label = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
                usedMs = intent.getLongExtra(EXTRA_USED, 0),
                limitMs = intent.getLongExtra(EXTRA_LIMIT, 0),
                platform = intent.getStringExtra(EXTRA_PLATFORM)?.let { name -> Platform.entries.firstOrNull { it.name == name } },
                surface = intent.getStringExtra(EXTRA_SURFACE)?.let { name -> IgSurface.entries.firstOrNull { it.name == name } },
                watched = intent.getIntExtra(EXTRA_WATCHED, 0),
                itemLimit = intent.getIntExtra(EXTRA_ITEM_LIMIT, 0),
                detail = intent.getStringExtra(EXTRA_DETAIL).orEmpty(),
                studyMode = intent.getBooleanExtra(EXTRA_STUDY, false),
            )
        }
    }
}

enum class BlockReason { LIMIT, STRICT_FOCUS, SHORT_FORM, SHORT_FORM_LIMIT, CHANNEL, WEBSITE, PROTECTION }

data class BlockContent(
    val reason: BlockReason,
    val packageName: String,
    val label: String = "",
    val usedMs: Long = 0,
    val limitMs: Long = 0,
    val platform: Platform? = null,
    val surface: IgSurface? = null,
    val watched: Int = 0,
    val itemLimit: Int = 0,
    val detail: String = "",
    val studyMode: Boolean = false,
)

/** "Instagram Reels", "YouTube Shorts"… */
@StringRes
fun Platform.shortFormName(): Int = when (this) {
    Platform.INSTAGRAM -> R.string.shortform_instagram
    Platform.YOUTUBE -> R.string.shortform_youtube
    Platform.SNAPCHAT -> R.string.shortform_snapchat
    Platform.FACEBOOK -> R.string.shortform_facebook
}

@StringRes
fun IgSurface.label(): Int = when (this) {
    IgSurface.REELS_TAB -> R.string.surface_reels_tab
    IgSurface.FEED -> R.string.surface_feed
    IgSurface.EXPLORE -> R.string.surface_explore
    IgSurface.DM -> R.string.surface_dm
    IgSurface.UNKNOWN -> R.string.surface_unknown
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
            val shortFormName = content.platform?.let { stringResource(it.shortFormName()) }.orEmpty()
            when (content.reason) {
                BlockReason.LIMIT -> {
                    Title(stringResource(R.string.block_limit_title, content.label))
                    Body(
                        stringResource(
                            R.string.block_limit_body,
                            formatShortDuration(content.usedMs),
                            formatShortDuration(content.limitMs),
                        ),
                    )
                    Button(onClick = onLeave) { Text(stringResource(R.string.block_close_app)) }
                }

                BlockReason.STRICT_FOCUS -> {
                    Title(stringResource(R.string.block_strict_title))
                    Body(stringResource(R.string.block_strict_body, content.label))
                    timer?.remainingMs(clock)?.let { remaining ->
                        Text(
                            stringResource(R.string.block_strict_remaining, formatDuration(remaining)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Button(onClick = onLeave) { Text(stringResource(R.string.block_back_to_focus)) }
                }

                BlockReason.SHORT_FORM -> {
                    Title(stringResource(R.string.block_shortform_title, shortFormName))
                    val surface = content.surface?.takeIf { it != IgSurface.UNKNOWN }
                    Body(
                        if (surface != null) {
                            stringResource(R.string.block_shortform_body_surface, stringResource(surface.label()))
                        } else {
                            stringResource(R.string.block_shortform_body)
                        },
                    )
                    Button(onClick = onLeave) { Text(stringResource(R.string.block_ok)) }
                }

                BlockReason.SHORT_FORM_LIMIT -> {
                    Title(stringResource(R.string.block_shortform_limit_title))
                    Body(stringResource(R.string.block_shortform_limit_body, content.watched, shortFormName, content.itemLimit))
                    Button(onClick = onLeave) { Text(stringResource(R.string.block_ok)) }
                }

                BlockReason.CHANNEL -> {
                    Title(stringResource(R.string.block_channel_title))
                    Body(stringResource(R.string.block_channel_body, content.detail))
                    Button(onClick = onLeave) { Text(stringResource(R.string.block_ok)) }
                }

                BlockReason.WEBSITE -> {
                    Title(stringResource(R.string.block_website_title, content.detail))
                    Body(
                        stringResource(
                            if (content.studyMode) R.string.block_website_body_study else R.string.block_website_body,
                        ),
                    )
                    Button(onClick = onLeave) { Text(stringResource(R.string.block_ok)) }
                }

                BlockReason.PROTECTION -> {
                    Title(stringResource(R.string.block_protection_title))
                    Body(
                        stringResource(
                            when (content.detail) {
                                Threat.UNINSTALL.name, Threat.STORE.name -> R.string.block_protection_uninstall
                                else -> R.string.block_protection_settings
                            },
                        ),
                    )
                    Button(onClick = onLeave) { Text(stringResource(R.string.block_ok)) }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
}
