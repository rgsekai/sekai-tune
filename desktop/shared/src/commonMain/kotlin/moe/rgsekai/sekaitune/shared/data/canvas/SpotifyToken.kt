package moe.rgsekai.sekaitune.shared.data.canvas

import moe.rgsekai.sekaitune.shared.data.DebugLog as Log
import moe.rgsekai.sekaitune.shared.data.settings.AppSettings
import moe.rgsekai.sekaitune.shared.platform.HttpRequest
import moe.rgsekai.sekaitune.shared.platform.NetHttp
import moe.rgsekai.sekaitune.shared.platform.currentTimeMillis
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * The bearer token behind Spotify's own web player, minted from the
 * listener's session cookie ([AppSettings.spotifySpdcToken]) rather than an
 * app credential — there is no public API for a track's Canvas, so this walks
 * the same door the web player itself uses to fetch one.
 *
 * The offscreen-web-player harvest itself is the one platform-specific part:
 * each host installs [harvester] (Android: a WebView, iOS: a WKWebView) and
 * [os]/[osVersion] for the client token, once at startup. Everything else —
 * caching, the client token, the session lookup — is shared.
 */
@OptIn(ExperimentalEncodingApi::class)
object SpotifyToken {

    /** What a harvest yields: the bearer, when it dies, and the client id the client token needs. */
    class Harvested(val token: String, val expiresAt: Long, val clientId: String?)

    /** Mints a logged-in web-player token for the `sp_dc` cookie, or null on failure/timeout. Never throws. */
    @Volatile var harvester: (suspend (cookie: String) -> Harvested?)? = null
    @Volatile var os = "unknown"
    @Volatile var osVersion = ""


    private const val TAG = "SpotifyToken"
    private const val DEFAULT_TOKEN_LIFETIME_MS = 3_600_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val harvestMutex = Mutex()

    private val guard = SynchronizedObject()

    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var accessTokenExpiresAtMs = 0L
    @Volatile private var cachedClientId: String? = null

    @Volatile private var cachedSession: SessionInfo? = null
    @Volatile private var cachedClientToken: String? = null
    @Volatile private var clientTokenExpiresAtMs = 0L

    private data class SessionInfo(val clientVersion: String, val deviceId: String)

    fun invalidate() {
        cachedAccessToken = null
        accessTokenExpiresAtMs = 0L
        cachedClientId = null
        cachedClientToken = null
        clientTokenExpiresAtMs = 0L
    }

    /**
     * The current bearer token, or null when there is no cookie to mint one
     * from, [init] was never called, or the harvest failed. Cached until
     * shortly before it expires so a skip through a queue doesn't pay for
     * this per track.
     *
     * Minted by loading the real web player in an offscreen WebView with the
     * listener's cookie applied and reading the token it mints for itself —
     * see [harvestViaWebView] for why signing the request ourselves isn't
     * enough.
     */
    suspend fun accessToken(): String? {
        val cookie = AppSettings.spotifySpdcToken.value
        if (cookie.isBlank()) return null

        val now = currentTimeMillis()
        cachedAccessToken?.let { if (now < accessTokenExpiresAtMs - 30_000) return it }

        return harvestMutex.withLock {
            val stillNow = currentTimeMillis()
            cachedAccessToken?.let { if (stillNow < accessTokenExpiresAtMs - 30_000) return@withLock it }

            val harvest = harvester
            if (harvest == null) {
                Log.w(TAG, "no token harvester installed on this platform")
                return@withLock null
            }

            Log.d(TAG, "harvesting web player token")
            val harvested = harvest(cookie)
            if (harvested == null) {
                Log.w(TAG, "token harvest failed or timed out")
                return@withLock null
            }

            cachedAccessToken = harvested.token
            accessTokenExpiresAtMs = harvested.expiresAt
            harvested.clientId?.let { cachedClientId = it }
            Log.d(TAG, "harvested access token, good until ${harvested.expiresAt}")
            harvested.token
        }
    }

