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
                val msg = if (response.message.startsWith("HTTP")) response.message else "HTTP ${response.statusCode}: ${response.message}"
                Result.failure(IllegalStateException(msg))
            }
            is AddonResponse.RateLimited -> Result.failure(IllegalStateException("Rate limited (retry after ${response.retryAfterSeconds ?: 0}s)"))
            is AddonResponse.SerializationError -> Result.failure(response.cause)
            is AddonResponse.NetworkError -> Result.failure(response.cause)
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
                    Result.failure(IllegalStateException(dto.error ?: "No stream URL returned by addon"))
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
                val msg = if (response.message.startsWith("HTTP")) response.message else "HTTP ${response.statusCode}: ${response.message}"
                Result.failure(IllegalStateException(msg))
            }
            is AddonResponse.RateLimited -> Result.failure(IllegalStateException("Rate limited (retry after ${response.retryAfterSeconds ?: 0}s)"))
            is AddonResponse.SerializationError -> Result.failure(response.cause)
            is AddonResponse.NetworkError -> Result.failure(response.cause)
        }
    }
}
