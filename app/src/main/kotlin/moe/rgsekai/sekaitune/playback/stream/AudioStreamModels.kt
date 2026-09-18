/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.stream

import moe.rgsekai.sekaitune.constants.AudioQuality
import moe.rgsekai.sekaitune.constants.PlayerStreamClient
import moe.rgsekai.sekaitune.innertube.PlaybackAuthState
import moe.rgsekai.sekaitune.innertube.models.response.PlayerResponse

enum class StreamPurpose {
    PLAYBACK,
    DOWNLOAD,
}

enum class StreamResolutionPriority {
    FOREGROUND,
    BACKGROUND,
}

data class AudioStreamRequest(
    val mediaId: String,
    val quality: AudioQuality = AudioQuality.AUTO,
    val networkMetered: Boolean = false,
    val purpose: StreamPurpose = StreamPurpose.PLAYBACK,
    val preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
    val authState: PlaybackAuthState,
)

data class ResolvedAudioStream(
    val url: String,
    val format: PlayerResponse.StreamingData.Format,
    val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
    val videoDetails: PlayerResponse.VideoDetails?,
    val playbackTracking: PlayerResponse.PlaybackTracking?,
    val expiresAtMs: Long,
    val authFingerprint: String,
)
