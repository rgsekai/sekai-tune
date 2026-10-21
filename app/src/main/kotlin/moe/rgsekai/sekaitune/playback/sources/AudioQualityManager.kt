package moe.rgsekai.sekaitune.playback.sources

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaybackQualityBadgeState {
    CHECKING,
    UPGRADING,
    FINAL,
}

data class PlaybackQualityBadgeInfo(
    val mediaId: String? = null,
    val state: PlaybackQualityBadgeState = PlaybackQualityBadgeState.FINAL,
    val label: String = "",
    val isLossless: Boolean = false,
    val isHiRes: Boolean = false,
    val details: String = "",
)

@Singleton
class AudioQualityManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()

    private val _qualityBadgeFlow = MutableStateFlow(PlaybackQualityBadgeInfo())
    val qualityBadgeFlow: StateFlow<PlaybackQualityBadgeInfo> = _qualityBadgeFlow.asStateFlow()

    fun isMeteredNetwork(): Boolean {
        val cm = connectivityManager ?: return false
        return cm.isActiveNetworkMetered
    }

    fun isWifiConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun setChecking(mediaId: String) {
        _qualityBadgeFlow.value = PlaybackQualityBadgeInfo(
            mediaId = mediaId,
            state = PlaybackQualityBadgeState.CHECKING,
            label = "Checking quality…",
        )
    }

    fun setUpgrading(mediaId: String) {
        _qualityBadgeFlow.value = _qualityBadgeFlow.value.copy(
            mediaId = mediaId,
            state = PlaybackQualityBadgeState.UPGRADING,
            label = "Upgrading quality",
        )
    }

    fun setFinal(
        mediaId: String,
        label: String,
        isLossless: Boolean = false,
        isHiRes: Boolean = false,
        details: String = "",
    ) {
        _qualityBadgeFlow.value = PlaybackQualityBadgeInfo(
            mediaId = mediaId,
            state = PlaybackQualityBadgeState.FINAL,
            label = label,
            isLossless = isLossless,
            isHiRes = isHiRes,
            details = details,
        )
    }
}
