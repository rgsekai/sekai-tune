/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas.tokens

import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a cached token along with its expiration epoch timestamp (in seconds).
 */
data class WebToken(
    val token: String,
    val expiresAtEpochSeconds: Long,
) {
    fun isExpired(bufferSeconds: Long = 60L): Boolean {
        val nowSeconds = System.currentTimeMillis() / 1_000L
        return expiresAtEpochSeconds <= (nowSeconds + bufferSeconds)
    }
}

/**
 * Storage interface for persisting tokens across app restarts.
 */
interface PersistentTokenStore {
    fun get(providerKey: String): WebToken?
    fun put(providerKey: String, token: WebToken)
    fun remove(providerKey: String)
    fun clearAll()
}

/**
 * Default in-memory token store for standalone/testing environments.
 */
class InMemoryTokenStore : PersistentTokenStore {
    private val store = ConcurrentHashMap<String, WebToken>()

    override fun get(providerKey: String): WebToken? = store[providerKey]

    override fun put(providerKey: String, token: WebToken) {
        store[providerKey] = token
    }

    override fun remove(providerKey: String) {
        store.remove(providerKey)
    }

    override fun clearAll() {
        store.clear()
    }
}

/**
 * Global registry for the active [PersistentTokenStore].
 * Initialized by the Android app module on startup.
 */
object TokenStoreRegistry {
    @Volatile
    var activeStore: PersistentTokenStore = InMemoryTokenStore()
}
