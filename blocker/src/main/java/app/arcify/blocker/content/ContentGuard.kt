package app.arcify.blocker.content

import app.arcify.blocker.Bounds
import app.arcify.blocker.NodeSnapshot
import app.arcify.core.content.ContentDefaults
import app.arcify.core.content.IgSurface
import app.arcify.core.content.MonitoredBrowser
import app.arcify.core.content.Platform
import app.arcify.core.content.ShortFormCounts
import app.arcify.core.content.ShortFormDecision
import app.arcify.core.content.ShortFormDeduper
import app.arcify.core.content.ShortFormMode
import app.arcify.core.content.ShortFormPolicy
import app.arcify.core.content.ShortFormRule
import app.arcify.core.content.ShortFormSighting
import app.arcify.core.content.ShortFormUrls
import app.arcify.core.content.StudyChannel
import app.arcify.core.content.StudyChannelPolicy
import app.arcify.core.content.StudyModeSettings
import app.arcify.core.content.StudyModes
import app.arcify.core.content.UrlNormalizer
import app.arcify.core.content.WebsiteDecision
import app.arcify.core.content.WebsitePolicy
import app.arcify.core.content.WebsiteRule
import app.arcify.core.timer.ActiveTimer

/** Why content inside an app was blocked. */
sealed interface ContentBlock {
    data class ShortForm(val platform: Platform, val surface: IgSurface?) : ContentBlock

    data class ShortFormLimit(val platform: Platform, val surface: IgSurface?, val watched: Int, val limit: Int) : ContentBlock

    /** YouTube study mode: this channel isn't on the study list. */
    data class Channel(val channel: String) : ContentBlock

    data class Website(val host: String, val studyMode: Boolean) : ContentBlock
}

/** Everything [ContentGuard] needs to decide, gathered by the service. */
data class ContentState(
    val shortFormRules: List<ShortFormRule> = emptyList(),
    val websiteRules: List<WebsiteRule> = emptyList(),
    val studyChannels: List<StudyChannel> = emptyList(),
    val settings: StudyModeSettings = StudyModeSettings(),
    val browsers: List<MonitoredBrowser> = emptyList(),
    val timer: ActiveTimer? = null,
)

/** Where counted items are read from and written to. The repository in production, a fake in tests. */
interface ShortFormStore {
    suspend fun countsToday(platform: Platform): ShortFormCounts
    suspend fun record(sighting: ShortFormSighting)
}

/**
 * Looks at one screen of a monitored app or browser and decides whether its content is blocked.
 * It also counts short-form items (spec F04–F06). Holds per-app memory: the deduper and
 * Instagram surface trail.
 */
class ContentGuard(
    private val store: ShortFormStore,
    private val shortFormHints: ShortFormHints = DetectorDefaults.shortForm,
    instagramHints: InstagramHints = DetectorDefaults.instagram,
    private val youtubeHints: YouTubeHints = DetectorDefaults.youtube,
) {
    private val deduper = ShortFormDeduper()
    private val igTracker = IgSurfaceTracker(instagramHints)

    /** True if [packageName] is one this guard looks inside. */
    fun monitors(packageName: String, state: ContentState): Boolean =
        packageName in ContentDefaults.shortFormApps || state.browsers.any { it.packageName == packageName }

    suspend fun inspect(packageName: String, root: NodeSnapshot, screen: Bounds, now: Long, state: ContentState): ContentBlock? {
        state.browsers.firstOrNull { it.packageName == packageName }?.let { browser ->
            return inspectBrowser(packageName, root, browser, now, state)
        }
        val platform = ContentDefaults.shortFormApps[packageName] ?: return null
        val player = ShortFormDetector.detect(root, screen, shortFormHints)
        if (player == null) {
            if (platform == Platform.INSTAGRAM) igTracker.observe(root)
            if (platform == Platform.YOUTUBE && StudyModes.youtubeActive(state.timer, state.settings)) {
                val channel = YouTubeChannelDetector.channelOnWatchPage(root, youtubeHints)
                if (channel != null && !StudyChannelPolicy.isAllowed(channel, state.studyChannels)) {
                    return ContentBlock.Channel(channel)
                }
            }
            return null
        }
        val surface = if (platform == Platform.INSTAGRAM) igTracker.surfaceForPlayer(root) else null
        return handleShortForm(ShortFormSighting(platform, packageName, surface, player.itemKey, now), state)
    }

    private suspend fun inspectBrowser(
        packageName: String,
        root: NodeSnapshot,
        browser: MonitoredBrowser,
        now: Long,
        state: ContentState,
    ): ContentBlock? {
        val url = UrlNormalizer.normalize(BrowserUrlDetector.read(root, browser)) ?: return null
        val studyMode = StudyModes.browserActive(state.timer, state.settings)
        val site = WebsitePolicy.decide(url, state.websiteRules, studyMode)
        if (site is WebsiteDecision.Blocked) return ContentBlock.Website(site.host, studyMode)
        val hit = ShortFormUrls.classify(url) ?: return null
        // In a browser we can't tell which Instagram surface a reel came from.
        val surface = if (hit.platform == Platform.INSTAGRAM) IgSurface.UNKNOWN else null
        return handleShortForm(ShortFormSighting(hit.platform, packageName, surface, hit.itemKey, now), state)
    }

    private suspend fun handleShortForm(sighting: ShortFormSighting, state: ContentState): ContentBlock? {
        val isNew = deduper.isNew(sighting)
        // YouTube study mode blocks Shorts outright (spec F05).
        if (sighting.platform == Platform.YOUTUBE && StudyModes.youtubeActive(state.timer, state.settings)) {
            return block(sighting)
        }
        val rule = ShortFormPolicy.ruleFor(sighting.platform, sighting.surface, state.shortFormRules)
        return when (rule?.mode) {
            ShortFormMode.BLOCK -> block(sighting)
            ShortFormMode.LIMIT -> {
                // Only a new item can push past the limit; the current one may finish playing.
                if (!isNew) return null
                when (val decision = ShortFormPolicy.decide(sighting, state.shortFormRules, store.countsToday(sighting.platform))) {
                    is ShortFormDecision.LimitReached -> {
                        deduper.forget(sighting.viaPackage)
                        ContentBlock.ShortFormLimit(sighting.platform, sighting.surface, decision.watched, decision.limit)
                    }

                    else -> {
                        store.record(sighting)
                        null
                    }
                }
            }

            ShortFormMode.ALLOW, null -> {
                if (isNew) store.record(sighting)
                null
            }
        }
    }

    private fun block(sighting: ShortFormSighting): ContentBlock {
        deduper.forget(sighting.viaPackage)
        return ContentBlock.ShortForm(sighting.platform, sighting.surface)
    }
}