    /**
     * Reads one `/api/token` response body the hooked page reported. Null for
     * anything unusable — and for the anonymous token the player also mints
     * before the cookie takes effect, which can't read canvases, so a harvester
     * keeps waiting for the logged-in one.
     */
    fun parseHarvest(payload: String?): Harvested? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(payload).jsonObject
            val token = root["accessToken"]?.jsonPrimitive?.contentOrNull
            val anonymous = root["isAnonymous"]?.jsonPrimitive?.contentOrNull
                ?.toBooleanStrictOrNull() ?: false
            if (token.isNullOrBlank() || anonymous) return null
            val expiresAt = root["accessTokenExpirationTimestampMs"]?.jsonPrimitive?.contentOrNull
                ?.toLongOrNull()?.takeIf { it > currentTimeMillis() }
                ?: (currentTimeMillis() + DEFAULT_TOKEN_LIFETIME_MS)
            Harvested(token, expiresAt, root["clientId"]?.jsonPrimitive?.contentOrNull)
        }.getOrNull()
    }

    // The web player's token comes from a real page load, not a signed request: Spotify retired the old
    // endpoint, and its replacement (`/api/token`) checks a TOTP whose forged tokens api.spotify.com and spclient
    // turn away with a 429 that reads like rate limiting. The platform harvesters load open.spotify.com offscreen
    // with the cookie applied and hook fetch/XHR before the player's own bundle runs, via [HOOK_SCRIPT].

    /** Injected by the platform harvesters; reports raw `/api/token` response bodies to `$BRIDGE_NAME`-style bridges. */
    fun hookScript(report: String): String = HOOK_SCRIPT.replace("__REPORT__", report)

    private val HOOK_SCRIPT = """
        (function () {
          if (window.__sekaiTokenHook) return;
          window.__sekaiTokenHook = true;
          var report = function (body) {
            try { __REPORT__(body); } catch (e) {}
          };
          var isToken = function (u) {
            try { return String(u).indexOf('/api/token') !== -1; } catch (e) { return false; }
          };
          var origFetch = window.fetch;
          if (origFetch) {
            window.fetch = function (input, init) {
              var url = (input && input.url) ? input.url : input;
              var result = origFetch.apply(this, arguments);
              if (isToken(url)) {
                try {
                  result.then(function (res) {
                    res.clone().text().then(report).catch(function () {});
                  }).catch(function () {});
                } catch (e) {}
              }
              return result;
            };
          }
          var origOpen = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function (method, url) {
            this.__sekaiUrl = url;
            return origOpen.apply(this, arguments);
          };
          var origSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function () {
            var xhr = this;
            try {
              xhr.addEventListener('load', function () {
                if (isToken(xhr.__sekaiUrl)) {
                  try { report(xhr.responseText); } catch (e) {}
                }
              });
            } catch (e) {}
            return origSend.apply(this, arguments);
          };
        })();
    """.trimIndent()

    /**
     * The second header these endpoints have started demanding alongside the
     * bearer token — api.spotify.com and spclient both turn away a request
     * that only carries [accessToken] with a 429. Best-effort: a caller with
     * no client token just sends the bearer alone and takes whatever the
     * endpoint does with that. Requires [accessToken] to have already
     * succeeded once, since the client id it needs comes off that response.
     */
    fun clientToken(): String? = synchronized(guard) {
        val now = currentTimeMillis()
        cachedClientToken?.let { if (now < clientTokenExpiresAtMs - 30_000) return@synchronized it }

        val clientId = cachedClientId
        if (clientId == null) {
            Log.w(TAG, "no client id yet (access token not minted); skipping client token")
            return@synchronized null
        }
        val session = session() ?: return@synchronized null

        val payload = buildJsonObject {
            putJsonObject("client_data") {
                put("client_version", session.clientVersion)
                put("client_id", clientId)
                putJsonObject("js_sdk_data") {
                    put("device_brand", "unknown")
                    put("device_model", "unknown")
                    put("os", os)
                    put("os_version", osVersion)
                    put("device_id", session.deviceId)
                    put("device_type", "smartphone")
                }
            }
        }

        // The ByteArray overload, deliberately: the String overload of
        // toRequestBody rewrites a charset-less MediaType to
        // "application/json; charset=utf-8", and clienttoken 400s on that
        // exact header rather than the bare "application/json" the real web
        // player sends.
        val request = HttpRequest(
            "https://clienttoken.spotify.com/v1/clienttoken", "POST",
            mapOf("Accept" to "application/json", "User-Agent" to CANVAS_UA),
            payload.toString().encodeToByteArray(),
            "application/json",
        )

        var lastCode = -1
        val body = runCatching {
            NetHttp.execute(request).let { response ->
                lastCode = response.code
                if (response.isSuccessful) response.text() else null
            }
        }.onFailure { Log.w(TAG, "client-token request threw: ${it.message}") }.getOrNull()
        if (body == null) {
            Log.w(TAG, "client-token request failed, http $lastCode")
            return@synchronized null
        }

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (root == null) {
            Log.w(TAG, "client-token response wasn't JSON")
            return@synchronized null
        }
        val responseType = root["response_type"]?.jsonPrimitive?.contentOrNull
        if (responseType != "RESPONSE_GRANTED_TOKEN_RESPONSE") {
            Log.w(TAG, "client-token request rejected: $responseType")
            return@synchronized null
        }
        val granted = root["granted_token"]?.jsonObject
        val token = granted?.get("token")?.jsonPrimitive?.contentOrNull
        if (token == null) {
            Log.w(TAG, "client-token response had no granted_token.token")
            return@synchronized null
        }
        val ttlSeconds = granted["expires_after_seconds"]?.jsonPrimitive?.contentOrNull
            ?.toLongOrNull() ?: 3600L

        cachedClientToken = token
        clientTokenExpiresAtMs = now + ttlSeconds * 1000
        Log.d(TAG, "minted client token, good for ${ttlSeconds}s")
        token
    }

    /**
     * [SessionInfo.clientVersion] and [SessionInfo.deviceId] both come off the
     * plain, unauthenticated web player page — the client version from a JSON
     * blob it embeds for itself, the device id from the `sp_t` cookie it hands
     * out to every visitor. Fetched once and held for the process: neither
     * changes inside a session.
     */
    private fun session(): SessionInfo? {
        cachedSession?.let { return it }

        val (html, deviceId) = runCatching {
            val response = NetHttp.execute(HttpRequest("https://open.spotify.com", headers = mapOf("User-Agent" to CANVAS_UA)))
            val spT = response.headers.entries.firstOrNull { it.key.equals("Set-Cookie", ignoreCase = true) }?.value
                ?.firstNotNullOfOrNull { header -> Regex("""sp_t=([^;]+)""").find(header)?.groupValues?.get(1) }
            response.text() to spT
        }.getOrNull() ?: (null to null)
        if (html == null) {
            Log.w(TAG, "couldn't load the web player page for session info")
            return null
        }

        val configB64 = Regex("""<script id="appServerConfig" type="text/plain">([^<]+)</script>""")
            .find(html)?.groupValues?.get(1)
        if (configB64 == null) {
            Log.w(TAG, "web player page had no appServerConfig block")
            return null
        }
        val clientVersion = runCatching {
            val configJson = Base64.decode(configB64).decodeToString()
            json.parseToJsonElement(configJson).jsonObject["clientVersion"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        if (clientVersion == null) {
            Log.w(TAG, "appServerConfig had no clientVersion")
            return null
        }

        val session = SessionInfo(clientVersion, deviceId ?: randomDeviceId())
        cachedSession = session
        return session
    }

    /** A random UUID-shaped id, for when the page hands out no `sp_t` cookie. */
    private fun randomDeviceId(): String =
        Random.nextBytes(16).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            .let { "${it.take(8)}-${it.substring(8, 12)}-${it.substring(12, 16)}-${it.substring(16, 20)}-${it.drop(20)}" }
}
