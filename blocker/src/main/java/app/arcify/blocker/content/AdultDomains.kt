package app.arcify.blocker.content

import android.content.Context
import app.arcify.blocker.Diag
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The bundled adult-site list (StevenBlack/hosts, MIT; see THIRD_PARTY_NOTICES.md), loaded once
 * in the background. Until it has loaded the set is empty, so the filter simply doesn't match yet.
 */
@Singleton
class AdultDomains @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @Volatile
    var domains: Set<String> = emptySet()
        private set

    suspend fun load() = withContext(Dispatchers.IO) {
        if (domains.isNotEmpty()) return@withContext
        try {
            val loaded = HashSet<String>(EXPECTED_SIZE)
            context.assets.open(ASSET).use { raw ->
                GZIPInputStream(raw).bufferedReader().useLines { lines ->
                    lines.forEach { line -> if (line.isNotBlank()) loaded += line.trim() }
                }
            }
            domains = loaded
            Diag.log("adult list loaded: ${loaded.size} domains")
        } catch (e: Exception) {
            Diag.error("adult list could not be loaded", e)
        }
    }

    private companion object {
        const val ASSET = "adult_domains.txt.gz"
        const val EXPECTED_SIZE = 70_000
    }
}
