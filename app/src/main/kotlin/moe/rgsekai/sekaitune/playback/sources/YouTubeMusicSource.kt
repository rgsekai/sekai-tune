/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

/**
 * Built-in YouTube / YouTube Music baseline source.
 * This is the fallback source in Sekai Tune; it is always enabled and pinned at the bottom of the source order.
 */
class YouTubeMusicSource(
    override val configId: String = YOUTUBE_CONFIG_ID,
    override val displayName: String = "YouTube",
) : MusicSource {

    override val kind: SourceKind = SourceKind.YOUTUBE

    override suspend fun health(): SourceHealth {
        return SourceHealth.Ok("Baseline active")
    }

    override suspend fun search(query: String, limit: Int): Result<List<SourceTrack>> {
        // Baseline catalog search is handled by existing YouTube repositories in this phase.
        return Result.success(emptyList())
    }

    override suspend fun stream(trackId: String, request: StreamRequest): Result<SourceStream> {
        // Stream resolution for YouTube baseline is handled by existing playback use cases.
        return Result.failure(UnsupportedOperationException("YouTube baseline streams are resolved via primary playback pipeline"))
    }

    companion object {
        const val YOUTUBE_CONFIG_ID = "builtin_youtube"
    }
}
