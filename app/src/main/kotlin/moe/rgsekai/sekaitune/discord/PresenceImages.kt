/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

import moe.rgsekai.sekaitune.ui.utils.resize
import kotlin.math.roundToInt

data class SpinStyle(val secondsPerTurn: Int, val fps: Int)

sealed interface PresencePicture {
    data class Web(val url: String) : PresencePicture
    data class Status(val step: Int, val playing: Boolean) : PresencePicture
    data class Spinning(val sourceUrl: String, val style: SpinStyle) : PresencePicture
    data object Logo : PresencePicture
}

object PresenceImages {
    const val StatusSteps = 20

    private const val AssetsBase = "https://raw.githubusercontent.com/rgsekai/sekai-tune/main/assets/discord/v1"

    const val LogoUrl = "$AssetsBase/sekaitune.png"

    fun statusUrl(status: PresencePicture.Status): String {
        val state = if (status.playing) "playing" else "paused"
        return "$AssetsBase/status_${state}_${status.step.toString().padStart(2, '0')}.png"
    }

    fun pick(source: PresenceImage, track: NowPlaying, nowMs: Long, spin: SpinStyle): PresencePicture? {
        val artwork = track.artworkUrl?.takeIf(::isWebImage)?.resize(512, 512)
        val artistArtwork = track.artistArtworkUrl?.takeIf(::isWebImage)?.resize(512, 512)
        return when (source) {
            PresenceImage.OFF -> null
            PresenceImage.ARTWORK -> artwork?.let(PresencePicture::Web) ?: PresencePicture.Logo
            PresenceImage.ARTIST -> (artistArtwork ?: artwork)?.let(PresencePicture::Web) ?: PresencePicture.Logo
            PresenceImage.APP_LOGO -> PresencePicture.Logo
            PresenceImage.PLAYBACK_STATUS -> PresencePicture.Status(
                step = (track.progressAt(nowMs) * StatusSteps).roundToInt().coerceIn(0, StatusSteps),
                playing = track.isPlaying,
            )
            PresenceImage.SPINNING_ARTWORK -> artwork?.let { PresencePicture.Spinning(it, spin) } ?: PresencePicture.Logo
            PresenceImage.SPINNING_ARTIST ->
                (artistArtwork ?: artwork)?.let { PresencePicture.Spinning(it, spin) } ?: PresencePicture.Logo
        }
    }

    private fun isWebImage(url: String) = url.startsWith("https://") || url.startsWith("http://")
}

