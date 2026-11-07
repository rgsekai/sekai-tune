/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

internal class DiscordGateway(
    private val token: String,
    private val onClosed: (code: Int) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val ready = CompletableDeferred<Unit>()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var sequence: Long? = null
    @Volatile private var heartbeatAcked = true
    private val closed = AtomicBoolean(false)
    private var heartbeat: Job? = null

    val isReady: Boolean get() = ready.isCompleted && !closed.get()

    suspend fun connect(): Boolean {
        val request = Request.Builder().url(GatewayUrl).build()
        socket = DiscordHttp.client.newWebSocket(request, Listener())
        val connected = try {
            withTimeoutOrNull(ReadyTimeoutMs) { ready.await() } != null
        } catch (e: IOException) {
            false
        }
        if (!connected) close(code = 4009)
        return connected
    }

    fun sendPresence(activity: JsonObject?): Boolean {
        if (!isReady) return false
        return send(
            op = OpPresenceUpdate,
            data = buildJsonObject {
                put("since", JsonNull)
                put("activities", buildJsonArray { if (activity != null) add(activity) })
                put("status", "online")
                put("afk", false)
            },
        )
    }

    fun close(code: Int = 1000) {
        if (!closed.compareAndSet(false, true)) return
        ready.completeExceptionally(IOException("Discord gateway closed with $code"))
        heartbeat?.cancel()
        socket?.close(1000, null)
        socket = null
        scope.cancel()
        onClosed(code)
    }

    private fun send(op: Int, data: JsonElement): Boolean {
        val frame = buildJsonObject {
            put("op", op)
            put("d", data)
        }
        return socket?.send(frame.toString()) == true
    }

    private fun onMessage(text: String) {
        val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        message["s"]?.jsonPrimitive?.let { if (it !is JsonNull) sequence = it.long }
        when (message["op"]?.jsonPrimitive?.intOrNull) {
            OpHello -> {
                val interval = message["d"]?.jsonObject?.get("heartbeat_interval")?.jsonPrimitive?.long ?: 41_250L
                startHeartbeat(interval)
                identify()
            }
            OpHeartbeatAck -> heartbeatAcked = true
            OpHeartbeat -> sendHeartbeat()
            OpReconnect, OpInvalidSession -> close(code = 4000)
            OpDispatch -> if (message["t"]?.jsonPrimitive?.contentOrNull == "READY") ready.complete(Unit)
        }
    }

    private fun identify() {
        send(
            op = OpIdentify,
            data = buildJsonObject {
                put("token", "Bearer $token")
                put("capabilities", Capabilities)
                put("intents", Intents)
                put("properties", buildJsonObject {
                    put("os", "Android")
                    put("browser", "SekaiTune")
                    put("device", "Android")
                })
            },
        )
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            delay((intervalMs * Random.nextDouble()).toLong())
            while (isActive) {
                if (!heartbeatAcked) {
                    close(code = 4009)
                    return@launch
                }
                sendHeartbeat()
                delay(intervalMs)
            }
        }
    }

    private fun sendHeartbeat() {
        heartbeatAcked = false
        send(OpHeartbeat, sequence?.let { JsonPrimitive(it) } ?: JsonNull)
    }

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) = this@DiscordGateway.onMessage(text)

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.tag(LogTag).d("gateway closing: $code $reason")
            close(code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.tag(LogTag).d(t, "gateway failed")
            response?.close()
            close(code = response?.code ?: 4000)
        }
    }

    companion object {
        private const val LogTag = "DiscordGateway"
        private const val GatewayUrl = "wss://gateway.discord.gg/?v=9&encoding=json"
        private const val ReadyTimeoutMs = 20_000L

        const val CloseAuthFailed = 4004

        private const val OpDispatch = 0
        private const val OpHeartbeat = 1
        private const val OpIdentify = 2
        private const val OpPresenceUpdate = 3
        private const val OpReconnect = 7
        private const val OpInvalidSession = 9
        private const val OpHello = 10
        private const val OpHeartbeatAck = 11

        private const val Capabilities = (1 shl 4) or (1 shl 5) or (1 shl 12) or (1 shl 16)
        private const val Intents = (1 shl 12) or (1 shl 18) or (1 shl 19) or (1 shl 22) or
            (1 shl 27) or (1 shl 28) or (1 shl 29)
    }
}

