/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.constants.DiscordAccessTokenKey
import moe.rgsekai.sekaitune.constants.DiscordProfileKey
import moe.rgsekai.sekaitune.discord.DiscordAuth
import moe.rgsekai.sekaitune.discord.DiscordLogin
import moe.rgsekai.sekaitune.discord.DiscordPresence
import moe.rgsekai.sekaitune.discord.DiscordProfile
import moe.rgsekai.sekaitune.discord.NowPlaying
import moe.rgsekai.sekaitune.discord.PresenceConnection
import moe.rgsekai.sekaitune.discord.PresenceSettings
import moe.rgsekai.sekaitune.utils.PreferenceStore
import moe.rgsekai.sekaitune.utils.dataStore
import javax.inject.Inject

@HiltViewModel
class DiscordViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val auth: DiscordAuth,
    private val presence: DiscordPresence,
) : ViewModel() {

    val isConfigured: Boolean get() = auth.isConfigured
    val login: StateFlow<DiscordLogin> = auth.login
    val connection: StateFlow<PresenceConnection> = presence.connection
    val nowPlaying: StateFlow<NowPlaying?> = presence.nowPlaying

    val signedIn: StateFlow<Boolean> = auth.signedIn
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PreferenceStore.get(DiscordAccessTokenKey) != null,
        )

    val profile: StateFlow<DiscordProfile?> = auth.profile
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PreferenceStore.get(DiscordProfileKey)?.let { DiscordProfile.fromJson(it) },
        )

    private val _settings = MutableStateFlow(
        PreferenceStore.getPreferences()?.let { PresenceSettings.from(it) } ?: PresenceSettings(),
    )
    val settings: StateFlow<PresenceSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            val initial = context.dataStore.data.first()
            _settings.value = PresenceSettings.from(initial)
        }
    }

    fun edit(change: PresenceSettings.() -> PresenceSettings) {
        val edited = _settings.value.change()
        _settings.value = edited
        presence.save(edited)
    }

    suspend fun loginUri(): Uri = auth.loginUri()

    fun cancelWaiting() = auth.cancelWaiting()

    fun dismissProblem() = auth.dismissProblem()

    fun refreshProfile() {
        viewModelScope.launch { auth.refreshProfile() }
    }

    fun logOut() {
        viewModelScope.launch(NonCancellable) { auth.logOut() }
    }
}

