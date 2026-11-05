package app.arcify.ui.block

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.arcify.R
import app.arcify.blocker.AppCatalog
import app.arcify.blocker.InstalledApp
import app.arcify.blocker.content.SnapshotRecorder
import app.arcify.blocker.content.AdultDomains
import app.arcify.core.content.AdultFilterSettings
import app.arcify.core.content.IgSurface
import app.arcify.core.content.MonitoredBrowser
import app.arcify.core.content.Platform
import app.arcify.core.content.ShortFormMode
import app.arcify.core.content.ShortFormPolicy
import app.arcify.core.content.ShortFormRule
import app.arcify.core.content.StudyModeSettings
import app.arcify.core.content.WebsitePolicy
import app.arcify.core.content.WebsiteRule
import app.arcify.core.content.WebsiteRuleKind
import app.arcify.core.data.ContentRepository
import app.arcify.core.data.StudyChannelItem
import app.arcify.core.timer.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CountRow(val viaLabel: String, val platform: Platform, val count: Int)

data class BrowserRow(val browser: MonitoredBrowser, val label: String)

data class WebsiteRow(val rule: WebsiteRule, val locked: Boolean)

data class ContentUiState(
    val shortFormRules: List<ShortFormRule> = emptyList(),
    val counts: List<CountRow> = emptyList(),
    val channels: List<StudyChannelItem> = emptyList(),
    val settings: StudyModeSettings = StudyModeSettings(),
    val websites: List<WebsiteRow> = emptyList(),
    val browsers: List<BrowserRow> = emptyList(),
    val adultFilter: AdultFilterSettings = AdultFilterSettings(),
    /** Domains on the bundled adult list; [AdultDomains.NOT_LOADED] while loading, 0 if it failed. */
    val adultListSize: Int = AdultDomains.NOT_LOADED,
    val recorderAvailable: Boolean = false,
    val recorderOn: Boolean = false,
    /** Null while the installed-app list is still loading. */
    val installed: List<InstalledApp>? = null,
    val now: Long = 0,
) {
    fun rule(platform: Platform, surface: IgSurface? = null): ShortFormRule? =
        shortFormRules.firstOrNull { it.platform == platform && it.surface == surface }

    fun isLocked(rule: ShortFormRule?): Boolean = rule != null && ShortFormPolicy.isStrict(rule, now)
}

private data class WebState(
    val websites: List<WebsiteRule>,
    val browsers: List<MonitoredBrowser>,
    val adult: AdultFilterSettings,
    val adultCount: Int,
)

