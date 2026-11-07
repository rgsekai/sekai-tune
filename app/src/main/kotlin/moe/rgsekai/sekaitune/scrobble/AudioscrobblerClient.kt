/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.scrobble

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.Locale

class AudioscrobblerClient(
    private val apiRoot: String,
    private val apiKey: String,
    private val apiSecret: String,
    private val useTokenAuth: Boolean = false,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && apiSecret.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
    }

    suspend fun authenticate(username: String, password: String): ScrobbleSession = withContext(Dispatchers.IO) {
        val authParams = if (useTokenAuth) {
            val token = md5(username.lowercase(Locale.ROOT) + md5(password))
            mapOf("authToken" to token)
        } else {
            mapOf("password" to password)
        }
        val params = mapOf("username" to username) + authParams
        val response = callMethod("auth.getMobileSession", params)
        val sessionObj = response["session"] as? JsonObject
            ?: throw ScrobblerApiException(-1, "Malformed session response from server")
        val name = sessionObj["name"]?.jsonPrimitive?.content
            ?: throw ScrobblerApiException(-1, "Missing username in session response")
        val key = sessionObj["key"]?.jsonPrimitive?.content
            ?: throw ScrobblerApiException(-1, "Missing session key in response")
        ScrobbleSession(username = name, sessionKey = key)
    }

    suspend fun updateNowPlaying(sessionKey: String, item: ScrobbleItem): Unit = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "sk" to sessionKey,
            "artist" to item.artist,
            "track" to item.title,
        )
        item.album?.takeIf { it.isNotBlank() }?.let { params["album"] = it }
        item.durationSeconds?.takeIf { it > 0 }?.let { params["duration"] = it.toString() }
        callMethod("track.updateNowPlaying", params)
    }

    suspend fun scrobble(sessionKey: String, item: ScrobbleItem): Unit = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "sk" to sessionKey,
            "artist" to item.artist,
            "track" to item.title,
            "timestamp" to item.timestamp.toString(),
        )
        item.album?.takeIf { it.isNotBlank() }?.let { params["album"] = it }
        item.durationSeconds?.takeIf { it > 0 }?.let { params["duration"] = it.toString() }
        callMethod("track.scrobble", params)
    }

    suspend fun love(sessionKey: String, artist: String, track: String, love: Boolean): Unit = withContext(Dispatchers.IO) {
        val method = if (love) "track.love" else "track.unlove"
        val params = mapOf(
            "sk" to sessionKey,
            "artist" to artist,
            "track" to track,
        )
        callMethod(method, params)
    }

    private suspend fun callMethod(method: String, values: Map<String, String>): JsonObject {
        val params = HashMap(values)
        params["method"] = method
        params["api_key"] = apiKey
        params["api_sig"] = generateSignature(params)
        params["format"] = "json"

        val response = httpClient.post(apiRoot) {
            setBody(
                FormDataContent(
                    Parameters.build {
                        params.forEach { (k, v) -> append(k, v) }
                    }
                )
            )
        }

        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess() && bodyText.isBlank()) {
            throw ScrobblerApiException(response.status.value, "HTTP ${response.status.value}")
        }

        val jsonElement = runCatching { json.parseToJsonElement(bodyText) }.getOrNull()
        val jsonObject = jsonElement as? JsonObject
            ?: throw ScrobblerApiException(-1, "Non-JSON response: ${bodyText.take(150)}")

        if (jsonObject.containsKey("error")) {
            val errorCode = jsonObject["error"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
            val message = jsonObject["message"]?.jsonPrimitive?.content ?: "Scrobbler error $errorCode"
            throw ScrobblerApiException(errorCode, message)
        }

        return jsonObject
    }

    private fun generateSignature(params: Map<String, String>): String {
        val sortedKeys = params.keys.filter { it != "format" && it != "callback" }.sorted()
        val buffer = StringBuilder()
        for (key in sortedKeys) {
            buffer.append(key)
            buffer.append(params[key].orEmpty())
        }
        buffer.append(apiSecret)
        return md5(buffer.toString())
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

