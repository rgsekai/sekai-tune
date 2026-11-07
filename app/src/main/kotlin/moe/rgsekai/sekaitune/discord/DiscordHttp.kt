/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object DiscordHttp {
    const val Api = "https://discord.com/api/v10"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    class Reply(val code: Int, val body: String, val retryAfterSeconds: Double?) {
        val ok: Boolean get() = code in 200..299
    }

    suspend fun get(path: String, token: String): Reply = send(
        Request.Builder().url(Api + path).header("Authorization", "Bearer $token").build(),
    )

    suspend fun postForm(path: String, fields: Map<String, String>): Reply {
        val body = FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
        return send(Request.Builder().url(Api + path).post(body).build())
    }

    suspend fun postJson(path: String, token: String, json: String): Reply = send(
        Request.Builder()
            .url(Api + path)
            .header("Authorization", "Bearer $token")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build(),
    )

    private suspend fun send(request: Request): Reply = client.newCall(request).await().use { response ->
        Reply(
            code = response.code,
            body = response.body?.string().orEmpty(),
            retryAfterSeconds = response.header("Retry-After")?.toDoubleOrNull(),
        )
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) =
                continuation.resume(response) { _, unused, _ -> unused.close() }
        })
    }
}

