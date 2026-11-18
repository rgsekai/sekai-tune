// LinkWire.kt

package moe.rgsekai.sekaitune.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.net.URLDecoder
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.TimeUnit

// the names both sides agree on, so a phone and a tv on different app versions still find each other
object LinkWire {
    // a phone keeps this one up while it has any device paired
    const val PhoneServiceType = "_sekaitunelink._tcp."

    // a device only shows up as this while its pairing screen is open, the name is from when only tvs had one
    const val PairServiceType = "_sekaitunetv._tcp."

    const val PhonePort = 47_801
    const val PairPort = 47_802

    // the tv listens here for its phone's play, skip and look commands while a linked profile is open
    const val TvControlPort = 47_803

    // bumped when a route changes shape, so an old phone and a new tv can say so instead of failing quietly; 2 signs every call and pairs without sending the key
    const val ProtocolVersion = 2

    // the header the old protocol carried the key itself in; only looked for, so an un-updated device hears "update" instead of "unpaired"
    const val OldKeyHeader = "X-SekaiTune-Key"

    const val AttrId = "id"
    const val AttrName = "n"
    const val AttrKind = "k"

    // a lan is fast and a phone that's gone should be noticed quickly, so short waits
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    // a big library takes the phone a while to copy and squeeze before the first byte goes out
    private val slowClient: OkHttpClient by lazy {
        client.newBuilder().readTimeout(3, TimeUnit.MINUTES).build()
    }

    fun getSlow(url: String, auth: LinkCredentials?): Response = slowClient.newCall(request(url, auth, null)).execute()

    fun baseUrl(host: String, port: Int) = "http://${if (host.contains(':')) "[$host]" else host}:$port"

    fun get(url: String, auth: LinkCredentials?): Response = client.newCall(request(url, auth, null)).execute()

    fun postJson(url: String, auth: LinkCredentials?, json: String): Response = client.newCall(request(url, auth, json)).execute()

    // a call signed with the key when there is one; the path and query are read back the way the server reads them, so both sign the same thing
    private fun request(url: String, auth: LinkCredentials?, json: String?): Request {
        val body = json?.toByteArray(Charsets.UTF_8)
        val builder = Request.Builder().url(url)
        if (auth != null) {
            val parsed = url.toHttpUrl()
            val path = URLDecoder.decode(parsed.encodedPath, "UTF-8")
            builder.header(LinkAuth.Header, LinkAuth.header(auth, if (body == null) "GET" else "POST", path, LinkAuth.parseQuery(parsed.encodedQuery), body))
        }
        if (body != null) builder.post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        return builder.build()
    }
}

// this device as the other side sees it: a stable id that survives renames, and a name people recognise
object LinkIdentity {
    private const val Prefs = "sekaitune_link"
    private const val IdKey = "device_id"

    fun id(context: Context): String {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        prefs.getString(IdKey, null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit { putString(IdKey, fresh) }
        return fresh
    }

    // the name set in the phone's own settings when there is one ("Kei's Pixel"), the model otherwise
    fun name(context: Context): String {
        val named = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        return named?.takeIf { it.isNotBlank() } ?: Build.MODEL.orEmpty().ifBlank { "Android" }
    }
}

// a device found on the wi-fi, with where to reach it; kind is a tv when the device didn't say
data class LinkPeer(val id: String, val name: String, val host: String, val port: Int, val kind: DeviceKind = DeviceKind.Tv)

// puts this device on the wi-fi under a service type until close is called
class LinkAdvert(context: Context, type: String, name: String, port: Int, attributes: Map<String, String>) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) = Unit
        override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {
            Timber.tag("Link").w("couldn't advertise $type ($error)")
        }
        override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
        override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
    }
    private var registered = false

    init {
        val info = NsdServiceInfo().apply {
            serviceName = name.take(60)
            serviceType = type
            setPort(port)
            attributes.forEach { (key, value) -> setAttribute(key, value) }
        }
        registered = runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }.isSuccess
    }

    fun close() {
        if (!registered) return
        registered = false
        runCatching { nsd.unregisterService(listener) }
    }
}

// everything of one service type on the wi-fi, as a list that updates while it's collected
fun discoverPeers(context: Context, type: String): Flow<List<LinkPeer>> = callbackFlow {
    val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    val found = LinkedHashMap<String, LinkPeer>()
    // nsd only resolves one service at a time on older androids, so they wait their turn
    val waiting = ArrayDeque<NsdServiceInfo>()
    val resolving = java.util.concurrent.atomic.AtomicBoolean(false)

    fun publish() {
        trySend(found.values.toList())
    }

    fun resolveNext() {
        if (!resolving.compareAndSet(false, true)) return
        val next = synchronized(waiting) { waiting.pollFirst() } ?: run { resolving.set(false); return }
        @Suppress("DEPRECATION")
        val started = runCatching {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                    resolving.set(false)
                    resolveNext()
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = hostOf(info)
                    val attrs = info.attributes.orEmpty()
                    val id = attrs[LinkWire.AttrId]?.toString(Charsets.UTF_8)
                    if (host != null && id != null) {
                        val name = attrs[LinkWire.AttrName]?.toString(Charsets.UTF_8) ?: info.serviceName
                        val kind = DeviceKind.fromWire(attrs[LinkWire.AttrKind]?.toString(Charsets.UTF_8), DeviceKind.Tv)
                        found[info.serviceName] = LinkPeer(id, name, host, info.port, kind)
                        publish()
                    }
                    resolving.set(false)
                    resolveNext()
                }
            })
        }.isSuccess
        if (!started) resolving.set(false)
    }

    val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Timber.tag("Link").w("discovery of $type failed ($errorCode)")
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

        override fun onServiceFound(info: NsdServiceInfo) {
            synchronized(waiting) { waiting.addLast(info) }
            resolveNext()
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            if (found.remove(info.serviceName) != null) publish()
        }
    }

    publish()
    val started = runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }.isSuccess
    awaitClose { if (started) runCatching { nsd.stopServiceDiscovery(listener) } }
}

private fun hostOf(info: NsdServiceInfo): String? {
    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        info.hostAddresses.firstOrNull { it is java.net.Inet4Address } ?: info.hostAddresses.firstOrNull()
    } else {
        @Suppress("DEPRECATION")
        info.host
    }
    return address?.hostAddress?.substringBefore('%')
}

// a tv runs the big-screen app and the phone side of the link never starts there
fun Context.isTelevision(): Boolean {
    val modeType = (getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager)?.currentModeType
    return modeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
}

