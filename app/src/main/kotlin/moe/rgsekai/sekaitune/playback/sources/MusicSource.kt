/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

/**
 * Health check result for an audio source.
 */
sealed interface SourceHealth {
    data class Ok(val details: String? = null) : SourceHealth
    data class Unreachable(val message: String) : SourceHealth
    data class Rejected(val message: String) : SourceHealth
}

/**
 * Requested quality tier when resolving an audio stream.
 */
sealed interface StreamRequest {
    data object Best : StreamRequest
    data object Lossless : StreamRequest
    data class Capped(val maxKbps: Int) : StreamRequest
}

/**
 * Audio codec/container format of a stream.
 */
enum class StreamFormat {
    FLAC,
    ALAC,
    WAV,
    AAC,
    OPUS,
    MP3,
    DASH,
    HLS,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): StreamFormat {
            val normalized = value?.trim()?.uppercase() ?: return UNKNOWN
            return when {
                normalized.contains("FLAC") -> FLAC
                normalized.contains("ALAC") -> ALAC
                normalized.contains("WAV") -> WAV
                normalized.contains("AAC") || normalized.contains("M4A") -> AAC
                normalized.contains("OPUS") || normalized.contains("OGG") -> OPUS
                normalized.contains("MP3") -> MP3
                normalized.contains("DASH") || normalized.contains("MPD") -> DASH
                normalized.contains("HLS") || normalized.contains("M3U8") -> HLS
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Normalized track metadata returned by a source search.
 */
data class SourceTrack(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationSec: Int? = null,
    val format: String? = null,
    val audioQuality: String? = null,
    val streamUrl: String? = null,
    val artworkUrl: String? = null,
)

/**
 * Resolved stream payload ready for playback.
 */
data class SourceStream(
    val url: String,
    val format: StreamFormat = StreamFormat.UNKNOWN,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val durationSec: Int? = null,
    val isBelowRequestedQuality: Boolean = false,
    val sourceConfigId: String? = null,
)

/**
 * Common abstraction for all audio catalog providers (addon servers, baseline YouTube, etc.).
 */
interface MusicSource {
    val configId: String
    val kind: SourceKind
    val displayName: String

    suspend fun health(): SourceHealth

    suspend fun search(query: String, limit: Int = 25): Result<List<SourceTrack>>

    suspend fun stream(trackId: String, request: StreamRequest): Result<SourceStream>
}
