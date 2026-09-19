/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanvasSettingsUseCases @Inject constructor(
    private val repository: CanvasSettingsRepository,
) {
    val policy: Flow<CanvasPolicy> = combine(
        repository.configuration,
        repository.connectivity,
    ) { configuration, connectivity ->
        CanvasPolicy(configuration, connectivity, ready = true)
    }.distinctUntilChanged()

    val spotifyConnected: Flow<Boolean> = repository.spotifyConnected

    suspend fun setEnabled(enabled: Boolean) = repository.setEnabled(enabled)
    suspend fun setSource(source: CanvasSource) = repository.setSource(source)
    suspend fun setWifiOnly(wifiOnly: Boolean) = repository.setWifiOnly(wifiOnly)

    suspend fun setCacheLimit(limitMb: Int) {
        require(limitMb in CACHE_LIMITS)
        repository.setCacheLimit(limitMb)
        repository.applyCacheLimit(limitMb)
    }

    suspend fun cacheBytes(): Long = repository.cacheBytes()
    suspend fun clearCache() = repository.clearCache()

    fun pendingHealth(policy: CanvasPolicy, spotifyConnected: Boolean): CanvasHealthStatus = CanvasHealthStatus(
        betterLyrics = healthAvailability(policy, CanvasSource.BETTER_LYRICS),
        appleMusic = healthAvailability(policy, CanvasSource.APPLE_MUSIC),
        tidal = healthAvailability(policy, CanvasSource.TIDAL),
        spotify = healthAvailability(policy, CanvasSource.SPOTIFY, spotifyConnected),
    )

    suspend fun checkHealth(policy: CanvasPolicy, spotifyConnected: Boolean): CanvasHealthStatus = coroutineScope {
        val betterLyrics = async { checkProvider(policy, CanvasSource.BETTER_LYRICS) }
        val appleMusic = async { checkProvider(policy, CanvasSource.APPLE_MUSIC) }
        val tidal = async { checkProvider(policy, CanvasSource.TIDAL) }
        val spotify = async { checkProvider(policy, CanvasSource.SPOTIFY, spotifyConnected) }
        CanvasHealthStatus(betterLyrics.await(), appleMusic.await(), tidal.await(), spotify.await())
    }

    private suspend fun checkProvider(policy: CanvasPolicy, source: CanvasSource, spotifyConnected: Boolean = true): CanvasHealth {
        val availability = healthAvailability(policy, source, spotifyConnected)
        Timber.tag("CanvasHealth").d(
            "checkProvider start: source=%s, availability=%s (policy.ready=%s, config.enabled=%s, config.source=%s, online=%s, wifi=%s, lowData=%s)",
            source, availability, policy.ready, policy.configuration.enabled, policy.configuration.source,
            policy.connectivity.online, policy.connectivity.wifi, policy.configuration.lowDataMode,
        )
        if (availability != CanvasHealth.CHECKING) return availability
        val timeout = if (source == CanvasSource.SPOTIFY) 45_000L else 20_000L
        return try {
            val isHealthy = withTimeoutOrNull(timeout) { repository.isHealthy(source) }
            val result = if (isHealthy == true) CanvasHealth.AVAILABLE else CanvasHealth.UNAVAILABLE
            Timber.tag("CanvasHealth").i("checkProvider finished: source=%s -> %s (isHealthy=%s, timeout=%dms)", source, result, isHealthy, timeout)
            result
        } catch (error: CancellationException) {
            Timber.tag("CanvasHealth").w("checkProvider cancelled: source=%s", source)
            throw error
        } catch (error: Exception) {
            Timber.tag("CanvasHealth").e(error, "checkProvider exception: source=%s", source)
            CanvasHealth.UNAVAILABLE
        }
    }

    private fun healthAvailability(policy: CanvasPolicy, source: CanvasSource, spotifyConnected: Boolean = true): CanvasHealth = when {
        !policy.ready -> CanvasHealth.NOT_CHECKED
        !policy.configuration.source.accepts(source) -> CanvasHealth.NOT_SELECTED
        !policy.configuration.enabled -> CanvasHealth.DISABLED
        source == CanvasSource.SPOTIFY && !spotifyConnected -> CanvasHealth.NOT_CONNECTED
        !policy.connectivity.online -> CanvasHealth.OFFLINE
        policy.configuration.wifiOnly && !policy.connectivity.wifi -> CanvasHealth.WIFI_REQUIRED
        policy.configuration.lowDataMode && policy.connectivity.metered -> CanvasHealth.LOW_DATA_MODE
        else -> CanvasHealth.CHECKING
    }

    companion object {
        val CACHE_LIMITS: List<Int> = listOf(0, 64, 128, 256, 512, 1024, 2048, 4096, 8192, -1)
    }
}
