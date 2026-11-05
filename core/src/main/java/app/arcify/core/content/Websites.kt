package app.arcify.core.content

import java.net.URI

/** A URL reduced to what blocking rules care about: lower-case host without `www.`/`m.`, and path. */
data class NormalizedUrl(val host: String, val path: String) {
    val pathSegments: List<String> get() = path.split('/').filter { it.isNotEmpty() }
}

object UrlNormalizer {
    /**
     * Normalises address-bar text. Returns null when it isn't a web address, e.g. search terms,
     * an empty bar or an internal browser page.
     */
    fun normalize(raw: String?): NormalizedUrl? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val withScheme = if (text.contains("://")) text else "https://$text"
        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = uri.host?.lowercase()?.let(::stripPrefixes) ?: return null
        if (!host.contains('.')) return null
        return NormalizedUrl(host, uri.rawPath.orEmpty())
    }

    internal fun stripPrefixes(host: String): String =
        host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
}

/** A short-form item recognised from its URL in a browser. */
data class ShortFormUrlHit(val platform: Platform, val itemKey: String)

object ShortFormUrls {
    /** Recognises Instagram reels, YouTube Shorts and Facebook reels by URL. */
    fun classify(url: NormalizedUrl): ShortFormUrlHit? {
        val segments = url.pathSegments
        val first = segments.firstOrNull()?.lowercase() ?: return null
        val id = segments.getOrNull(1)
        return when (url.host) {
            "instagram.com" -> if (first == "reel" || first == "reels") hit(Platform.INSTAGRAM, id) else null
            "youtube.com" -> if (first == "shorts") hit(Platform.YOUTUBE, id) else null
            "facebook.com" -> if (first == "reel" || first == "reels") hit(Platform.FACEBOOK, id) else null
            else -> null
        }
    }

    // A feed URL without an item ID still counts as short-form, so blocking applies; it just
    // shares one key, so it isn't counted repeatedly.
    private fun hit(platform: Platform, id: String?) = ShortFormUrlHit(platform, id ?: "feed")
}

enum class WebsiteRuleKind { BLOCK, ALLOW }

/** Pattern forms: `example.com` (and its subdomains) or `example.com/some/path` (path prefix). */
data class WebsiteRule(
    val pattern: String,
    val kind: WebsiteRuleKind,
    val strictUntil: Long? = null,
)

sealed interface WebsiteDecision {
    data object Allow : WebsiteDecision

    data class Blocked(val host: String, val byPattern: String?) : WebsiteDecision
}

object WebsitePolicy {

    fun matches(pattern: String, url: NormalizedUrl): Boolean {
        val cleaned = pattern.trim().lowercase()
            .substringAfter("://")
            .trimEnd('/')
        if (cleaned.isEmpty()) return false
        val host = UrlNormalizer.stripPrefixes(cleaned.substringBefore('/'))
        val pathPrefix = cleaned.substringAfter('/', missingDelimiterValue = "")
        val hostMatches = url.host == host || url.host.endsWith(".$host")
        if (!hostMatches) return false
        if (pathPrefix.isEmpty()) return true
        val path = url.path.lowercase().trimStart('/')
        return path == pathPrefix || path.startsWith("$pathPrefix/")
    }

    /**
     * In study mode only ALLOW rules let a page through. Otherwise BLOCK rules block, and
     * everything else is allowed.
     */
    fun decide(url: NormalizedUrl, rules: List<WebsiteRule>, studyMode: Boolean): WebsiteDecision {
        if (studyMode) {
            val allowed = rules.any { it.kind == WebsiteRuleKind.ALLOW && matches(it.pattern, url) }
            return if (allowed) WebsiteDecision.Allow else WebsiteDecision.Blocked(url.host, null)
        }
        val blocking = rules.firstOrNull { it.kind == WebsiteRuleKind.BLOCK && matches(it.pattern, url) }
        return if (blocking != null) WebsiteDecision.Blocked(url.host, blocking.pattern) else WebsiteDecision.Allow
    }

    fun isStrict(rule: WebsiteRule, now: Long): Boolean = (rule.strictUntil ?: Long.MIN_VALUE) > now
}
