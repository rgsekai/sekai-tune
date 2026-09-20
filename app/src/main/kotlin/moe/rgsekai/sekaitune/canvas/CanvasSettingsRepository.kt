/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.constants.CanvasAudioReactiveKey
import moe.rgsekai.sekaitune.constants.CanvasProceduralFallbackKey
import moe.rgsekai.sekaitune.constants.CanvasProceduralStyleKey
import moe.rgsekai.sekaitune.constants.CanvasSourceKey
import moe.rgsekai.sekaitune.constants.CanvasWifiOnlyKey
import moe.rgsekai.sekaitune.constants.LowDataModeKey
import moe.rgsekai.sekaitune.constants.MaxCanvasCacheSizeKey
import moe.rgsekai.sekaitune.constants.SekaiTuneCanvasKey
import moe.rgsekai.sekaitune.constants.SpotifySpDcKey
import moe.rgsekai.sekaitune.ui.player.CanvasArtworkPlaybackCache
import moe.rgsekai.sekaitune.utils.dataStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanvasSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()

    val spotifyConnected: Flow<Boolean> = context.dataStore.data.map { preferences ->
        !preferences[SpotifySpDcKey].isNullOrBlank()
    }.distinctUntilChanged()

    val configuration: Flow<CanvasConfiguration> = context.dataStore.data.map { preferences ->
        CanvasConfiguration(
            enabled = preferences[SekaiTuneCanvasKey] ?: false,
            source = CanvasSource.fromPreference(preferences[CanvasSourceKey]),
            wifiOnly = preferences[CanvasWifiOnlyKey] ?: false,
            proceduralFallback = preferences[CanvasProceduralFallbackKey] ?: true,
            proceduralStyle = ProceduralCanvasStyle.fromPreference(preferences[CanvasProceduralStyleKey]),
            audioReactive = preferences[CanvasAudioReactiveKey] ?: false,
            cacheLimitMb = (preferences[MaxCanvasCacheSizeKey] ?: 256).coerceAtLeast(-1),
            lowDataMode = preferences[LowDataModeKey] ?: false,
        )
    }.distinctUntilChanged()

    val connectivity: Flow<CanvasConnectivity> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(CanvasConnectivity())
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentConnectivity())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.toCanvasConnectivity())
            }

            override fun onLost(network: Network) {
                trySend(CanvasConnectivity())
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(currentConnectivity())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun currentConnectivity(): CanvasConnectivity {
        val manager = connectivityManager ?: return CanvasConnectivity()
        val network = manager.activeNetwork ?: return CanvasConnectivity()
        return manager.getNetworkCapabilities(network)?.toCanvasConnectivity() ?: CanvasConnectivity()
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SekaiTuneCanvasKey] = enabled }
    }

    suspend fun setSource(source: CanvasSource) {
        context.dataStore.edit { it[CanvasSourceKey] = source.name }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { it[CanvasWifiOnlyKey] = wifiOnly }
    }

    suspend fun setProceduralFallback(enabled: Boolean) {
        context.dataStore.edit { it[CanvasProceduralFallbackKey] = enabled }
    }

    suspend fun setProceduralStyle(style: ProceduralCanvasStyle) {
        context.dataStore.edit { it[CanvasProceduralStyleKey] = style.name }
    }

    suspend fun setAudioReactive(enabled: Boolean) {
        context.dataStore.edit { it[CanvasAudioReactiveKey] = enabled }
    }

    suspend fun setCacheLimit(limitMb: Int) {
        context.dataStore.edit { it[MaxCanvasCacheSizeKey] = limitMb }
    }

    suspend fun initializeCache() = withContext(Dispatchers.IO) {
        CanvasArtworkPlaybackCache.init(context)
        CanvasArtworkPlaybackCache.setMaxSize(configuration.first().cacheLimitMb)
    }

    suspend fun applyCacheLimit(limitMb: Int) = withContext(Dispatchers.IO) {
        CanvasArtworkPlaybackCache.setMaxSize(limitMb)
    }

    suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) {
        CanvasArtworkPlaybackCache.byteSize()
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        if (!CanvasArtworkPlaybackCache.clearAndPersist()) throw IOException("Canvas cache could not be cleared")
    }

    suspend fun isHealthy(source: CanvasSource): Boolean = withContext(Dispatchers.IO) {
        when (source) {
            CanvasSource.BETTER_LYRICS -> SekaiTuneCanvas.isHealthy()
            CanvasSource.APPLE_MUSIC -> AppleMusicProvider.isHealthy()
            CanvasSource.TIDAL -> TidalCanvasProvider.isHealthy()
            CanvasSource.SPOTIFY -> {
                val spDc = context.dataStore.data.first()[SpotifySpDcKey]?.takeIf { it.isNotBlank() }
                SpotifyCanvasProvider.isHealthy(spDc)
            }
            CanvasSource.ALL -> error("Health checks require one provider")
        }
    }
}

private fun NetworkCapabilities.toCanvasConnectivity(): CanvasConnectivity = CanvasConnectivity(
    online = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    wifi = hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
    metered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
)
