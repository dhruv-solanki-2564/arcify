package app.arcify.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.arcify.core.protection.Commitments
import app.arcify.core.protection.ProtectionLevel
import app.arcify.core.protection.ProtectionRules
import app.arcify.core.protection.ProtectionSettings
import app.arcify.core.timer.TimerController
import app.arcify.core.timer.WallClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Live view of what the user is committed to (strict session, locked rules). */
@Singleton
class CommitmentTracker @Inject constructor(
    timer: TimerController,
    blocks: BlockRepository,
    content: ContentRepository,
    private val clock: WallClock,
) {
    // Locks expire by time alone, so re-evaluate periodically too.
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    private val contentRules = combine(
        content.observeShortFormRules(),
        content.observeWebsiteRules(),
        content.adultFilter,
    ) { shortForm, websites, adult -> Triple(shortForm, websites, adult) }

    val commitments: Flow<Commitments> = combine(
        timer.timer,
        blocks.observeLimits(),
        contentRules,
        ticks,
    ) { t, limits, (shortForm, websites, adult), _ ->
        ProtectionRules.commitments(t, limits, shortForm, websites, clock.nowMs(), adult)
    }.distinctUntilChanged()

    suspend fun current(): Commitments = commitments.first()
}

/** Protection settings. Every change that weakens protection is refused while commitments last. */
@Singleton
class ProtectionRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val tracker: CommitmentTracker,
) {
    val settings: Flow<ProtectionSettings> = store.data.map { prefs ->
        ProtectionSettings(
            level = prefs[LEVEL]?.let { name -> ProtectionLevel.entries.firstOrNull { it.name == name } }
                ?: ProtectionRules.DEFAULT_LEVEL,
            hardExitEnabled = prefs[HARD_EXIT] ?: false,
            homeHidingEnabled = prefs[HOME_HIDING] ?: false,
            homeLauncher = prefs[HOME_LAUNCHER],
        )
    }

    suspend fun setLevel(level: ProtectionLevel): Boolean {
        val current = settings.first().level
        if (!ProtectionRules.canChangeLevel(current, level, tracker.current())) return false
        store.edit { it[LEVEL] = level.name }
        return true
    }

    suspend fun setHardExitEnabled(enabled: Boolean): Boolean {
        if (enabled && !ProtectionRules.canWeaken(tracker.current())) return false
        store.edit { it[HARD_EXIT] = enabled }
        return true
    }

    suspend fun setHomeHidingEnabled(enabled: Boolean): Boolean {
        if (!enabled && !ProtectionRules.canWeaken(tracker.current())) return false
        store.edit { it[HOME_HIDING] = enabled }
        return true
    }

    suspend fun setHomeLauncher(packageName: String) {
        store.edit { it[HOME_LAUNCHER] = packageName }
    }

    /** For actions handled outside this repository (e.g. switching off device admin). */
    suspend fun canWeaken(): Boolean = ProtectionRules.canWeaken(tracker.current())

    private companion object {
        val LEVEL = stringPreferencesKey("protection_level")
        val HARD_EXIT = booleanPreferencesKey("hard_exit_enabled")
        val HOME_HIDING = booleanPreferencesKey("home_hiding_enabled")
        val HOME_LAUNCHER = stringPreferencesKey("home_launcher_package")
    }
}
