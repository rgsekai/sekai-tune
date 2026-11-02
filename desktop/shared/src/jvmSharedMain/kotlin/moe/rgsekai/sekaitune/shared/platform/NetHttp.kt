package moe.rgsekai.sekaitune.shared.platform

import moe.rgsekai.sekaitune.shared.data.Http
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

actual object NetHttp {
    private val derived = ConcurrentHashMap<Pair<Long?, Long?>, OkHttpClient>()

    private fun clientFor(call: Long?, connect: Long?): OkHttpClient =
        if (call == null && connect == null) Http.client
        else derived.getOrPut(call to connect) {
            Http.client.newBuilder().apply {
                call?.let { callTimeout(it, TimeUnit.MILLISECONDS) }
                connect?.let { connectTimeout(it, TimeUnit.MILLISECONDS) }
            }.build()
        }

    actual fun execute(request: HttpRequest): HttpResponse {
        try {
            val body = request.body ?: if (request.method in setOf("POST", "PUT", "PATCH")) ByteArray(0) else null
            val req = Request.Builder().url(request.url)
                .method(request.method, body?.toRequestBody(request.contentType?.toMediaTypeOrNull()))
                .apply { request.headers.forEach { (k, v) -> header(k, v) } }
                .build()
            clientFor(request.callTimeoutMs, request.connectTimeoutMs).newCall(req).execute().use { r ->
                return HttpResponse(r.code, r.headers.toMultimap(), r.body.bytes(), r.request.url.toString())
            }
        } catch (e: IOException) {
            throw HttpException(e.message ?: "http failure", e)
        } catch (e: IllegalArgumentException) {
            throw HttpException(e.message ?: "bad request", e)
        }
    }
}
