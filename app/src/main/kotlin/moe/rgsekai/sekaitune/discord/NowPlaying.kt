/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

data class NowPlaying(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val artistArtworkUrl: String?,
    val durationMs: Long,
    val positionMs: Long,
    val readAtMs: Long,
    val speed: Float,
    val isPlaying: Boolean,
    val quality: String?,
    val isYouTube: Boolean,
) {
    fun positionAt(nowMs: Long): Long {
        if (!isPlaying) return positionMs
        val moved = ((nowMs - readAtMs) * speed).toLong()
        return (positionMs + moved).coerceIn(0, durationMs.coerceAtLeast(0))
    }

    fun progressAt(nowMs: Long): Float =
        if (durationMs <= 0) 0f else positionAt(nowMs).toFloat() / durationMs

    companion object {
        fun sample(nowMs: Long) = NowPlaying(
            mediaId = "",
            title = "Fake Plastic Trees",
            artist = "Radiohead",
            album = "The Bends",
            artworkUrl = null,
            artistArtworkUrl = null,
            durationMs = 290_000,
            positionMs = 96_000,
            readAtMs = nowMs,
            speed = 1f,
            isPlaying = true,
            quality = "OPUS · 160 kbps",
            isYouTube = false,
        )
    }
}

