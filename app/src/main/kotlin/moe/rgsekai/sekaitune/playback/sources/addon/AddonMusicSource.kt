/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources.addon

import moe.rgsekai.sekaitune.playback.sources.MusicSource
import moe.rgsekai.sekaitune.playback.sources.SourceHealth
import moe.rgsekai.sekaitune.playback.sources.SourceKind
import moe.rgsekai.sekaitune.playback.sources.SourceStream
import moe.rgsekai.sekaitune.playback.sources.SourceTrack
import moe.rgsekai.sekaitune.playback.sources.StreamFormat
import moe.rgsekai.sekaitune.playback.sources.StreamRequest
import timber.log.Timber

/**
 * Concrete [MusicSource] implementation backed by an external HTTP REST addon server.
 */
class AddonMusicSource(
    override val configId: String,
    override val displayName: String,
    val client: AddonClient,
) : MusicSource {

    override val kind: SourceKind = SourceKind.ADDON

    override suspend fun health(): SourceHealth {
        return when (val response = client.fetchManifest()) {
            is AddonResponse.Success -> {
                val manifest = response.value
                val details = listOfNotNull(
                    manifest.name.takeIf { it.isNotBlank() },
                    manifest.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                ).joinToString(" ")
                SourceHealth.Ok(details.ifBlank { "Connected" })
            }
            is AddonResponse.HttpError -> {
                if (response.statusCode == 401 || response.statusCode == 403) {
                    SourceHealth.Rejected("Authentication rejected (HTTP ${response.statusCode})")
                } else {
                    SourceHealth.Unreachable("HTTP ${response.statusCode}: ${response.message}")
                }
            }
            is AddonResponse.RateLimited -> {
                SourceHealth.Unreachable("Rate limited by server (retry after ${response.retryAfterSeconds ?: 0}s)")
            }
            is AddonResponse.SerializationError -> {
                SourceHealth.Unreachable("Server returned unexpected manifest format")
            }
            is AddonResponse.NetworkError -> {
                SourceHealth.Unreachable(response.cause.message ?: "Connection timed out or failed")
            }
        }
    }

    override suspend fun search(query: String, limit: Int): Result<List<SourceTrack>> {
        return when (val response = client.search(query)) {
            is AddonResponse.Success -> {
                val tracks = response.value.take(limit).map { dto ->
                    SourceTrack(
                        id = dto.id,
                        title = dto.title,
                        artists = dto.resolvedArtists(),
                        album = dto.resolvedAlbum,
                        durationSec = dto.resolvedDurationSec(),
                        format = dto.format,
                        audioQuality = dto.audioQuality,
                        streamUrl = dto.resolvedStreamUrl(),
                        artworkUrl = dto.resolvedArtworkUrl(),
                    )
                }
                Result.success(tracks)
            }
            is AddonResponse.HttpError -> {
                if (AddonResponseClassifier.isNoMatchHttp(response.statusCode, response.message)) {
                    Timber.d("Addon search NO_MATCH (HTTP %d) from %s: %s", response.statusCode, displayName, response.message)
                    Result.success(emptyList())
                } else {
                    val reason = AddonResponseClassifier.formatFailureReason(response)
                    Result.failure(IllegalStateException(reason))
                }
            }
            is AddonResponse.RateLimited -> {
                val reason = AddonResponseClassifier.formatFailureReason(response)
                Result.failure(IllegalStateException(reason))
            }
            is AddonResponse.SerializationError -> {
                val reason = AddonResponseClassifier.formatFailureReason(response)
                Result.failure(IllegalStateException(reason, response.cause))
            }
            is AddonResponse.NetworkError -> {
                val reason = AddonResponseClassifier.formatFailureReason(response)
                Result.failure(IllegalStateException(reason, response.cause))
            }
        }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): Result<SourceStream> {
        val quality = when (request) {
            is StreamRequest.Lossless -> "lossless"
            is StreamRequest.Best -> "high"
            is StreamRequest.Capped -> "low"
        }

        return when (val response = client.stream(trackId, quality)) {
            is AddonResponse.Success -> {
                val dto = response.value
                val streamUrl = dto.resolvedUrl()
                if (streamUrl.isNullOrBlank()) {
                    val err = dto.error ?: "No stream URL returned by addon"
                    if (AddonResponseClassifier.isNoMatchHttp(404, err)) {
                        Timber.d("Addon stream NO_MATCH from %s: %s", displayName, err)
                        Result.failure(AddonNoMatchException(404, err))
                    } else {
                        val reason = AddonResponseClassifier.formatFailureReason(IllegalStateException(err))
                        Result.failure(IllegalStateException(reason))
                    }
                } else {
                    val stream = SourceStream(
                        url = streamUrl,
                        format = StreamFormat.fromString(dto.format ?: dto.codec),
                        bitrateKbps = dto.resolvedBitrate(),
                        sampleRateHz = dto.resolvedSampleRateHz(),
                        bitDepth = dto.resolvedBitDepth(),
                        headers = dto.headers ?: emptyMap(),
                        sourceConfigId = configId,
                    )
                    Result.success(stream)
                }
            }
            is AddonResponse.HttpError -> {
                if (AddonResponseClassifier.isNoMatchHttp(response.statusCode, response.message)) {
                    Timber.d("Addon stream NO_MATCH (HTTP %d) from %s: %s", response.statusCode, displayName, response.message)
                    Result.failure(AddonNoMatchException(response.statusCode, response.message))
                } else {
                    val reason = AddonResponseClassifier.formatFailureReason(response)
                    Result.failure(IllegalStateException(reason))
                }
            }
            is AddonResponse.RateLimited -> {
                val reason = AddonResponseClassifier.formatFailureReason(response)
                Result.failure(IllegalStateException(reason))
            }
            is AddonResponse.SerializationError -> {
                val reason = AddonResponseClassifier.formatFailureReason(response)
                Result.failure(IllegalStateException(reason, response.cause))
            }
            is AddonResponse.NetworkError -> {
                val reason = AddonResponseClassifier.formatFailureReason(response)
                Result.failure(IllegalStateException(reason, response.cause))
            }
        }
    }
}
