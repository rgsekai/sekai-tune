/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.scrobble

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.constants.ScrobbleSendLikesKey
import moe.rgsekai.sekaitune.utils.dataStore
import javax.inject.Inject

data class ScrobbleUiState(
    val isConfigured: Boolean = true,
    val isAuthenticated: Boolean = false,
    val username: String = "",
    val isEnabled: Boolean = false,
    val scrobbleEnabled: Boolean = true,
    val nowPlayingEnabled: Boolean = true,
    val sendLikesEnabled: Boolean = false,
    val primaryArtistOnly: Boolean = false,
    val pendingCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ScrobbleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scrobbleManager: ScrobbleManager,
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    fun uiState(provider: ScrobbleProvider): StateFlow<ScrobbleUiState> {
        val pendingCountFlow = scrobbleManager.getPendingCountFlow(provider)

        return combine(
            context.dataStore.data,
            pendingCountFlow,
            _loading,
            _error,
        ) { prefs, pendingCount, isLoading, errorMsg ->
            val sessionKey = prefs[provider.sessionKeyPref].orEmpty()
            val username = prefs[provider.usernamePref].orEmpty()
            val isEnabled = prefs[provider.enabledPref] ?: false
            val scrobbleEnabled = prefs[provider.scrobblePref] ?: true
            val nowPlayingEnabled = prefs[provider.nowPlayingPref] ?: true
            val sendLikesEnabled = prefs[ScrobbleSendLikesKey] ?: false
            val primaryArtistOnly = prefs[provider.primaryArtistOnlyPref] ?: false

            ScrobbleUiState(
                isConfigured = provider.client.isConfigured,
                isAuthenticated = sessionKey.isNotBlank(),
                username = username,
                isEnabled = isEnabled,
                scrobbleEnabled = scrobbleEnabled,
                nowPlayingEnabled = nowPlayingEnabled,
                sendLikesEnabled = sendLikesEnabled,
                primaryArtistOnly = primaryArtistOnly,
                pendingCount = pendingCount,
                isLoading = isLoading,
                errorMessage = errorMsg,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ScrobbleUiState(),
        )
    }

    fun login(provider: ScrobbleProvider, user: String, pass: String) {
        if (!provider.client.isConfigured) {
            _error.value = "${provider.title} API key and secret are not configured. Please add LASTFM_API_KEY and LASTFM_SECRET to local.properties."
            return
        }
        if (user.isBlank() || pass.isBlank()) {
            _error.value = "Username and password cannot be empty"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val session = provider.client.authenticate(user.trim(), pass)
                context.dataStore.edit {
                    it[provider.sessionKeyPref] = session.sessionKey
                    it[provider.usernamePref] = session.username
                    it[provider.enabledPref] = true
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Authentication failed"
            } finally {
                _loading.value = false
            }
        }
    }

    fun logout(provider: ScrobbleProvider) {
        viewModelScope.launch {
            context.dataStore.edit {
                it.remove(provider.sessionKeyPref)
                it.remove(provider.usernamePref)
                it[provider.enabledPref] = false
            }
        }
    }

    fun setEnabled(provider: ScrobbleProvider, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[provider.enabledPref] = enabled }
        }
    }

    fun setScrobbleEnabled(provider: ScrobbleProvider, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[provider.scrobblePref] = enabled }
        }
    }

    fun setNowPlayingEnabled(provider: ScrobbleProvider, enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[provider.nowPlayingPref] = enabled }
        }
    }

    fun setSendLikesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[ScrobbleSendLikesKey] = enabled }
        }
    }

    fun setPrimaryArtistOnly(provider: ScrobbleProvider, primaryOnly: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[provider.primaryArtistOnlyPref] = primaryOnly }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
