package moe.rgsekai.sekaitune.car

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

// a wrapper around exo player that can swap the song's artist for the line being sung, for android auto's own player
class CarLyricsPlayer(player: Player) : ForwardingPlayer(player) {
    data class Overlay(val mediaId: String, val title: String, val line: String)

    private val listeners = mutableListOf<Player.Listener>()

    @Volatile private var overlay: Overlay? = null

    fun show(next: Overlay?) {
        if (next == null && overlay == null) return
        overlay = next
        val metadata = mediaMetadata
        listeners.forEach { it.onMediaMetadataChanged(metadata) }
    }

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
    }

    override fun getMediaMetadata(): MediaMetadata {
        val real = super.getMediaMetadata()
        val shown = overlay ?: return real
        if (currentMediaItem?.mediaId != shown.mediaId) return real
        return real.buildUpon()
            .setTitle(shown.title)
            .setDisplayTitle(shown.title)
            .setArtist(shown.line)
            .setSubtitle(shown.line)
            .build()
    }
}

