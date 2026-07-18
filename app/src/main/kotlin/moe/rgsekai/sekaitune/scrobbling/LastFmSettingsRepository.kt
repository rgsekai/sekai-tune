/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.scrobbling

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import moe.rgsekai.sekaitune.BuildConfig
import moe.rgsekai.sekaitune.constants.EnableLastFMScrobblingKey
import moe.rgsekai.sekaitune.constants.LastFMApiKeyOverrideKey
import moe.rgsekai.sekaitune.constants.LastFMCustomEndpointKey
import moe.rgsekai.sekaitune.constants.LastFMProviderKey
import moe.rgsekai.sekaitune.constants.LastFMSecretOverrideKey
import moe.rgsekai.sekaitune.constants.LastFMSessionKey
import moe.rgsekai.sekaitune.constants.LastFMUseNowPlaying
import moe.rgsekai.sekaitune.constants.LastFMUsernameKey
import moe.rgsekai.sekaitune.constants.LastFmProvider
import moe.rgsekai.sekaitune.constants.ScrobbleDelayPercentKey
import moe.rgsekai.sekaitune.constants.ScrobbleDelaySecondsKey
import moe.rgsekai.sekaitune.constants.ScrobbleMinSongDurationKey
import moe.rgsekai.sekaitune.extensions.toEnum
import moe.rgsekai.sekaitune.lastfm.LastFM
import moe.rgsekai.sekaitune.lastfm.models.Authentication
import moe.rgsekai.sekaitune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

data class LastFmSettingsData(
    val serviceConfig: LastFmServiceConfig,
    val username: String,
    val sessionKey: String,
    val scrobblingEnabled: Boolean,
    val nowPlayingEnabled: Boolean,
    val minTrackDurationSeconds: Int,
    val scrobbleDelayPercent: Float,
    val scrobbleDelaySeconds: Int,
) {
    val isLoggedIn: Boolean
        get() = sessionKey.isNotBlank()
}

data class LastFmServiceConfig(
    val provider: LastFmProvider,
    val customEndpoint: String,
    val apiKeyOverride: String,
    val secretOverride: String,
    val endpoint: String,
    val apiKey: String,
    val secret: String,
    val endpointValid: Boolean,
) {
    val initialized: Boolean
        get() = endpointValid && apiKey.isNotBlank() && secret.isNotBlank()

    fun apply(sessionKey: String?) {
        LastFM.configure(
            endpoint = endpoint,
            apiKey = apiKey,
            secret = secret,
            sessionKey = sessionKey.takeIf { endpointValid },
        )
    }

    companion object {
        fun fromPreferences(
            preferences: Preferences,
            defaultApiKey: String = BuildConfig.LASTFM_API_KEY,
            defaultSecret: String = BuildConfig.LASTFM_SECRET,
        ): LastFmServiceConfig {
            val provider = preferences[LastFMProviderKey].toEnum(LastFmProvider.LASTFM)
            val customEndpoint = preferences[LastFMCustomEndpointKey].orEmpty()
            val apiKeyOverride = preferences[LastFMApiKeyOverrideKey].orEmpty()
            val secretOverride = preferences[LastFMSecretOverrideKey].orEmpty()

            return fromValues(
                provider = provider,
                customEndpoint = customEndpoint,
                apiKeyOverride = apiKeyOverride,
                secretOverride = secretOverride,
                defaultApiKey = defaultApiKey,
                defaultSecret = defaultSecret,
            )
        }

        fun fromValues(
            provider: LastFmProvider,
            customEndpoint: String,
            apiKeyOverride: String,
            secretOverride: String,
            defaultApiKey: String = BuildConfig.LASTFM_API_KEY,
            defaultSecret: String = BuildConfig.LASTFM_SECRET,
        ): LastFmServiceConfig {
            val normalizedCustomEndpoint = normalizeEndpointOrNull(customEndpoint)
            val endpoint =
                when (provider) {
                    LastFmProvider.LASTFM -> LastFM.DEFAULT_API_ENDPOINT
                    LastFmProvider.LIBREFM -> LastFM.LIBREFM_API_ENDPOINT
                    LastFmProvider.CUSTOM -> normalizedCustomEndpoint ?: LastFM.DEFAULT_API_ENDPOINT
                }
            val endpointValid = provider != LastFmProvider.CUSTOM || normalizedCustomEndpoint != null
            val apiKey =
                when (provider) {
                    LastFmProvider.LASTFM -> defaultApiKey

                    LastFmProvider.LIBREFM,
                    LastFmProvider.CUSTOM,
                    -> apiKeyOverride.ifBlank { LastFM.FALLBACK_COMPAT_API_KEY }
                }
            val secret =
                when (provider) {
                    LastFmProvider.LASTFM -> defaultSecret

                    LastFmProvider.LIBREFM,
                    LastFmProvider.CUSTOM,
                    -> secretOverride.ifBlank { LastFM.FALLBACK_COMPAT_SECRET }
                }

            return LastFmServiceConfig(
                provider = provider,
                customEndpoint = customEndpoint,
                apiKeyOverride = apiKeyOverride,
                secretOverride = secretOverride,
                endpoint = endpoint,
                apiKey = apiKey,
                secret = secret,
                endpointValid = endpointValid,
            )
        }

        fun normalizeEndpointOrNull(endpoint: String): String? = runCatching { LastFM.normalizeEndpoint(endpoint) }.getOrNull()
    }
}

