package moe.rgsekai.sekaitune.shared.data.presence

import moe.rgsekai.sekaitune.shared.data.DebugLog
import moe.rgsekai.sekaitune.shared.platform.HttpRequest
import moe.rgsekai.sekaitune.shared.platform.NetHttp
import kotlinx.coroutines.CoroutineScope
import moe.rgsekai.sekaitune.shared.platform.ioDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.random.Random

enum class PresencePlatform(val wire: String) { Android("android"), Pc("pc") }

/**
 * Tells the Sekai Tune server this install is open, so it can publish how many
 * apps are open right now (`GET /api/stats/live`).
 *
 * While open, one small POST goes out every few minutes. The server sets the
 * interval in each reply, so it can slow every client down without an update.
 * Closing sends one more POST so the count drops straight away; if that one is
 * lost, the server forgets the install a minute or two after its last ping.
 *
 * Only a random per-install UUID and the platform are sent, never anything
 * about the listener or what they play. This always goes to the official
 * server, not a custom Listen Together one, so the public count is only ours.
 */
class Presence(
    private val platform: PresencePlatform,
    private val installId: String,
    private val endpoint: String = ENDPOINT,
) {
    private val open = MutableStateFlow(false)
    @Volatile private var announced = false
    @Volatile private var intervalSec = DEFAULT_INTERVAL_SEC

    init {
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            // collectLatest: a change of state cancels whatever the last one was
            // still doing, so a close cancels the ping loop and a quick reopen
            // cancels a pending close.
            open.collectLatest { isOpen ->
                if (isOpen) {
                    while (true) {
                        send(open = true)
                        delay(nextDelayMs())
                    }
                } else if (announced) {
                    // A window flicker or an activity restart is not a close.
                    delay(CLOSE_DEBOUNCE_MS)
                    send(open = false)
                }
            }
        }
    }

    fun setOpen(isOpen: Boolean) {
        open.value = isOpen
    }

    /**
     * For process exit, where nothing launched would get to run: sends the close
     * on the calling thread, giving up after two seconds.
     */
    fun closeBlocking() {
        open.value = false
        if (announced) send(open = false, timeoutSec = 2)
    }

    private fun nextDelayMs(): Long {
        val base = intervalSec.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC) * 1000L
        // ±10%, so a release that starts thousands of apps at once spreads out.
        return base + Random.nextLong(-base / 10, base / 10 + 1)
    }

    private fun send(open: Boolean, timeoutSec: Long = 15) {
        val body = buildJsonObject {
            put("id", installId)
            put("platform", platform.wire)
            put("open", open)
        }.toString()
        // Set before sending: a ping that times out may still have been counted,
        // so it still needs a close.
        if (open) announced = true
        try {
            val response = NetHttp.execute(
                HttpRequest(
                    url = endpoint,
                    method = "POST",
                    body = body.encodeToByteArray(),
                    contentType = "application/json",
                    callTimeoutMs = timeoutSec * 1000,
                ),
            )
            if (!open) announced = false
            if (!response.isSuccessful) {
                DebugLog.w(TAG, "presence ping refused: HTTP ${response.code}")
                return
            }
            runCatching { Json.parseToJsonElement(response.text()).jsonObject["intervalSec"]?.jsonPrimitive?.intOrNull }
                .getOrNull()
                ?.let { intervalSec = it }
        } catch (e: Exception) {
            // Offline, or the server is down. The next ping is soon enough.
            DebugLog.d(TAG, "presence ping failed: ${e.message}")
        }
    }

    companion object {
        const val ENDPOINT = ""
        private const val TAG = "Presence"
        private const val DEFAULT_INTERVAL_SEC = 300
        private const val MIN_INTERVAL_SEC = 60
        private const val MAX_INTERVAL_SEC = 3600
        private const val CLOSE_DEBOUNCE_MS = 2_000L
    }
}
