package moe.rgsekai.sekaitune.simpstream

import io.ktor.client.call.body
import kotlinx.coroutines.CancellationException
import moe.rgsekai.sekaitune.innertube.PlaybackAuthState
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.ResponseContext
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rgsekai.sekaitune.innertube.models.response.PlayerResponse
import moe.rgsekai.sekaitune.simpstream.extractor.ExtractSource
import moe.rgsekai.sekaitune.simpstream.extractor.SimpStreamExtractor
import moe.rgsekai.sekaitune.simpstream.extractor.contentLengthOf
import kotlin.random.Random

private const val TAG = "SimpMusicPlayer"

object SimpMusicPlayer {
    private val extractor by lazy { SimpStreamExtractor() }

    fun getExtractSource(videoId: String): String? = ExtractSource.of(videoId)

    fun isManifestUrl(url: String): Boolean = url.contains(".m3u8") || url.contains(".mpd") || url.contains("manifest")

    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        authState: PlaybackAuthState = YouTube.currentPlaybackAuthState(),
    ): Result<Pair<String, PlayerResponse>> =
        runCatching {
            val cpn =
                (1..16)
                    .map {
                        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[
                            Random.nextInt(0, 64),
                        ]
                    }.joinToString("")

            val signatureTimestamp = (System.currentTimeMillis() / 86400000L).toInt()

            val streamsList = extractor.extractStreams(videoId)
            if (streamsList.isEmpty()) {
                throw IllegalStateException("SimpStream extractor returned no usable streams for $videoId")
            }

            val webRemixResponse =
                runCatching {
                    YouTube
                        .player(
                            videoId = videoId,
                            playlistId = playlistId,
                            client = WEB_REMIX,
                            signatureTimestamp = signatureTimestamp,
                            setLogin = authState.hasLoginCookie,
                            authState = authState,
                        ).getOrNull()
                }.getOrNull()

            val baseStreamingData = webRemixResponse?.streamingData
            val updatedFormats = mutableListOf<PlayerResponse.StreamingData.Format>()

            val updatedAdaptiveFormats =
                if (baseStreamingData?.adaptiveFormats?.isNotEmpty() == true) {
                    baseStreamingData.adaptiveFormats.map { adaptiveFormat ->
                        val url = streamsList.find { it.first == adaptiveFormat.itag }?.second
                        adaptiveFormat.copy(
                            url = url,
                            contentLength = url?.let(::contentLengthOf) ?: adaptiveFormat.contentLength,
                        )
                    }
                } else {
                    streamsList.map { (itag, url) ->
                        PlayerResponse.StreamingData.Format(
                            itag = itag,
                            url = url,
                            mimeType = if (itag == 140 || itag == 139) "audio/mp4; codecs=\"mp4a.40.2\"" else "audio/webm; codecs=\"opus\"",
                            bitrate = when (itag) {
                                140 -> 128000
                                251 -> 160000
                                250 -> 70000
                                249 -> 50000
                                139 -> 48000
                                else -> 128000
                            },
                            width = null,
                            height = null,
                            contentLength = contentLengthOf(url),
                            quality = "tiny",
                            fps = null,
                            qualityLabel = null,
                            averageBitrate = when (itag) {
                                140 -> 128000
                                251 -> 160000
                                250 -> 70000
                                249 -> 50000
                                139 -> 48000
                                else -> 128000
                            },
                            audioQuality = "AUDIO_QUALITY_MEDIUM",
                            approxDurationMs = null,
                            audioSampleRate = 44100,
                            audioChannels = 2,
                            loudnessDb = null,
                            lastModified = null,
                            signatureCipher = null,
                            cipher = null,
                        )
                    }
                }

            // Append manifests as extra formats if available
            streamsList.filter { isManifestUrl(it.second) }.forEach { manifest ->
                updatedFormats.add(
                    PlayerResponse.StreamingData.Format(
                        itag = manifest.first,
                        url = manifest.second,
                        mimeType = if (manifest.second.contains(".m3u8")) "application/x-mpegURL" else "application/dash+xml",
                        bitrate = 0,
                        width = null,
                        height = null,
                        contentLength = 0L,
                        quality = "",
                        fps = null,
                        qualityLabel = null,
                        averageBitrate = null,
                        audioQuality = null,
                        approxDurationMs = null,
                        audioSampleRate = null,
                        audioChannels = null,
                        loudnessDb = null,
                        lastModified = null,
                        signatureCipher = null,
                        cipher = null,
                    ),
                )
            }

            val mergedStreamingData =
                PlayerResponse.StreamingData(
                    expiresInSeconds = baseStreamingData?.expiresInSeconds ?: 21600,
                    formats = updatedFormats,
                    adaptiveFormats = updatedAdaptiveFormats,
                )

            val baseResponse = webRemixResponse ?: PlayerResponse(
                responseContext = ResponseContext(visitorData = null, serviceTrackingParams = null),
                playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK", reason = null),
                playerConfig = null,
                playbackTracking = null,
                videoDetails = null,
                streamingData = mergedStreamingData,
            )

            val mergedResponse = baseResponse.copy(
                playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK", reason = null),
                streamingData = mergedStreamingData,
            )

            Pair(cpn, mergedResponse)
        }
}

