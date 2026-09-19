/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas.tokens

import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Thrown when an API indicates the current token is invalid, revoked, or expired.
 */
class TokenRejectedException(
    val provider: String,
    val statusCode: Int? = null,
    message: String? = null,
    cause: Throwable? = null,
) : IOException("[$provider] Token rejected (status=$statusCode): ${message ?: "Unauthorized"}", cause)

/**
 * Reusable provider for dynamic public token scraping, persistent caching, and refresh-on-failure.
 *
 * @property providerName Identifier for this token provider (e.g. "TIDAL", "AppleMusic").
 * @property client Ktor [HttpClient] used for network operations.
 * @property fetcher Function to fetch/scrape a fresh [WebToken] from upstream web sources.
 */
class WebTokenProvider(
    val providerName: String,
    private val client: HttpClient,
    private val fetcher: suspend (client: HttpClient, rejectedToken: String?) -> WebToken,
) {
    private val mutex = Mutex()
    private var inMemoryToken: WebToken? = null

    private fun formatEpoch(epochSeconds: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochSeconds * 1000L))
    }

    private fun log(message: String) {
        println("WebTokenProvider[$providerName]: $message")
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        System.err.println("WebTokenProvider[$providerName]: ERROR: $message")
        throwable?.printStackTrace(System.err)
    }

    /**
     * Gets a valid token, checking in-memory cache and persistent store before scraping.
     *
     * @param rejectedToken If specified, ensures the returned token is NOT this value.
     * @param forceRefresh If true, forces a re-scrape regardless of cache state.
     */
    suspend fun getToken(rejectedToken: String? = null, forceRefresh: Boolean = false): String = mutex.withLock {
        val store = TokenStoreRegistry.activeStore

        // 1. Check in-memory cache
        if (!forceRefresh) {
            val mem = inMemoryToken
            if (mem != null && mem.token != rejectedToken && !mem.isExpired()) {
                return@withLock mem.token
            }

            // 2. Check persistent store
            val persisted = store.get(providerName)
            if (persisted != null && persisted.token != rejectedToken && !persisted.isExpired()) {
                inMemoryToken = persisted
                log("Restored valid token from persistent store (expires at ${formatEpoch(persisted.expiresAtEpochSeconds)}, epoch=${persisted.expiresAtEpochSeconds})")
                return@withLock persisted.token
            }
        }

        // 3. Scrape fresh token
        val startTime = System.currentTimeMillis()
        log("Scraping / minting new token from upstream (rejectedToken=${rejectedToken?.take(10)}...)...")

        val freshToken: WebToken = try {
            fetcher(client, rejectedToken)
        } catch (t: Throwable) {
            logError("Failed to fetch fresh token from upstream (${System.currentTimeMillis() - startTime}ms)", t)
            throw IOException("Failed to obtain dynamic token for $providerName: ${t.message}", t)
        }

        if (freshToken.token.isBlank()) {
            val errorMsg = "Scraped token was blank for $providerName"
            logError(errorMsg)
            throw IOException(errorMsg)
        }

        // 4. Update memory cache and persistent store
        inMemoryToken = freshToken
        store.put(providerName, freshToken)

        log("Successfully obtained fresh token in ${System.currentTimeMillis() - startTime}ms (expires at ${formatEpoch(freshToken.expiresAtEpochSeconds)}, epoch=${freshToken.expiresAtEpochSeconds})")
        return@withLock freshToken.token
    }

    /**
     * Invalidates any cached token in memory and persistent storage.
     */
    fun invalidate() {
        inMemoryToken = null
        TokenStoreRegistry.activeStore.remove(providerName)
        log("Invalidated cached token.")
    }

    /**
     * Executes an API request block with automatic token retry.
     *
     * If the block throws a [TokenRejectedException], or a 401/403 status is detected,
     * the cached token is invalidated, a fresh token is acquired, and the request is retried once.
     * If the retry also fails, the exception is surfaced immediately without silent fallback.
     */
    suspend fun <T> executeWithTokenRetry(block: suspend (token: String) -> T): T {
        val currentToken = getToken()
        return try {
            block(currentToken)
        } catch (e: TokenRejectedException) {
            log("Token rejected with status ${e.statusCode ?: "unknown"} ('${e.message}'). Invalidating and retrying once...")
            val freshToken = getToken(rejectedToken = currentToken, forceRefresh = true)
            try {
                block(freshToken)
            } catch (retryException: Throwable) {
                logError("Retry attempt after token refresh also failed for $providerName", retryException)
                throw retryException
            }
        } catch (t: Throwable) {
            // Check for unauthorized status message if wrapped in another exception
            val msg = t.message ?: ""
            if (msg.contains("401") || msg.contains("403") || msg.contains("Unauthorized") || msg.contains("Invalid token")) {
                log("Detected token failure in error message ('$msg'). Invalidating and retrying once...")
                val freshToken = getToken(rejectedToken = currentToken, forceRefresh = true)
                try {
                    block(freshToken)
                } catch (retryException: Throwable) {
                    logError("Retry attempt after token refresh also failed for $providerName", retryException)
                    throw retryException
                }
            } else {
                throw t
            }
        }
    }
}
