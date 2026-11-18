// PhoneLinkHost.kt

package moe.rgsekai.sekaitune.link.phone

import android.content.Context
import androidx.core.content.edit
import moe.rgsekai.sekaitune.BuildConfig
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.di.ApplicationScope
import moe.rgsekai.sekaitune.link.DeviceKind
import moe.rgsekai.sekaitune.link.LinkAdvert
import moe.rgsekai.sekaitune.link.LinkAuth
import moe.rgsekai.sekaitune.link.LinkCredentials
import moe.rgsekai.sekaitune.link.LinkControlRoutes
import moe.rgsekai.sekaitune.link.LinkHttpServer
import moe.rgsekai.sekaitune.link.LinkIdentity
import moe.rgsekai.sekaitune.link.LinkPeer
import moe.rgsekai.sekaitune.link.LinkPlayer
import moe.rgsekai.sekaitune.link.LinkRequest
import moe.rgsekai.sekaitune.link.LinkResponse
import moe.rgsekai.sekaitune.link.LinkWire
import moe.rgsekai.sekaitune.playback.DownloadUtil
import moe.rgsekai.sekaitune.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

// the phone's half of the link: remembers paired devices and keeps a small server up so they can sync, stream and steer each other
@Singleton
class PhoneLinkHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    downloadUtil: DownloadUtil,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(PairedDeviceStore.Prefs, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _devices = MutableStateFlow(PairedDeviceStore.read(prefs))
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private val server = LinkHttpServer("PhoneLink", ::route)
    private var advert: LinkAdvert? = null
    private var advertPort = -1

    // devices that said hello lately; a tv with Sekai Tune open says it every 20 seconds, a phone as long as it's on screen
    private val _live = MutableStateFlow<Map<String, LiveDevice>>(emptyMap())
    val live: StateFlow<Map<String, LiveDevice>> = _live.asStateFlow()

    // a device that wasn't around a moment ago and just turned up, for "take control when it connects"
    private val _arrivals = MutableSharedFlow<LiveDevice>(extraBufferCapacity = 4)
    val arrivals: SharedFlow<LiveDevice> = _arrivals.asSharedFlow()

    // when each tv last streamed a song from this phone, for the notification's "streaming" line
    private val _lastStream = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastStream: StateFlow<Map<String, Long>> = _lastStream.asStateFlow()

    private val _foreground = MutableStateFlow(false)

    // Sekai Tune is on screen here: other phones are checked on and a steered device is asked what it plays
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    private val _notifyShown = MutableStateFlow(prefs.getBoolean(NotifyKey, true))
    val notifyShown: StateFlow<Boolean> = _notifyShown.asStateFlow()

    // the "tell me when the tv syncs" switch
    var notifySync: Boolean
        get() = _notifyShown.value
        set(value) {
            prefs.edit { putBoolean(NotifyKey, value) }
            _notifyShown.value = value
        }

    private val _menuShown = MutableStateFlow(prefs.getBoolean(PlayerMenuKey, true))
    val menuShown: StateFlow<Boolean> = _menuShown.asStateFlow()

    // the "show my devices in the player menu" switch
    var showInPlayerMenu: Boolean
        get() = _menuShown.value
        set(value) {
            prefs.edit { putBoolean(PlayerMenuKey, value) }
            _menuShown.value = value
        }

    // the background switch lives here too, so the boot receiver can read it without the settings store
    var staysReachable: Boolean
        get() = prefs.getBoolean(StayKey, true)
        set(value) {
            prefs.edit { putBoolean(StayKey, value) }
            if (value) PhoneLinkService.start(context) else PhoneLinkService.stop(context)
        }

    // goes up whenever the library changes, so a tv only pulls a fresh copy when there is one
    private val revision = AtomicLong(System.currentTimeMillis())
    private var watchingLibrary = false
    @Volatile private var mark: Pair<Long, String>? = null

    // the library sum each tv was last told "in step" about, so the notice only shows when it got something new
    private val toldLibrary = ConcurrentHashMap<String, String>()

    private val library = PhoneLibraryRoutes(context, database, downloadUtil)
    private val verifier = LinkAuth.Verifier()
    private val control = LinkControlRoutes(context)
    private val peers = PhonePeers(context, this, scope)

    // where the link server listens, -1 while it's down
    val port: Int get() = if (server.isRunning) server.port else -1

    fun liveDevices(now: Long = System.currentTimeMillis()): List<LiveDevice> = _live.value.values.filter { now - it.seenAt < LiveForMs }

    fun liveDevice(id: String, now: Long = System.currentTimeMillis()): LiveDevice? = _live.value[id]?.takeIf { now - it.seenAt < LiveForMs }

    // keeps the server, its advert and the library watch up while any device is paired, quiet otherwise
    @Synchronized
    fun refresh() {
        if (_devices.value.isEmpty()) {
            stop()
            return
        }
        watchLibrary()
        val port = server.start(LinkWire.PhonePort)
        // a phone paired while Sekai Tune is on screen is checked on straight away, not from the next launch
        if (_foreground.value) peers.start()
        if (advert != null && advertPort == port) return
        advert?.close()
        advertPort = port
        advert = LinkAdvert(
            context,
            LinkWire.PhoneServiceType,
            "SekaiTune ${LinkIdentity.name(context)}",
            port,
            mapOf(
                LinkWire.AttrId to LinkIdentity.id(context),
                LinkWire.AttrName to LinkIdentity.name(context),
                LinkWire.AttrKind to DeviceKind.of(context).wire,
            ),
        )
    }

    // the advert made before android 17 let us on the wi-fi never went out, so it's made again
    fun advertiseAgain() {
        scope.launch(Dispatchers.IO) {
            synchronized(this@PhoneLinkHost) {
                advert?.close()
                advert = null
                advertPort = -1
                refresh()
            }
        }
    }

    @Synchronized
    fun stop() {
        peers.stop()
        advert?.close()
        advert = null
        advertPort = -1
        server.stop()
    }

    fun appShown() {
        _foreground.value = true
        scope.launch(Dispatchers.IO) {
            if (_devices.value.isNotEmpty()) refresh()
        }
    }

    fun appHidden() {
        _foreground.value = false
        peers.stop()
    }

    // asks a device in pairing mode to take this phone, with the code its screen shows; the key made here is shared by both from now on
    suspend fun pair(peer: LinkPeer, code: String): Result<PairedDevice> = withContext(Dispatchers.IO) {
        runCatching {
            if (_devices.value.none { it.id == peer.id } && _devices.value.size >= MaxDevices) throw PairException(PairException.Reason.Full)
            val port = server.start(LinkWire.PhonePort)
            val keyPair = LinkAuth.newKeyPair()
            val body = JSONObject()
                .put("code", code.trim())
                .put("phoneId", LinkIdentity.id(context))
                .put("phoneName", LinkIdentity.name(context))
                .put("kind", DeviceKind.of(context).wire)
                .put("userName", "")
                .put("hasAvatar", false)
                .put("avatar", "")
                .put("port", port)
                .put("pub", LinkAuth.publicText(keyPair))
                .put("protocol", LinkWire.ProtocolVersion)
            LinkWire.postJson("${LinkWire.baseUrl(peer.host, peer.port)}/pair", null, body.toString()).use { response ->
                val text = response.body.string()
                when {
                    response.code == 200 -> Unit
                    response.code == 403 -> throw PairException(PairException.Reason.WrongCode)
                    response.code == 426 -> throw PairException(PairException.Reason.Outdated)
                    response.code == 409 && text == FullReply -> throw PairException(PairException.Reason.Full)
                    else -> throw PairException(PairException.Reason.Failed)
                }
                val reply = JSONObject(text)
                val now = System.currentTimeMillis()
                val kind = DeviceKind.fromWire(reply.optString("kind").ifBlank { null }, peer.kind)
                val id = reply.optString("tvId").ifBlank { peer.id }
                val otherPublic = reply.optString("pub").ifBlank { throw PairException(PairException.Reason.Outdated) }
                val key = LinkAuth.sharedKey(keyPair, otherPublic, code, listOf(LinkIdentity.id(context), id))
                    ?: throw PairException(PairException.Reason.Failed)
                val fresh = PairedDevice(id = id, name = "", key = "", kind = kind, pairedAt = now, lastSeenAt = now)
                val paired = (_devices.value.firstOrNull { it.id == id } ?: fresh).copy(
                    name = reply.optString("tvName").ifBlank { peer.name },
                    key = key,
                    kind = kind,
                    pairedAt = now,
                    lastSeenAt = now,
                    host = if (kind.isTv) null else peer.host,
                    port = if (kind.isTv) 0 else reply.optInt("port", LinkWire.PhonePort),
                )
                change { list -> list.filterNot { it.id == id } + paired }
                paired
            }
        }.onSuccess { paired ->
            refresh()
            if (!paired.kind.isTv && paired.host != null) markLive(paired, paired.host, paired.port)
            PhoneLinkService.start(context)
        }.onFailure {
            if (_devices.value.isEmpty()) stop()
            Timber.tag("Link").w(it, "pairing with ${peer.name} failed")
        }
    }

    // another phone typed the code this one shows: the key is worked out from its public half and ours, and ours goes back in the reply
    internal fun acceptPairing(body: JSONObject, remoteHost: String, code: String): Result<Accepted> {
        val id = body.optString("phoneId").ifBlank { return Result.failure(PairException(PairException.Reason.Failed)) }
        val otherPublic = body.optString("pub").ifBlank { return Result.failure(PairException(PairException.Reason.Outdated)) }
        val previous = _devices.value.firstOrNull { it.id == id }
        if (previous == null && _devices.value.size >= MaxDevices) return Result.failure(PairException(PairException.Reason.Full))
        val keyPair = LinkAuth.newKeyPair()
        val key = LinkAuth.sharedKey(keyPair, otherPublic, code, listOf(id, LinkIdentity.id(context)))
            ?: return Result.failure(PairException(PairException.Reason.Failed))
        val now = System.currentTimeMillis()
        val kind = DeviceKind.fromWire(body.optString("kind").ifBlank { null }, DeviceKind.Phone)
        val port = body.optInt("port", LinkWire.PhonePort)
        val device = (previous ?: PairedDevice(id = id, name = "", key = "", kind = kind, pairedAt = now, lastSeenAt = now)).copy(
            name = body.optString("phoneName").ifBlank { "Phone" },
            key = key,
            kind = kind,
            pairedAt = now,
            lastSeenAt = now,
            host = remoteHost,
            port = port,
        )
        change { list -> list.filterNot { it.id == id } + device }
        refresh()
        markLive(device, remoteHost, port, now)
        PhoneLinkService.start(context)
        return Result.success(Accepted(device, LinkAuth.publicText(keyPair)))
    }

    private fun credentials(key: String) = LinkCredentials(LinkIdentity.id(context), key)

    suspend fun tell(device: LiveDevice, path: String, body: JSONObject? = null): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${LinkWire.baseUrl(device.host, device.port)}$path"
            val response = if (body == null) LinkWire.get(url, credentials(device.key)) else LinkWire.postJson(url, credentials(device.key), body.toString())
            response.use { if (it.isSuccessful) JSONObject(it.body.string().ifBlank { "{}" }) else null }
        }.getOrNull()
    }

    fun unlink(id: String) {
        val live = liveDevice(id)
        forget(id)
        if (live != null && !live.kind.isTv) {
            scope.launch(Dispatchers.IO) {
                runCatching { LinkWire.postJson("${LinkWire.baseUrl(live.host, live.port)}/unlink", credentials(live.key), "{}").close() }
            }
        }
    }

    internal fun forget(id: String) {
        _live.update { it - id }
        change { list -> list.filterNot { it.id == id } }
        refresh()
        if (_devices.value.isEmpty()) PhoneLinkService.stop(context)
    }

    fun update(id: String, transform: (PairedDevice) -> PairedDevice) = change { list -> list.map { if (it.id == id) transform(it) else it } }

    internal fun markLive(device: PairedDevice, host: String, port: Int, now: Long = System.currentTimeMillis()) {
        val wasAround = liveDevice(device.id, now) != null
        val live = LiveDevice(device.id, device.name, device.kind, host, port, device.key, now)
        _live.update { it + (device.id to live) }
        if (!device.kind.isTv && (device.host != host || device.port != port)) update(device.id) { it.copy(host = host, port = port) }
        if (wasAround) return
        _arrivals.tryEmit(live)
        if (device.kind.isTv && notifySync) PhoneLinkNotices.connected(context, device.name)
    }

    fun checkPeersNow() = peers.poke()

    private fun change(transform: (List<PairedDevice>) -> List<PairedDevice>) {
        synchronized(lock) {
            val before = _devices.value
            val after = transform(before)
            if (after == before) return
            _devices.value = after
            PairedDeviceStore.write(prefs, after)
        }
    }

    private fun route(request: LinkRequest): LinkResponse {
        if (request.header(LinkWire.OldKeyHeader) != null) return LinkResponse.status(426, "update Sekai Tune on that device")
        val from = verifier.verify(request) { id -> _devices.value.firstOrNull { it.id == id }?.key }
        val device = from?.let { id -> _devices.value.firstOrNull { it.id == id } }
            ?: return LinkResponse.status(401, "not paired")
        seen(device)
        return when {
            request.path == "/hello" -> hello(request, device)
            request.path == "/avatar" -> LinkResponse.status(404)
            request.path == "/library" -> {
                if (!device.mirrorLibrary) return LinkResponse.status(403, "library sharing is off")
                val sent = libraryMark()
                val snapshot = library.snapshot()
                LinkResponse.stream(snapshot.contentType) { out ->
                    snapshot.writer(out)
                    synced(device, sent)
                }
            }
            request.path == "/files" -> LinkResponse.json(if (device.streamLocal) library.files().toString() else "[]")
            request.path.startsWith("/file/") -> {
                if (!device.streamLocal) return LinkResponse.status(403, "streaming is off")
                _lastStream.update { it + (device.id to System.currentTimeMillis()) }
                library.file(request, request.path.removePrefix("/file/"))
            }
            request.path == "/changes" && request.method == "POST" -> {
                if (device.mirrorLibrary) {
                    library.applyChanges(JSONObject(request.bodyText()))
                    revision.incrementAndGet()
                }
                LinkResponse.json("{}")
            }
            request.path == "/unlink" && request.method == "POST" -> {
                forget(device.id)
                LinkResponse.json("{}")
            }
            else -> controlled(request, device)
        }
    }

    private fun hello(request: LinkRequest, device: PairedDevice): LinkResponse {
        val port = (request.query["tvPort"] ?: request.query["port"])?.toIntOrNull()?.takeIf { it > 0 }
        if (port != null) markLive(device, request.remoteHost, port)
        val json = JSONObject()
            .put("id", LinkIdentity.id(context))
            .put("name", LinkIdentity.name(context))
            .put("kind", DeviceKind.of(context).wire)
            .put("user", "")
            .put("avatar", "")
            .put("protocol", LinkWire.ProtocolVersion)
            .put("dbVersion", MusicDatabaseVersion)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("rev", revision.get())
            .put("mirror", device.mirrorLibrary)
            .put("stream", device.streamLocal)
            .put("port", this.port)
        if (device.kind.isTv && device.mirrorLibrary) json.put("lib", libraryMark())
        return LinkResponse.json(json.toString())
    }

    private fun controlled(request: LinkRequest, device: PairedDevice): LinkResponse {
        if (device.kind.isTv) return LinkResponse.status(404)
        if (!device.remoteControl) return LinkResponse.status(403, "remote control is off on this phone")
        val connection = LinkPlayer.current() ?: return LinkResponse.status(409, "Sekai Tune isn't open on this phone")
        return control.handle(request, connection)
    }

    private fun libraryMark(): String {
        val now = revision.get()
        mark?.takeIf { it.first == now }?.let { return it.second }
        val fresh = runCatching { library.mark() }.getOrElse { now.toString() }
        mark = now to fresh
        return fresh
    }

    private fun watchLibrary() {
        if (watchingLibrary) return
        watchingLibrary = true
        database.invalidationTracker.addObserver(
            object : androidx.room.InvalidationTracker.Observer(
                arrayOf("song", "artist", "album", "playlist", "playlist_song_map", "event"),
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    revision.incrementAndGet()
                }
            },
        )
    }

    private fun synced(device: PairedDevice, library: String) {
        update(device.id) { it.copy(lastSyncAt = System.currentTimeMillis()) }
        if (toldLibrary.put(device.id, library) == library) return
        if (notifySync) PhoneLinkNotices.synced(context, device.name)
    }

    private fun seen(device: PairedDevice) {
        val now = System.currentTimeMillis()
        if (now - device.lastSeenAt < 60_000) return
        update(device.id) { it.copy(lastSeenAt = now) }
    }

    companion object {
        private const val StayKey = "stay_reachable"
        private const val PlayerMenuKey = "player_menu"
        private const val NotifyKey = "notify_sync"

        const val MusicDatabaseVersion = 37

        const val MaxDevices = 10
        internal const val FullReply = "full"
        const val LiveForMs = 70_000L

        fun hasPairedDevices(context: Context): Boolean =
            PairedDeviceStore.hasAny(context.getSharedPreferences(PairedDeviceStore.Prefs, Context.MODE_PRIVATE))

        fun staysReachable(context: Context): Boolean =
            context.getSharedPreferences(PairedDeviceStore.Prefs, Context.MODE_PRIVATE).getBoolean(StayKey, true)
    }
}

internal class Accepted(val device: PairedDevice, val publicKey: String)

class PairException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { WrongCode, Full, Failed, Outdated }
}

