/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rgsekai.sekaitune.utils.dataStore
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class PresenceConnection { Off, Idle, Connecting, Live, Retrying }

@OptIn(FlowPreview::class)
@Singleton
class DiscordPresence @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: DiscordAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    val settings: StateFlow<PresenceSettings> = context.dataStore.data
        .map(PresenceSettings::from)
        .stateIn(scope, SharingStarted.Eagerly, PresenceSettings())

    private val _connection = MutableStateFlow(PresenceConnection.Off)
    val connection: StateFlow<PresenceConnection> = _connection.asStateFlow()

    private val nudges = MutableStateFlow(0)
    private val pendingSave = MutableStateFlow<PresenceSettings?>(null)

    private val gatewayLock = Mutex()
    private var gateway: DiscordGateway? = null
    @Volatile private var connectFailures = 0
    @Volatile private var nextConnectAt = 0L

    @Volatile private var lastSent: Sent? = null
    private var lastPictureRetryAt = 0L
    private val recentSends = ArrayDeque<Long>()

    private val assetPaths = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 256
    }

    private val refusedUrls = ConcurrentHashMap.newKeySet<String>()
    private val spinning = SpinningArtwork(scope, onReady = ::nudge)

    private data class Sent(val activity: PresenceActivity, val largeAsset: String?, val smallAsset: String?)

    init {
        scope.launch { auth.forgetOldLogin() }
        scope.launch { follow() }
        scope.launch {
            pendingSave.filterNotNull().collect { edited -> context.dataStore.edit { edited.writeTo(it) } }
        }
    }

    fun save(edited: PresenceSettings) {
        pendingSave.value = edited
    }

    fun update(track: NowPlaying?) {
        _nowPlaying.value = track
    }

    fun nudge() {
        nudges.update { it + 1 }
    }

    private suspend fun follow() {
        combine(auth.signedIn, settings.debounce(SettingsSettleMs), _nowPlaying, nudges) { signedIn, settings, track, _ ->
            Triple(signedIn, settings, track)
        }.collectLatest { (signedIn, settings, track) ->
            when {
                !auth.isConfigured || !signedIn || !settings.enabled -> {
                    hide()
                    _connection.value = PresenceConnection.Off
                }
                track == null || (!track.isPlaying && settings.clearOnPause) -> {
                    hide()
                    _connection.value = PresenceConnection.Idle
                }
                else -> show(track, settings)
            }
        }
    }

    private suspend fun show(track: NowPlaying, settings: PresenceSettings) {
        val pausedAt = if (track.isPlaying) null else track.readAtMs
        while (true) {
            val now = System.currentTimeMillis()
            if (pausedAt != null && now - pausedAt > KeepPausedMs) {
                hide()
                _connection.value = PresenceConnection.Idle
                return
            }
            send(PresenceActivity.build(track, settings, now), settings.refreshSeconds)
            delay(settings.refreshSeconds * 1000L)
        }
    }

    private suspend fun send(activity: PresenceActivity, refreshSeconds: Int) {
        if (!worthSending(activity, lastSent)) return

        waitForTurn(refreshSeconds)
        val live = connect() ?: return
        val token = auth.accessToken() ?: return
        val (large, small) = assetsFor(activity, token)
        val payload = activity.toJson(DiscordAuth.ClientId, large, small)
        if (live.sendPresence(payload)) {
            lastSent = Sent(activity, large, small)
            recentSends.addLast(System.currentTimeMillis())
            _connection.value = PresenceConnection.Live
        }
    }

    private fun worthSending(activity: PresenceActivity, previous: Sent?): Boolean {
        if (previous == null || !activity.looksLike(previous.activity)) return true
        if (picturesChanged(activity, previous)) return true
        val missing = (activity.largeImage != null && previous.largeAsset == null) ||
            (activity.smallImage != null && previous.smallAsset == null)
        val now = System.currentTimeMillis()
        if (missing && now - lastPictureRetryAt > PictureRetryMs) {
            lastPictureRetryAt = now
            refusedUrls.clear()
            return true
        }
        return false
    }

    private fun picturesChanged(activity: PresenceActivity, previous: Sent): Boolean {
        fun spinningReady(picture: PresencePicture?, sentAsset: String?): Boolean {
            if (picture !is PresencePicture.Spinning) return false
            val url = spinning.urlFor(picture.sourceUrl, picture.style) ?: return false
            if (url in refusedUrls) return false
            return synchronized(assetPaths) { assetPaths[url] } != sentAsset
        }
        return spinningReady(activity.largeImage, previous.largeAsset) ||
            spinningReady(activity.smallImage, previous.smallAsset)
    }

    private suspend fun waitForTurn(refreshSeconds: Int) {
        val now = System.currentTimeMillis()
        val sinceLast = recentSends.lastOrNull()?.let { now - it } ?: Long.MAX_VALUE
        val intervalWait = refreshSeconds * 1000L - sinceLast
        while (recentSends.isNotEmpty() && now - recentSends.first() > BurstWindowMs) recentSends.removeFirst()
        val burstWait = if (recentSends.size >= BurstLimit) recentSends.first() + BurstWindowMs - now else 0L
        val wait = maxOf(intervalWait, burstWait)
        if (wait > 0) delay(wait)
    }

    private suspend fun connect(): DiscordGateway? = gatewayLock.withLock {
        gateway?.takeIf { it.isReady }?.let { return@withLock it }
        if (System.currentTimeMillis() < nextConnectAt) return@withLock null
        val token = auth.accessToken() ?: return@withLock null
        _connection.value = if (connectFailures == 0) PresenceConnection.Connecting else PresenceConnection.Retrying

        var opened: DiscordGateway? = null
        val fresh = DiscordGateway(token) { code -> opened?.let { onGatewayClosed(it, code, token) } }
        opened = fresh
        val ready = try {
            fresh.connect()
        } catch (e: CancellationException) {
            fresh.close()
            throw e
        }
        if (ready) {
            gateway = fresh
            connectFailures = 0
            nextConnectAt = 0
            fresh
        } else {
            connectFailures++
            nextConnectAt = System.currentTimeMillis() + backoffMs()
            _connection.value = PresenceConnection.Retrying
            scope.launch {
                delay(backoffMs())
                nudge()
            }
            null
        }
    }

    private fun onGatewayClosed(closed: DiscordGateway, code: Int, token: String) {
        scope.launch {
            val wasCurrent = gatewayLock.withLock {
                (gateway === closed).also { if (it) gateway = null }
            }
            if (!wasCurrent) return@launch
            lastSent = null
            Timber.tag(LogTag).d("gateway closed with $code, connecting again")
            if (code == DiscordGateway.CloseAuthFailed) auth.renew(token)
            connectFailures++
            nextConnectAt = System.currentTimeMillis() + backoffMs()
            _connection.value = PresenceConnection.Retrying
            delay(backoffMs())
            nudge()
        }
    }

    private suspend fun hide() {
        lastSent = null
        val current = gatewayLock.withLock { gateway.also { gateway = null } } ?: return
        current.sendPresence(null)
        current.close()
    }

    private fun backoffMs(): Long = Backoff[(connectFailures - 1).coerceIn(0, Backoff.lastIndex)]

    private suspend fun assetsFor(activity: PresenceActivity, token: String): Pair<String?, String?> {
        val large = candidatesFor(activity.largeImage)
        val small = candidatesFor(activity.smallImage)
        val missing = (large + small).distinct()
            .filter { it !in refusedUrls && synchronized(assetPaths) { it !in assetPaths } }
        if (missing.isNotEmpty()) register(missing, token)
        return synchronized(assetPaths) {
            large.firstNotNullOfOrNull { assetPaths[it] } to small.firstNotNullOfOrNull { assetPaths[it] }
        }
    }

    private fun candidatesFor(picture: PresencePicture?): List<String> = when (picture) {
        null -> emptyList()
        is PresencePicture.Web -> listOf(picture.url)
        is PresencePicture.Status -> listOf(PresenceImages.statusUrl(picture))
        PresencePicture.Logo -> listOf(PresenceImages.LogoUrl)
        is PresencePicture.Spinning -> listOfNotNull(spinning.urlFor(picture.sourceUrl, picture.style), picture.sourceUrl)
    }

    private suspend fun register(urls: List<String>, token: String, renewed: Boolean = false) {
        val body = buildJsonObject { put("urls", buildJsonArray { urls.forEach { add(JsonPrimitive(it)) } }) }
        val reply = try {
            DiscordHttp.postJson("/applications/${DiscordAuth.ClientId}/external-assets", token, body.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(LogTag).d(e, "couldn't register pictures")
            return
        }
        if (reply.code == 401 && !renewed) {
            auth.renew(token)?.let { register(urls, it, renewed = true) }
            return
        }
        if (!reply.ok) {
            Timber.tag(LogTag).d("pictures turned down: HTTP ${reply.code} ${reply.body.take(200)}")
            return
        }
        val registered = runCatching { json.parseToJsonElement(reply.body).jsonArray }.getOrNull() ?: return
        synchronized(assetPaths) {
            urls.forEachIndexed { index, url ->
                val path = registered.getOrNull(index)?.jsonObject?.get("external_asset_path")?.jsonPrimitive?.contentOrNull
                if (path != null) assetPaths[url] = "mp:$path" else refusedUrls.add(url)
            }
        }
    }

    companion object {
        private const val LogTag = "DiscordPresence"

        private const val SettingsSettleMs = 600L
        private const val KeepPausedMs = 10 * 60_000L
        private const val PictureRetryMs = 60_000L
        private const val BurstLimit = 5
        private const val BurstWindowMs = 20_000L
        private val Backoff = longArrayOf(2_000, 5_000, 15_000, 30_000, 60_000)
    }
}

