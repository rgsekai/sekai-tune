package moe.rgsekai.sekaitune.car

import android.content.Context
import android.content.pm.PackageManager
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.constants.CarLyricsKey
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// lyrics on android auto's own player
@UnstableApi
class CarLyrics(
    private val context: Context,
    private val source: CarLyricsSource,
    private val playing: StateFlow<MediaMetadata?>,
    private val player: () -> Player,
    private var overlay: CarLyricsPlayer,
    private val scope: CoroutineScope,
) {
    private val automotive = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    private val connection = CarConnection(context)
    private val inCar = MutableStateFlow(automotive)

    private val onConnection = Observer<Int> { type -> inCar.value = automotive || type != CarConnection.CONNECTION_TYPE_NOT_CONNECTED }
    private var job: Job? = null
    private var shown: CarLyricsPlayer.Overlay? = null

    fun start() {
        if (job != null) return
        connection.type.observeForever(onConnection)
        job = scope.launch {
            val wanted = context.dataStore.data.map { it[CarLyricsKey] ?: true }.distinctUntilChanged()
            combine(inCar, wanted) { car, on -> car && on }
                .distinctUntilChanged()
                .collectLatest { active -> if (active) follow() else show(null) }
        }
    }

    fun stop() {
        connection.type.removeObserver(onConnection)
        job?.cancel()
        job = null
        show(null)
    }

    fun attach(next: CarLyricsPlayer) {
        overlay = next
        next.show(shown)
    }

    private fun show(next: CarLyricsPlayer.Overlay?) {
        shown = next
        overlay.show(next)
    }

    private suspend fun follow() {
        try {
            playing.distinctUntilChangedBy { it?.id }.collectLatest { song ->
                show(null)
                if (song != null) sing(song)
            }
        } finally {
            show(null)
        }
    }

    private suspend fun sing(song: MediaMetadata) {
        val lyrics = source.load(song)?.takeIf { it.synced } ?: return
        val title = listOf(song.title, song.artists.joinToString { it.name }).filter { it.isNotBlank() }.joinToString(" — ")
        var shownLine = Int.MIN_VALUE
        while (true) {
            val current = player()
            val position = current.currentPosition
            val index = lyrics.lineAt(position)
            val line = lyrics.lines.getOrNull(index)?.takeIf {
                it.text.isNotBlank() && current.currentMediaItem?.mediaId == song.id && !lyrics.isBreak(index, position)
            }
            if (line == null) {
                if (shownLine != Int.MIN_VALUE) show(null)
                shownLine = Int.MIN_VALUE
            } else if (index != shownLine) {
                show(CarLyricsPlayer.Overlay(song.id, title, line.text.trim()))
                shownLine = index
            }
            delay(if (current.isPlaying) PlayingTickMs else PausedTickMs)
        }
    }

    private companion object {
        const val PlayingTickMs = 90L
        const val PausedTickMs = 500L
    }
}

