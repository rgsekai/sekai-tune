package moe.rgsekai.sekaitune.link.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.MediaItem
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.di.ApplicationScope
import moe.rgsekai.sekaitune.extensions.metadata
import moe.rgsekai.sekaitune.link.LinkPlayer
import moe.rgsekai.sekaitune.link.LinkSongs
import moe.rgsekai.sekaitune.link.isTelevision
import moe.rgsekai.sekaitune.link.phone.LiveDevice
import moe.rgsekai.sekaitune.link.phone.PairedDevice
import moe.rgsekai.sekaitune.link.phone.PhoneLinkHost
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.playback.queues.Queue
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RemoteControlEntryPoint {
    fun remoteControl(): RemoteControl
}

// the phone as a remote: which paired device it steers, what that device plays, and the songs picked here that go there instead
@Singleton
class RemoteControl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val host: PhoneLinkHost,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _target = MutableStateFlow<RemoteTarget?>(null)
    val target: StateFlow<RemoteTarget?> = _target.asStateFlow()

    private val _now = MutableStateFlow<RemoteNow?>(null)
    val now: StateFlow<RemoteNow?> = _now.asStateFlow()

    private val _reachable = MutableStateFlow(true)
    val reachable: StateFlow<Boolean> = _reachable.asStateFlow()

    // how many remotes are open; while one is, the device is asked every second instead of now and then
    private val watchers = MutableStateFlow(0)

    init {
        if (!context.isTelevision()) {
            scope.launch { poll() }
            scope.launch { host.arrivals.collect(::onArrival) }
            // a device unlinked, or with remote control switched off, can't be steered any more
            scope.launch {
                host.devices.collect { list ->
                    val steered = _target.value ?: return@collect
                    if (list.none { it.id == steered.id && it.remoteControl }) stop()
                }
            }
            // Sekai Tune may open after the device already turned up, which is an arrival too
            host.liveDevices().firstOrNull { live -> autoControls(live.id) }?.let(::onArrival)
        }
    }

    // steer a device from here on, and when asked, what this phone is playing moves there first
    fun start(device: PairedDevice, handOver: Boolean) {
        _target.value = RemoteTarget(device.id, device.name, device.kind)
        _now.value = null
        _reachable.value = host.liveDevice(device.id) != null
        if (handOver && LinkPlayer.connection?.isPlaying?.value == true) handOver()
    }

    fun stop() {
        _target.value = null
        _now.value = null
    }

    fun isSteering(id: String) = _target.value?.id == id

    // the remote sheet asks for a lively position while it's open
    fun watch(open: Boolean) = watchers.update { (it + if (open) 1 else -1).coerceAtLeast(0) }

    fun toggle() {
        val now = System.currentTimeMillis()
        // the button answers at once, the device's own answer follows a moment later
        _now.update { it?.copy(playing = !it.playing, position = it.positionAt(now), readAt = now) }
        command("toggle")
    }

    fun next() = command("next")

    fun previous() = command("previous")

    fun seek(position: Long) {
        _now.update { it?.copy(position = position, readAt = System.currentTimeMillis()) }
        command("seek", JSONObject().put("position", position))
    }

    fun like() {
        _now.update { it?.copy(liked = !it.liked) }
        command("like")
    }

    // a tv pulls the phone's library now, so a playlist change made here shows there straight away
    fun syncLibrary() = command("sync")

    fun jump(index: Int) = send("/jump", JSONObject().put("index", index))

    fun remove(index: Int) {
        _now.update { now -> now?.copy(upNext = now.upNext.filterNot { it.index == index }) }
        send("/remove", JSONObject().put("index", index))
    }

    // the steered device's settings, in its own words; null when it couldn't be reached
    suspend fun settings(): List<RemoteSetting>? = ask("/settings", null)?.let(RemoteSetting::listFromJson)

    suspend fun changeSetting(key: String, value: Any): List<RemoteSetting>? =
        ask("/settings", JSONObject().put("key", key).put("value", value))?.let(RemoteSetting::listFromJson)

    // a queue picked anywhere in the app plays on the steered device instead; false when nothing is steered
    fun takeQueue(queue: Queue): Boolean {
        val target = _target.value ?: return false
        scope.launch {
            val status = runCatching { withContext(Dispatchers.IO) { queue.getInitialStatus() } }.getOrNull()
            val picked = status?.items?.getOrNull(status.mediaItemIndex)?.mediaId
            val songs = status?.items?.mapNotNull { it.metadata }.orEmpty()
            if (status == null || songs.isEmpty()) {
                toast(context.getString(R.string.tv_menu_unreachable, target.name))
                return@launch
            }
            // a long playlist goes over as a window around the song picked
            val start = songs.indexOfFirst { it.id == picked }.coerceAtLeast(0)
            val from = (start - MaxBefore).coerceAtLeast(0)
            val window = songs.subList(from, minOf(songs.size, from + MaxSent))
            val body = JSONObject()
                .put("songs", LinkSongs.listToJson(window))
                .put("index", start - from)
                .put("position", status.position)
                .apply { status.title?.let { put("title", it) } }
            announce(target, ask("/play", body), R.string.tv_menu_sent, window.getOrNull(start - from))
        }
        return true
    }

    fun takeNext(items: List<MediaItem>): Boolean = takeSongs(items, "/next", R.string.tv_menu_next_sent)

    fun takeQueueEnd(items: List<MediaItem>): Boolean = takeSongs(items, "/queue", R.string.remote_queued_on)

    // what this phone plays moves to the steered device, at the same spot, and the phone goes quiet
    fun handOver() {
        val target = _target.value ?: return
        val connection = LinkPlayer.connection ?: return
        val player = connection.player
        val from = player.currentMediaItemIndex.coerceAtLeast(0)
        val songs = (from until minOf(player.mediaItemCount, from + MaxSent)).mapNotNull { player.getMediaItemAt(it).metadata }
        if (songs.isEmpty()) return
        val body = JSONObject().put("songs", LinkSongs.listToJson(songs)).put("index", 0).put("position", player.currentPosition)
        scope.launch {
            val sent = ask("/play", body)
            if (sent != null) withContext(Dispatchers.Main) { connection.player.pause() }
            announce(target, sent, R.string.tv_menu_sent, songs.first())
        }
    }

    private fun takeSongs(items: List<MediaItem>, path: String, done: Int): Boolean {
        val target = _target.value ?: return false
        val songs = items.mapNotNull { it.metadata }
        if (songs.isEmpty()) return true
        scope.launch { announce(target, ask(path, JSONObject().put("songs", LinkSongs.listToJson(songs))), done, songs.first()) }
        return true
    }

    private fun announce(target: RemoteTarget, answer: JSONObject?, done: Int, song: MediaMetadata?) {
        if (answer == null) {
            toast(context.getString(R.string.tv_menu_unreachable, target.name))
            return
        }
        toast(context.getString(done, target.name))
        scope.launch { refresh(target) }
    }

    private fun toast(msg: String) {
        mainHandler.post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun command(action: String, extra: JSONObject = JSONObject()) = send("/control", extra.put("action", action))

    private fun send(path: String, body: JSONObject) {
        val target = _target.value ?: return
        scope.launch {
            if (ask(path, body) == null) toast(context.getString(R.string.tv_menu_unreachable, target.name))
            refresh(target)
        }
    }

    // one call to the steered device: a get without a body, a post with one
    private suspend fun ask(path: String, body: JSONObject?): JSONObject? {
        val target = _target.value ?: return null
        val live = host.liveDevice(target.id)
        if (live == null) {
            _reachable.value = false
            return null
        }
        return host.tell(live, path, body)
    }

    private suspend fun refresh(target: RemoteTarget) {
        val answer = ask("/now", null)
        if (_target.value?.id != target.id) return
        _reachable.value = answer != null
        if (answer != null) _now.value = RemoteNow.fromJson(answer, System.currentTimeMillis())
    }

    // only while Sekai Tune is on screen, quick while a remote is open and slow otherwise, so the pill knows the device is still there
    private suspend fun poll() {
        combine(_target, host.foreground, watchers) { target, shown, open -> Triple(target, shown, open > 0) }
            .distinctUntilChanged()
            .collectLatest { (target, shown, open) ->
                if (target == null || !shown) return@collectLatest
                while (true) {
                    refresh(target)
                    delay(if (open) FastPollMs else SlowPollMs)
                }
            }
    }

    private fun autoControls(id: String) = host.devices.value.any { it.id == id && it.autoControl && it.remoteControl }

    // a device that just turned up is taken over when it asked for that and nothing else is steered
    private fun onArrival(device: LiveDevice) {
        if (_target.value != null || !autoControls(device.id)) return
        val paired = host.devices.value.firstOrNull { it.id == device.id } ?: return
        start(paired, handOver = false)
        toast(context.getString(R.string.remote_controlling, paired.name))
    }

    private companion object {
        const val FastPollMs = 1_000L
        const val SlowPollMs = 5_000L
        const val MaxSent = 300
        const val MaxBefore = 50
    }
}