@Singleton
class LastFmSettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun observeSettings(): Flow<LastFmSettingsData> = context.dataStore.data.map(::settingsFromPreferences)

        suspend fun login(
            username: String,
            password: String,
        ): Result<Authentication> {
            val current = context.dataStore.data.first()
            val settings = settingsFromPreferences(current)
            if (!settings.serviceConfig.initialized) {
                return Result.failure(LastFM.LastFmException(10, "Service is not configured"))
            }

            settings.serviceConfig.apply(sessionKey = null)
            return LastFM
                .getMobileSession(username.trim(), password)
                .onSuccess { authentication ->
                    context.dataStore.edit { preferences ->
                        preferences[LastFMUsernameKey] = authentication.session.name
                        preferences[LastFMSessionKey] = authentication.session.key
                    }
                    LastFM.sessionKey = authentication.session.key
                }
        }

        suspend fun logout() {
            context.dataStore.edit { preferences ->
                clearSession(preferences)
            }
            LastFM.sessionKey = null
        }

        suspend fun saveServiceConfig(
            provider: LastFmProvider,
            customEndpoint: String,
            apiKeyOverride: String,
            secretOverride: String,
        ): LastFmServiceConfig? {
            val normalizedEndpoint =
                if (provider == LastFmProvider.CUSTOM) {
                    LastFmServiceConfig.normalizeEndpointOrNull(customEndpoint) ?: return null
                } else {
                    customEndpoint.trim()
                }
            val nextApiKey = apiKeyOverride.trim()
            val nextSecret = secretOverride.trim()
            var nextConfig: LastFmServiceConfig? = null

            context.dataStore.edit { preferences ->
                val previous = LastFmServiceConfig.fromPreferences(preferences)
                val changed =
                    previous.provider != provider ||
                        previous.customEndpoint != normalizedEndpoint ||
                        previous.apiKeyOverride != nextApiKey ||
                        previous.secretOverride != nextSecret

                preferences[LastFMProviderKey] = provider.name
                preferences[LastFMCustomEndpointKey] = normalizedEndpoint
                preferences[LastFMApiKeyOverrideKey] = nextApiKey
                preferences[LastFMSecretOverrideKey] = nextSecret

                if (changed) {
                    clearSession(preferences)
                    preferences[EnableLastFMScrobblingKey] = false
                    preferences[LastFMUseNowPlaying] = false
                }

                nextConfig = LastFmServiceConfig.fromPreferences(preferences)
            }

            nextConfig?.apply(sessionKey = null)
            return nextConfig
        }

        suspend fun setScrobblingEnabled(enabled: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[EnableLastFMScrobblingKey] = enabled
                if (!enabled) {
                    preferences[LastFMUseNowPlaying] = false
                }
            }
        }

        suspend fun setNowPlayingEnabled(enabled: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[LastFMUseNowPlaying] = enabled
            }
        }

        suspend fun setMinTrackDurationSeconds(value: Int) {
            context.dataStore.edit { preferences ->
                preferences[ScrobbleMinSongDurationKey] = value
            }
        }

        suspend fun setScrobbleDelayPercent(value: Float) {
            context.dataStore.edit { preferences ->
                preferences[ScrobbleDelayPercentKey] = value
            }
        }

        suspend fun setScrobbleDelaySeconds(value: Int) {
            context.dataStore.edit { preferences ->
                preferences[ScrobbleDelaySecondsKey] = value
            }
        }

        private fun settingsFromPreferences(preferences: Preferences): LastFmSettingsData {
            val serviceConfig = LastFmServiceConfig.fromPreferences(preferences)
            return LastFmSettingsData(
                serviceConfig = serviceConfig,
                username = preferences[LastFMUsernameKey].orEmpty(),
                sessionKey = preferences[LastFMSessionKey].orEmpty(),
                scrobblingEnabled = preferences[EnableLastFMScrobblingKey] ?: false,
                nowPlayingEnabled = preferences[LastFMUseNowPlaying] ?: false,
                minTrackDurationSeconds = preferences[ScrobbleMinSongDurationKey] ?: LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION,
                scrobbleDelayPercent = preferences[ScrobbleDelayPercentKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT,
                scrobbleDelaySeconds = preferences[ScrobbleDelaySecondsKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS,
            )
        }

        private fun clearSession(preferences: androidx.datastore.preferences.core.MutablePreferences) {
            preferences.remove(LastFMUsernameKey)
            preferences.remove(LastFMSessionKey)
        }
    }