/** Backs the Shorts & Reels, YouTube study mode and Websites & browsers screens (F04–F06). */
@HiltViewModel
class ContentViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val catalog: AppCatalog,
    private val recorder: SnapshotRecorder,
    private val clock: WallClock,
    private val adultDomains: AdultDomains,
) : ViewModel() {

    init {
        viewModelScope.launch { adultDomains.load() }
    }

    private val installed = flow<List<InstalledApp>?> {
        emit(null)
        emit(catalog.launchableApps())
    }.flowOn(Dispatchers.IO)

    private val shortForm = combine(repository.observeShortFormRules(), repository.observeTodayCounts()) { r, c -> r to c }
    private val study = combine(repository.observeStudyChannels(), repository.studySettings) { c, s -> c to s }
    private val web = combine(
        repository.observeWebsiteRules(),
        repository.observeBrowsers(),
        repository.adultFilter,
        adultDomains.count,
    ) { w, b, a, n -> WebState(w, b, a, n) }

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    val ui: StateFlow<ContentUiState> =
        combine(shortForm, study, web, repository.recorderEnabled, installed) { sf, st, wb, recorderOn, apps ->
            val (rules, counts) = sf
            val (channels, settings) = st
            val (websites, browsers, adult, adultCount) = wb
            val labels = apps?.associate { it.packageName to it.label }.orEmpty()
            val now = clock.nowMs()
            ContentUiState(
                shortFormRules = rules,
                counts = counts.mapNotNull { row ->
                    val platform = Platform.entries.firstOrNull { it.name == row.platform } ?: return@mapNotNull null
                    CountRow(labels[row.viaPackage] ?: row.viaPackage, platform, row.n)
                },
                channels = channels,
                settings = settings,
                websites = websites.map { WebsiteRow(it, WebsitePolicy.isStrict(it, now)) },
                browsers = browsers.map { BrowserRow(it, labels[it.packageName] ?: it.packageName) },
                adultFilter = adult,
                adultListSize = adultCount,
                recorderAvailable = recorder.isAvailable,
                recorderOn = recorderOn,
                installed = apps,
                now = now,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContentUiState())

    // ---- Shorts & Reels ----

    fun setRule(platform: Platform, surface: IgSurface?, mode: ShortFormMode, limit: Int?) = launchChecked {
        val current = ui.value.rule(platform, surface)
        repository.saveShortFormRule(
            ShortFormRule(platform, surface, mode, if (mode == ShortFormMode.LIMIT) limit ?: DEFAULT_LIMIT else null, current?.strictUntil),
        )
    }

    /** Removes an Instagram surface rule, so the surface follows the Instagram-wide rule again. */
    fun inheritSurface(surface: IgSurface) = launchChecked { repository.deleteShortFormRule(Platform.INSTAGRAM, surface) }

    fun lockRule(platform: Platform, surface: IgSurface?) = launchChecked { repository.lockShortFormRuleForToday(platform, surface) }

    fun setRecorder(enabled: Boolean) = viewModelScope.launch { repository.setRecorderEnabled(enabled) }

    // ---- YouTube study mode ----

    fun setYoutubeStudyMode(enabled: Boolean) = viewModelScope.launch { repository.setYoutubeStudyMode(enabled) }

    fun addChannel(input: String) = launchChecked(R.string.channel_invalid) { repository.addStudyChannel(input) }

    fun removeChannel(id: Long) = viewModelScope.launch { repository.removeStudyChannel(id) }

    fun resetChannels() = viewModelScope.launch { repository.resetStudyChannels() }

    // ---- Websites & browsers ----

    fun setBrowserStudyMode(enabled: Boolean) = viewModelScope.launch { repository.setBrowserStudyMode(enabled) }

    fun addWebsite(pattern: String, kind: WebsiteRuleKind) = launchChecked(R.string.website_invalid) {
        repository.saveWebsiteRule(pattern, kind)
    }

    fun removeWebsite(pattern: String) = launchChecked { repository.deleteWebsiteRule(pattern) }

    fun lockWebsite(pattern: String) = launchChecked { repository.lockWebsiteRuleForToday(pattern) }

    /** Chromium-based browsers name their address bar `url_bar`; other browsers may need editing later. */
    fun addBrowser(packageName: String) = viewModelScope.launch {
        repository.saveBrowser(MonitoredBrowser(packageName, listOf("$packageName:id/url_bar")))
    }

    fun removeBrowser(packageName: String) = viewModelScope.launch { repository.removeBrowser(packageName) }

    fun resetBrowsers() = viewModelScope.launch { repository.resetBrowsers() }

    // ---- Adult-site filter ----

    fun setAdultFilter(enabled: Boolean) = launchChecked { repository.setAdultFilterEnabled(enabled) }

    fun lockAdultFilter() = viewModelScope.launch { repository.lockAdultFilterForToday() }

    fun addAdultException(input: String) = launchChecked(R.string.adult_exception_refused) { repository.addAdultException(input) }

    fun removeAdultException(domain: String) = viewModelScope.launch { repository.removeAdultException(domain) }

    private fun launchChecked(@StringRes failure: Int = R.string.limit_locked_message, action: suspend () -> Boolean) =
        viewModelScope.launch {
            if (!action()) _messages.tryEmit(failure)
        }

    private companion object {
        const val DEFAULT_LIMIT = 10
    }
}
