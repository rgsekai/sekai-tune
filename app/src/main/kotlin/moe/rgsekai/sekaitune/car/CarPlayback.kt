package moe.rgsekai.sekaitune.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.link.LinkPlayer
import moe.rgsekai.sekaitune.playback.MusicService
import moe.rgsekai.sekaitune.playback.PlayerConnection
import moe.rgsekai.sekaitune.playback.queues.Queue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// the car's line to the music service: binds it, hands android auto its media session, and plays what's picked on the car screen
@UnstableApi
class CarPlayback(
    private val context: Context,
    private val database: MusicDatabase,
    private val scope: CoroutineScope,
) {
    private val _service = MutableStateFlow<MusicService?>(null)
    val service: StateFlow<MusicService?> = _service.asStateFlow()

    private var bound = false

    // a paired phone steering this one needs a player even when Sekai Tune's own screen was never opened
    private var standIn: PlayerConnection? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val music = binder as? MusicService.MusicBinder ?: return
            _service.value = music.service
            dropStandIn()
            standIn = PlayerConnection(context, music, database, scope).also {
                // nothing of it is on screen, so its pollers rest
                it.isUiVisible.value = false
                LinkPlayer.background = it
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            dropStandIn()
        }
    }

    fun bind() {
        if (bound) return
        bound = context.bindService(Intent(context, MusicService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        _service.value = null
        dropStandIn()
    }

    // the token android auto's player reads; null until the service is bound
    fun sessionToken(): MediaSessionCompat.Token? =
        _service.value?.let { MediaSessionCompat.Token.fromToken(it.platformSessionToken) }

    // a pick made before the service is up waits for it instead of getting lost
    fun play(queue: Queue) {
        _service.value?.let { it.playQueue(queue); return }
        scope.launch { service.filterNotNull().first().playQueue(queue) }
    }

    fun togglePlayPause() {
        val player = _service.value?.player ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    private fun dropStandIn() {
        val old = standIn ?: return
        LinkPlayer.releaseBackground(old)
        old.dispose()
        standIn = null
    }
}

