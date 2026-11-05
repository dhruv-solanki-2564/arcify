package app.arcify.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.arcify.core.content.ContentDefaults
import app.arcify.core.content.IgSurface
import app.arcify.core.content.MonitoredBrowser
import app.arcify.core.content.Platform
import app.arcify.core.content.ShortFormCounts
import app.arcify.core.content.ShortFormPolicy
import app.arcify.core.content.ShortFormRule
import app.arcify.core.content.ShortFormSighting
import app.arcify.core.content.StudyChannel
import app.arcify.core.content.StudyChannelPolicy
import app.arcify.core.content.StudyModeSettings
import app.arcify.core.content.WebsitePolicy
import app.arcify.core.content.WebsiteRule
import app.arcify.core.content.WebsiteRuleKind
import app.arcify.core.timer.DayBounds
import app.arcify.core.timer.WallClock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A study channel with its row ID, so the UI can remove it. */
data class StudyChannelItem(val id: Long, val channel: StudyChannel)

/**
 * Rules and lists for content blocking (spec F04–F06): short-form rules and counts, the
 * YouTube study list, website rules, monitored browsers and the study-mode switches.
 */
@Singleton
class ContentRepository @Inject constructor(
    private val dao: ContentDao,
    private val store: DataStore<Preferences>,
    private val clock: WallClock,
) {
    /** Seeds the bundled defaults once. Lists the user later empties stay empty. */
    suspend fun ensureDefaults() {
        if (store.data.first()[DEFAULTS_SEEDED] == true) return
        insertDefaultChannels()
        dao.insertBrowsers(ContentDefaults.browsers.map(MonitoredBrowserEntity::from))
        store.edit { it[DEFAULTS_SEEDED] = true }
    }

    // ---- Short-form rules ----

    fun observeShortFormRules(): Flow<List<ShortFormRule>> =
        dao.observeShortFormRules().map { rows -> rows.map { it.toModel() } }

    /** Saves the rule unless a strict lock forbids the change. */
    suspend fun saveShortFormRule(rule: ShortFormRule): Boolean {
        val current = dao.getShortFormRule(rule.platform.name, rule.surface.toKey())?.toModel()
        if (current != null && !ShortFormPolicy.canChange(current, rule, clock.nowMs())) return false
        dao.upsertShortFormRule(ShortFormRuleEntity.from(rule))
        return true
    }

    suspend fun deleteShortFormRule(platform: Platform, surface: IgSurface?): Boolean {
        val current = dao.getShortFormRule(platform.name, surface.toKey())?.toModel() ?: return true
        if (!ShortFormPolicy.canChange(current, null, clock.nowMs())) return false
        dao.deleteShortFormRule(platform.name, surface.toKey())
        return true
    }

    suspend fun lockShortFormRuleForToday(platform: Platform, surface: IgSurface?): Boolean {
        val current = dao.getShortFormRule(platform.name, surface.toKey())?.toModel() ?: return false
        return saveShortFormRule(current.copy(strictUntil = maxOf(current.strictUntil ?: Long.MIN_VALUE, endOfToday())))
    }

    // ---- Counter ----

    suspend fun record(sighting: ShortFormSighting) {
        dao.insertShortFormEvent(
            ShortFormEventEntity(
                day = dayOf(sighting.atMs),
                viaPackage = sighting.viaPackage,
                platform = sighting.platform.name,
                surfaceKey = sighting.surface.toKey(),
                itemKey = sighting.itemKey,
                atMs = sighting.atMs,
            ),
        )
    }

    /** Today's counts for [platform], ready for [ShortFormPolicy.decide]. */
    suspend fun countsToday(platform: Platform): ShortFormCounts {
        val rows = dao.surfaceCounts(dayOf(clock.nowMs()), platform.name)
        val bySurface = rows.associate { it.surfaceKey to it.n }
        val total = rows.sumOf { it.n }
        return ShortFormCounts { p, surface ->
            when {
                p != platform -> 0
                surface == null -> total
                else -> bySurface[surface.name] ?: 0
            }
        }
    }

    /** Counts per app or browser and platform for today. The day is fixed when this is called. */
    fun observeTodayCounts(): Flow<List<SourceCount>> = dao.observeSourceCounts(dayOf(clock.nowMs()))

    // ---- YouTube study list ----

    fun observeStudyChannels(): Flow<List<StudyChannelItem>> =
        dao.observeStudyChannels().map { rows -> rows.map { StudyChannelItem(it.id, it.toModel()) } }

    /** Adds a channel from what the user typed (name, @handle or link). Returns false for blank input. */
    suspend fun addStudyChannel(input: String): Boolean {
        val channel = StudyChannelPolicy.parseInput(input) ?: return false
        val position = dao.maxStudyChannelPosition() + 1
        dao.insertStudyChannels(listOf(StudyChannelEntity(0, channel.displayName, channel.handle, false, position)))
        return true
    }

    suspend fun removeStudyChannel(id: Long) = dao.deleteStudyChannel(id)

    suspend fun resetStudyChannels() {
        dao.clearStudyChannels()
        insertDefaultChannels()
    }

    // ---- Websites ----

    fun observeWebsiteRules(): Flow<List<WebsiteRule>> =
        dao.observeWebsiteRules().map { rows -> rows.map { it.toModel() } }

    /** Adds or replaces a rule. Returns false for a blank pattern or when the existing rule is locked. */
    suspend fun saveWebsiteRule(pattern: String, kind: WebsiteRuleKind): Boolean {
        val cleaned = pattern.trim().lowercase()
        if (cleaned.isEmpty() || cleaned.any { it.isWhitespace() }) return false
        val current = dao.getWebsiteRule(cleaned)?.toModel()
        if (current != null && WebsitePolicy.isStrict(current, clock.nowMs()) && current.kind != kind) return false
        dao.upsertWebsiteRule(WebsiteRuleEntity(cleaned, kind.name, current?.strictUntil))
        return true
    }

    suspend fun deleteWebsiteRule(pattern: String): Boolean {
        val current = dao.getWebsiteRule(pattern)?.toModel() ?: return true
        if (WebsitePolicy.isStrict(current, clock.nowMs())) return false
        dao.deleteWebsiteRule(pattern)
        return true
    }

    suspend fun lockWebsiteRuleForToday(pattern: String): Boolean {
        val current = dao.getWebsiteRule(pattern)?.toModel() ?: return false
        dao.upsertWebsiteRule(
            WebsiteRuleEntity(current.pattern, current.kind.name, maxOf(current.strictUntil ?: Long.MIN_VALUE, endOfToday())),
        )
        return true
    }

    // ---- Browsers ----

    fun observeBrowsers(): Flow<List<MonitoredBrowser>> =
        dao.observeBrowsers().map { rows -> rows.map { it.toModel() } }

    suspend fun saveBrowser(browser: MonitoredBrowser) =
        dao.insertBrowsers(listOf(MonitoredBrowserEntity.from(browser)))

    suspend fun removeBrowser(packageName: String) = dao.deleteBrowser(packageName)

    suspend fun resetBrowsers() {
        dao.clearBrowsers()
        dao.insertBrowsers(ContentDefaults.browsers.map(MonitoredBrowserEntity::from))
    }

    // ---- Switches ----

    val studySettings: Flow<StudyModeSettings> = store.data.map { prefs ->
        StudyModeSettings(
            youtubeStudyMode = prefs[YOUTUBE_STUDY] ?: false,
            browserStudyMode = prefs[BROWSER_STUDY] ?: false,
        )
    }

    suspend fun setYoutubeStudyMode(enabled: Boolean) = store.edit { it[YOUTUBE_STUDY] = enabled }

    suspend fun setBrowserStudyMode(enabled: Boolean) = store.edit { it[BROWSER_STUDY] = enabled }

    /** Debug-only screen recorder used to calibrate detectors on a real phone. */
    val recorderEnabled: Flow<Boolean> = store.data.map { it[RECORDER] ?: false }

    suspend fun setRecorderEnabled(enabled: Boolean) = store.edit { it[RECORDER] = enabled }

    // ---- Helpers ----

    private suspend fun insertDefaultChannels() {
        dao.insertStudyChannels(
            ContentDefaults.studyChannels.mapIndexed { index, channel ->
                StudyChannelEntity(0, channel.displayName, channel.handle, true, index)
            },
        )
    }

    private fun dayOf(ms: Long): Long = DayBounds.of(ms, ZoneId.systemDefault()).startMs

    private fun endOfToday(): Long = DayBounds.of(clock.nowMs(), ZoneId.systemDefault()).endMs

    private companion object {
        val DEFAULTS_SEEDED = booleanPreferencesKey("content_defaults_seeded_v1")
        val YOUTUBE_STUDY = booleanPreferencesKey("youtube_study_mode")
        val BROWSER_STUDY = booleanPreferencesKey("browser_study_mode")
        val RECORDER = booleanPreferencesKey("debug_snapshot_recorder")
    }
}
