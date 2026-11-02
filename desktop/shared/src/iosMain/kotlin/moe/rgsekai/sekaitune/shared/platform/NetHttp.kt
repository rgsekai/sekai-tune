package moe.rgsekai.sekaitune.shared.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

actual object NetHttp {
    private val client by lazy {
        HttpClient(Darwin) {
            expectSuccess = false
            followRedirects = true
            install(HttpTimeout)
        }
    }

    actual fun execute(request: HttpRequest): HttpResponse = try {
        runBlocking {
            val r = client.request(request.url) {
                method = HttpMethod.parse(request.method)
                request.headers.forEach { (k, v) -> header(k, v) }
                request.contentType?.let { contentType(ContentType.parse(it)) }
                request.body?.let { setBody(it) }
                    ?: if (request.method in setOf("POST", "PUT", "PATCH")) setBody(ByteArray(0)) else Unit
                timeout {
                    request.callTimeoutMs?.let { requestTimeoutMillis = it }
                    request.connectTimeoutMs?.let { connectTimeoutMillis = it }
                }
            }
            HttpResponse(r.status.value, r.headers.entries().associate { it.key to it.value }, r.bodyAsBytes(), r.call.request.url.toString())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw HttpException(e.message ?: "http failure", e)
    }
}
