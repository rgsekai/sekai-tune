package moe.rgsekai.sekaitune.shared.platform

class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null,
    val callTimeoutMs: Long? = null,
    val connectTimeoutMs: Long? = null,
)

class HttpResponse(
    val code: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
    val finalUrl: String,
) {
    val isSuccessful get() = code in 200..299
    fun text(): String = body.decodeToString()
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

class HttpException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Blocking HTTP. Throws [HttpException] on transport failure; non-2xx is a normal response. */
expect object NetHttp {
    fun execute(request: HttpRequest): HttpResponse
}
