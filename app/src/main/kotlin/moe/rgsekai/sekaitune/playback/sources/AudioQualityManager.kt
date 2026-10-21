package moe.rgsekai.sekaitune.playback.sources

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rgsekai.sekaitune.constants.LowDataModeKey
import moe.rgsekai.sekaitune.constants.MaxQualityNetwork
import moe.rgsekai.sekaitune.constants.PlayMaxQualityOnKey
import moe.rgsekai.sekaitune.utils.dataStore
import moe.rgsekai.sekaitune.utils.get
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaybackQualityBadgeState {
    CHECKING,
    UPGRADING,
    FINAL,
}

enum class AudioPipelineStatus {
    PRELOADED_INSTANT,
    UPGRADED_FROM_OPUS,
    SETTLED_NO_MATCH,
    SETTLED_LOWER_QUALITY,
    SETTLED_METERED,
    SETTLED_RATE_LIMITED,
}

data class PlaybackQualityBadgeInfo(
    val mediaId: String? = null,
    val state: PlaybackQualityBadgeState = PlaybackQualityBadgeState.FINAL,
    val label: String = "",
    val isLossless: Boolean = false,
    val isHiRes: Boolean = false,
    val details: String = "",
    val sourceName: String = "",
    val format: String = "",
    val bitDepth: String = "",
    val sampleRate: String = "",
    val bitrate: String = "",
    val pcmDataRate: String = "",
    val channels: String = "Stereo",
    val decoderName: String = "",
    val outputFormat: String = "",
    val resamplerInfo: String = "",
    val transferSize: String = "",
    val statusText: String = "",
    val pipelineStatus: AudioPipelineStatus? = null,
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

    fun isCellularConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun getPlayMaxQualityOn(): MaxQualityNetwork {
        val raw = context.dataStore[PlayMaxQualityOnKey]
        return MaxQualityNetwork.fromPreferences(raw)
    }

    fun isLowDataMode(): Boolean {
        return context.dataStore[LowDataModeKey] ?: true
    }

    companion object {
        fun isMaxAllowed(
            preference: MaxQualityNetwork,
            isWifi: Boolean,
            isCellular: Boolean,
            isMetered: Boolean,
            lowDataMode: Boolean,
        ): Boolean {
            // When Low Data Mode is on and the network is metered, it still wins
            if (lowDataMode && isMetered) {
                return false
            }
            return when (preference) {
                MaxQualityNetwork.WIFI_ONLY -> isWifi
                MaxQualityNetwork.MOBILE_DATA_ONLY -> isCellular
                MaxQualityNetwork.WIFI_AND_MOBILE_DATA -> isWifi || isCellular || !isMetered
            }
        }
    }

    fun isMaxAllowed(
        preference: MaxQualityNetwork = getPlayMaxQualityOn(),
        isWifi: Boolean = isWifiConnected(),
        isCellular: Boolean = isCellularConnected(),
        isMetered: Boolean = isMeteredNetwork(),
        lowDataMode: Boolean = isLowDataMode(),
    ): Boolean = Companion.isMaxAllowed(
        preference = preference,
        isWifi = isWifi,
        isCellular = isCellular,
        isMetered = isMetered,
        lowDataMode = lowDataMode,
    )

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
        sourceName: String = "",
        format: String = "",
        bitDepth: String = "",
        sampleRate: String = "",
        bitrate: String = "",
        pcmDataRate: String = "",
        channels: String = "Stereo",
        decoderName: String = "",
        outputFormat: String = "",
        resamplerInfo: String = "",
        transferSize: String = "",
        statusText: String = "",
        pipelineStatus: AudioPipelineStatus? = null,
    ) {
        _qualityBadgeFlow.value = PlaybackQualityBadgeInfo(
            mediaId = mediaId,
            state = PlaybackQualityBadgeState.FINAL,
            label = label,
            isLossless = isLossless,
            isHiRes = isHiRes,
            details = details,
            sourceName = sourceName,
            format = format,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
            bitrate = bitrate,
            pcmDataRate = pcmDataRate,
            channels = channels,
            decoderName = decoderName,
            outputFormat = outputFormat,
            resamplerInfo = resamplerInfo,
            transferSize = transferSize,
            statusText = statusText,
            pipelineStatus = pipelineStatus,
        )
    }
}
