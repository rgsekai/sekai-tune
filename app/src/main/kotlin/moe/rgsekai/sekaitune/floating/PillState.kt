package moe.rgsekai.sekaitune.floating

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.media3.common.C
import androidx.media3.common.Player
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.ui.player.PlaybackClock

@Stable
internal class PillState(private val player: () -> Player?) {
    var song by mutableStateOf<MediaMetadata?>(null)

    // Null while lyrics are still loading; bubble shows title/artist meanwhile
    var script by mutableStateOf<PillScript?>(null)

    // Extracted cover art accent color for the bubble
    var accent by mutableStateOf<Color?>(null)

    var playing by mutableStateOf(false)

    // Bumped on seek or speed changes so timing is recalibrated
    var seekTick by mutableIntStateOf(0)

    // Expanded controls state (play/pause and seek bar visible)
    var expanded by mutableStateOf(false)

    // Docked at the right edge when true, left edge when false
    var dockEnd by mutableStateOf(false)

    // Tucked into cover disc while dragging until it lands
    var tucked by mutableStateOf(false)

    // Finger carrying the bubble
    var lifted by mutableStateOf(false)

    // Bumped on touch during expansion to reset auto-collapse timer
    var touchTick by mutableIntStateOf(0)

    var offsetMs = 0

    val lyricsClock = PlaybackClock(player) { offsetMs.toLong() }

    fun songPositionMs(): Long = player()?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun durationMs(): Long {
        val known = player()?.duration ?: C.TIME_UNSET
        if (known != C.TIME_UNSET && known > 0L) return known
        return (song?.duration ?: 0) * 1000L
    }

    fun speed(): Float = player()?.playbackParameters?.speed?.takeIf { it > 0f } ?: 1f
}

