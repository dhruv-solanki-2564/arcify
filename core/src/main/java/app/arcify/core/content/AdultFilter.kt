package app.arcify.core.content

/** The optional adult-site filter (spec F06). */
data class AdultFilterSettings(
    val enabled: Boolean = false,
    /** While in the future the filter can't be switched off and no exceptions can be added. */
    val strictUntil: Long? = null,
    /** Domains the user chose to let through; each also covers its subdomains. */
    val exceptions: Set<String> = emptySet(),
) {
    fun isStrict(now: Long): Boolean = (strictUntil ?: Long.MIN_VALUE) > now
}

object AdultFilter {

    /**
     * True if [host] or any domain above it (`a.b.example.com` → `b.example.com` → `example.com`)
     * is on the list, unless the user made an exception for it or a domain above it.
     */
    fun isBlocked(host: String, domains: Set<String>, exceptions: Set<String>): Boolean {
        val candidates = suffixes(UrlNormalizer.stripPrefixes(host.lowercase()))
        if (candidates.any { it in exceptions }) return false
        return candidates.any { it in domains }
    }

    /** `a.b.example.com` → [a.b.example.com, b.example.com, example.com]. Top-level domains alone are never checked. */
    internal fun suffixes(host: String): List<String> {
        val parts = host.trim('.').split('.')
        if (parts.size < 2) return emptyList()
        return (0..parts.size - 2).map { parts.drop(it).joinToString(".") }
    }

    /** Cleans what the user typed into a domain exception, or null if it isn't one. */
    fun normalizeException(input: String): String? =
        UrlNormalizer.normalize(input)?.host?.takeIf { it.contains('.') }

    /** Switching off, or adding an exception, loosens the filter: not allowed while locked. */
    fun canLoosen(settings: AdultFilterSettings, now: Long): Boolean = !settings.isStrict(now)
}
