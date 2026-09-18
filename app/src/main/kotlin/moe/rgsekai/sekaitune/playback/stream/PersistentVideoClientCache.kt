/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.stream

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.App
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient
import moe.rgsekai.sekaitune.utils.StreamClientUtils
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent cache mapping videoId -> winning YouTubeClient key.
 * Caches which client succeeded for a given video ID so repeat plays
 * can jump directly to the winning client without going through failing cascades.
 */
object PersistentVideoClientCache {
    private const val TAG = "VideoClientCache"
    private const val PREFS_NAME = "persistent_video_winning_clients"
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000L // 7 days TTL

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryCache = ConcurrentHashMap<String, Entry>()
    private var prefs: SharedPreferences? = null
    @Volatile private var isLoaded = false

    private data class Entry(
        val clientKey: String,
        val timestampMs: Long,
    )

    fun initialize(context: Context) {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            try {
                val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs = sp
                val now = System.currentTimeMillis()
                sp.all.forEach { (key, value) ->
                    if (value is String) {
                        val parts = value.split("|")
                        if (parts.size >= 2) {
                            val clientKey = parts[0]
                            val timestamp = parts[1].toLongOrNull() ?: 0L
                            if (now - timestamp < TTL_MS) {
                                memoryCache[key] = Entry(clientKey, timestamp)
                            }
                        }
                    }
                }
                isLoaded = true
                Timber.tag(TAG).d("Loaded %d cached winning clients from disk", memoryCache.size)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to initialize PersistentVideoClientCache")
            }
        }
    }

    private fun ensureInitialized() {
        if (!isLoaded) {
            App.instance?.let { initialize(it) }
        }
    }

    fun getWinningClientKey(videoId: String): String? {
        ensureInitialized()
        val entry = memoryCache[videoId] ?: return null
        val now = System.currentTimeMillis()
        if (now - entry.timestampMs >= TTL_MS) {
            memoryCache.remove(videoId)
            scope.launch { prefs?.edit()?.remove(videoId)?.apply() }
            return null
        }
        return entry.clientKey
    }

    fun putWinningClient(videoId: String, client: YouTubeClient) {
        ensureInitialized()
        val clientKey = StreamClientUtils.buildClientKey(client)
        val now = System.currentTimeMillis()
        val previous = memoryCache.put(videoId, Entry(clientKey, now))
        if (previous?.clientKey != clientKey) {
            Timber.tag(TAG).i("[VideoClientCache] Saved winning client for %s -> %s", videoId, clientKey)
            scope.launch {
                prefs?.edit()?.putString(videoId, "$clientKey|$now")?.commit()
            }
        }
    }

    fun invalidate(videoId: String) {
        ensureInitialized()
        if (memoryCache.remove(videoId) != null) {
            Timber.tag(TAG).d("[VideoClientCache] Evicted winning client for %s", videoId)
            scope.launch {
                prefs?.edit()?.remove(videoId)?.commit()
            }
        }
    }

    fun clearAll() {
        ensureInitialized()
        memoryCache.clear()
        scope.launch {
            prefs?.edit()?.clear()?.commit()
        }
        Timber.tag(TAG).i("[VideoClientCache] Cleared all persistent winning clients")
    }
}

