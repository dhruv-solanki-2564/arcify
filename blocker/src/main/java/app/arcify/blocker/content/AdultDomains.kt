package app.arcify.blocker.content

import android.content.Context
import app.arcify.blocker.Diag
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The bundled adult-site list (StevenBlack/hosts, MIT; see THIRD_PARTY_NOTICES.md), loaded once
 * in the background. Until it has loaded the set is empty, so the filter simply doesn't match yet.
 *
 * The repo keeps the list gzipped, but the Android build unpacks `.gz` assets and drops the
 * extension, so the APK holds plain `adult_domains.txt`. Both names are tried.
 */
@Singleton
class AdultDomains @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @Volatile
    var domains: Set<String> = emptySet()
        private set

    private val _count = MutableStateFlow(NOT_LOADED)

    /** Number of listed domains: [NOT_LOADED] until loading has finished, 0 if it failed. */
    val count: StateFlow<Int> = _count.asStateFlow()

    private val mutex = Mutex()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (domains.isNotEmpty()) return@withLock
            val loaded = HashSet<String>(EXPECTED_SIZE)
            try {
                open().useLines { lines ->
                    lines.forEach { line ->
                        val domain = line.trim().lowercase()
                        if (domain.isNotEmpty() && !domain.startsWith('#')) loaded += domain
                    }
                }
                domains = loaded
                Diag.log("adult list loaded: ${loaded.size} domains")
            } catch (e: Exception) {
                Diag.error("adult list could not be loaded", e)
            }
            _count.value = loaded.size
        }
    }

    private fun open(): BufferedReader = try {
        context.assets.open(ASSET_PLAIN).bufferedReader()
    } catch (_: FileNotFoundException) {
        GZIPInputStream(context.assets.open(ASSET_GZIP)).bufferedReader()
    }

    companion object {
        const val NOT_LOADED = -1
        private const val ASSET_PLAIN = "adult_domains.txt"
        private const val ASSET_GZIP = "adult_domains.txt.gz"
        private const val EXPECTED_SIZE = 70_000
    }
}
