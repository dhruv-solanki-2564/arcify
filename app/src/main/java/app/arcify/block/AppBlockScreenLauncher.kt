package app.arcify.block

import android.content.Context
import android.content.Intent
import app.arcify.MainActivity
import app.arcify.blocker.AppCatalog
import app.arcify.blocker.BlockScreenLauncher
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
        context.startActivity(intent)
    }

    override fun returnToFocus() {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
    }

    override fun limitReminder(packageName: String, usedMs: Long, limitMs: Long) {
        BlockNotifications.postReminder(context, packageName, catalog.label(packageName), usedMs, limitMs)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BlockModule {
    @Binds
    abstract fun blockScreenLauncher(impl: AppBlockScreenLauncher): BlockScreenLauncher
}
