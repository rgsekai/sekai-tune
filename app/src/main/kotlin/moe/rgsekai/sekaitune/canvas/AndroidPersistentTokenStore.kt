/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import android.content.Context
import android.content.SharedPreferences
import moe.rgsekai.sekaitune.canvas.tokens.PersistentTokenStore
import moe.rgsekai.sekaitune.canvas.tokens.TokenStoreRegistry
import moe.rgsekai.sekaitune.canvas.tokens.WebToken
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * SharedPreferences-backed implementation of [PersistentTokenStore].
 * Persists dynamically scraped and minted tokens (TIDAL, Apple Music) across cold starts.
 */
class AndroidPersistentTokenStore(context: Context) : PersistentTokenStore {
    companion object {
        private const val PREFS_NAME = "persistent_web_tokens"
        private const val TAG = "TokenStore"

        fun initialize(context: Context) {
            try {
                val store = AndroidPersistentTokenStore(context)
                TokenStoreRegistry.activeStore = store
                Timber.tag(TAG).d("Initialized AndroidPersistentTokenStore with %d tokens", store.memoryCache.size)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to initialize AndroidPersistentTokenStore")
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val memoryCache = ConcurrentHashMap<String, WebToken>()

    init {
        val nowSeconds = System.currentTimeMillis() / 1_000L
        prefs.all.forEach { (key, value) ->
            if (value is String) {
                val separatorIndex = value.lastIndexOf('|')
                if (separatorIndex > 0) {
                    val token = value.substring(0, separatorIndex)
                    val expiry = value.substring(separatorIndex + 1).toLongOrNull() ?: 0L
                    if (expiry > nowSeconds) {
                        memoryCache[key] = WebToken(token, expiry)
                    }
                }
            }
        }
    }

    override fun get(providerKey: String): WebToken? {
        val cached = memoryCache[providerKey] ?: return null
        if (cached.isExpired()) {
            remove(providerKey)
            return null
        }
        return cached
    }

    override fun put(providerKey: String, token: WebToken) {
        memoryCache[providerKey] = token
        prefs.edit().putString(providerKey, "${token.token}|${token.expiresAtEpochSeconds}").apply()
        Timber.tag(TAG).d("Persisted token for %s (expires in %ds)", providerKey, token.expiresAtEpochSeconds - (System.currentTimeMillis() / 1000L))
    }

    override fun remove(providerKey: String) {
        memoryCache.remove(providerKey)
        prefs.edit().remove(providerKey).apply()
        Timber.tag(TAG).d("Removed persisted token for %s", providerKey)
    }

    override fun clearAll() {
        memoryCache.clear()
        prefs.edit().clear().apply()
        Timber.tag(TAG).d("Cleared all persisted tokens")
    }
}
