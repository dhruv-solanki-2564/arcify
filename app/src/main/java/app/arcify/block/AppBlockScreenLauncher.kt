package app.arcify.block

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import app.arcify.MainActivity
import app.arcify.blocker.AppCatalog
import app.arcify.blocker.BlockScreenLauncher
import app.arcify.blocker.protection.Threat
import app.arcify.core.block.BlockDecision
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** The app's side of the blocking engine: opens block screens and posts limit reminders. */
@Singleton
class AppBlockScreenLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val catalog: AppCatalog,
) : BlockScreenLauncher {

    override fun showBlock(decision: BlockDecision) {
        val intent = when (decision) {
            is BlockDecision.StrictFocus ->
                BlockOverlayActivity.strictFocus(context, decision.packageName, catalog.label(decision.packageName))

            is BlockDecision.LimitReached -> BlockOverlayActivity.limitReached(
                context,
                decision.packageName,
                catalog.label(decision.packageName),
                decision.usedMs,
                decision.limitMs,
            )

            BlockDecision.Allow, BlockDecision.ReturnToFocus -> return
        }
        val pkg = when (decision) {
            is BlockDecision.StrictFocus -> decision.packageName
            is BlockDecision.LimitReached -> decision.packageName
            else -> ""
        }
        // Keyed by type and app only: counters inside the decision change on every check.
        show("app:${decision::class.simpleName}:$pkg", intent)
    }

    override fun returnToFocus() {
        show(
            "focus",
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION,
            ),
        )
    }

    private var lastKey: String? = null
    private var lastShownAt = 0L

    /**
     * Opens [intent] unless the same screen was opened a moment ago: repeated identical block
     * screens are what made the phone flicker.
     */
    @Synchronized
    private fun show(key: String, intent: Intent) {
        val now = SystemClock.uptimeMillis()
        if (key == lastKey && now - lastShownAt < REPEAT_GUARD_MS) return
        lastKey = key
        lastShownAt = now
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    private companion object {
        const val REPEAT_GUARD_MS = 1_500L
    }

    override fun limitReminder(packageName: String, usedMs: Long, limitMs: Long) {
        BlockNotifications.postReminder(context, packageName, catalog.label(packageName), usedMs, limitMs)
    }

    override fun showProtection(threat: Threat) {
        show("protection:$threat", BlockOverlayActivity.protection(context, threat))
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BlockModule {
    @Binds
    abstract fun blockScreenLauncher(impl: AppBlockScreenLauncher): BlockScreenLauncher
}
