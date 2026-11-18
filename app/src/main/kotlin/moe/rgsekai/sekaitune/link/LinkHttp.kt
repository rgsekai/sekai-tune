// LinkHttp.kt

package moe.rgsekai.sekaitune.link

import timber.log.Timber
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

// one request as the link server reads it: the path without its query, the query split up, headers in lowercase
class LinkRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray,
    val remoteHost: String,
) {
    fun header(name: String): String? = headers[name.lowercase()]

    fun bodyText(): String = body.toString(Charsets.UTF_8)
}

// what a route answers with: either a small body in memory or a writer that streams a big one
class LinkResponse private constructor(
    val status: Int,
    val contentType: String,
    val headers: Map<String, String>,
    val length: Long,
    val writer: (OutputStream) -> Unit,
) {
    companion object {
        fun bytes(bytes: ByteArray, contentType: String = "application/octet-stream", status: Int = 200) =
            LinkResponse(status, contentType, emptyMap(), bytes.size.toLong()) { it.write(bytes) }

        fun json(text: String, status: Int = 200) = bytes(text.toByteArray(Charsets.UTF_8), "application/json; charset=utf-8", status)

        fun status(status: Int, message: String = "") = bytes(message.toByteArray(Charsets.UTF_8), "text/plain; charset=utf-8", status)

        // a body whose size isn't known up front (a zipped database): it runs until the connection closes
        fun stream(contentType: String, writer: (OutputStream) -> Unit) = LinkResponse(200, contentType, emptyMap(), -1L, writer)

        // a file-like source with seeking, answered whole or as the byte range the player asked for
        fun ranged(
            request: LinkRequest,
            totalLength: Long,
            contentType: String,
            open: (start: Long, length: Long, out: OutputStream) -> Unit,
        ): LinkResponse {
            val range = parseRange(request.header("range"), totalLength)
                ?: return LinkResponse(200, contentType, mapOf("Accept-Ranges" to "bytes"), totalLength) { open(0, totalLength, it) }
            val (start, end) = range
            if (start >= totalLength || start > end) {
                return LinkResponse(416, contentType, mapOf("Content-Range" to "bytes */$totalLength"), 0) {}
            }
            val length = end - start + 1
            val headers = mapOf("Accept-Ranges" to "bytes", "Content-Range" to "bytes $start-$end/$totalLength")
            return LinkResponse(206, contentType, headers, length) { open(start, length, it) }
        }

        // "bytes=100-" or "bytes=100-199", the only kinds a media player sends
        private fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
            val trimmed = header?.trim() ?: return null
            if (!trimmed.startsWith("bytes=")) return null
            val first = trimmed.removePrefix("bytes=").substringBefore(',')
            val startText = first.substringBefore('-').trim()
            val endText = first.substringAfter('-', "").trim()
            if (startText.isEmpty()) {
                // "bytes=-500" is the last 500 bytes
                val suffix = endText.toLongOrNull() ?: return null
                return (total - suffix).coerceAtLeast(0) to total - 1
            }
            val start = startText.toLongOrNull() ?: return null
            val end = endText.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
            return start to end
        }
    }
}

// a very small http/1.1 server, one request per connection, enough for a phone and a tv on the same wi-fi to talk
class LinkHttpServer(
    private val name: String,
    private val handler: (LinkRequest) -> LinkResponse,
) {
    @Volatile private var socket: ServerSocket? = null
    private var workers: ExecutorService? = null

    val port: Int get() = socket?.localPort ?: -1
    val isRunning: Boolean get() = socket?.isClosed == false

    // binds the preferred port when it's free, otherwise any port; the other side finds it through nsd anyway
    @Synchronized
    fun start(preferredPort: Int): Int {
        socket?.takeIf { !it.isClosed }?.let { return it.localPort }
        val server = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(preferredPort)) } }
            .getOrElse { ServerSocket(0) }
        socket = server
        // a few at once covers a song streaming while the library syncs and the art loads
        val pool = Executors.newFixedThreadPool(MaxConnections) { runnable -> Thread(runnable, "$name-conn").apply { isDaemon = true } }
        workers = pool
        Thread({ acceptLoop(server, pool) }, "$name-accept").apply { isDaemon = true }.start()
        Timber.tag("Link").i("$name listening on ${server.localPort}")
        return server.localPort
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
        workers?.shutdownNow()
        workers = null
    }

    private fun acceptLoop(server: ServerSocket, pool: ExecutorService) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (_: SocketException) {
                break
            } catch (e: Exception) {
                Timber.tag("Link").w(e, "$name accept failed")
                continue
            }
            try {
                pool.execute { serve(client) }
            } catch (_: RejectedExecutionException) {
                runCatching { client.close() }
            }
        }
    }

    private fun serve(client: Socket) {
        client.use { connection ->
            runCatching {
                connection.soTimeout = ReadTimeoutMs
                val input = BufferedInputStream(connection.getInputStream())
                val request = readRequest(input, connection.inetAddress?.hostAddress.orEmpty()) ?: return
                val response = runCatching { handler(request) }.getOrElse {
                    Timber.tag("Link").w(it, "$name route ${request.path} failed")
                    LinkResponse.status(500, it.message.orEmpty())
                }
                val out = connection.getOutputStream().buffered(64 * 1024)
                writeHead(out, response)
                if (request.method != "HEAD") response.writer(out)
                out.flush()
            }.onFailure {
                // a player that seeks drops the old connection halfway through a song, nothing to report there
                if (it !is SocketException) Timber.tag("Link").d(it, "$name connection ended")
            }
        }
    }

    private fun readRequest(input: InputStream, remoteHost: String): LinkRequest? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MaxBodyBytes) ?: 0
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val got = input.read(body, read, length - read)
            if (got < 0) break
            read += got
        }
        val target = parts[1]
        val path = URLDecoder.decode(target.substringBefore('?'), "UTF-8")
        val query = LinkAuth.parseQuery(target.substringAfter('?', ""))
        return LinkRequest(parts[0].uppercase(), path, query, headers, body, remoteHost)
    }

    // header lines are plain ascii, read byte by byte so the body right after them stays in the stream
    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (line.isEmpty()) null else line.toString()
            if (byte == '\n'.code) return line.toString().trimEnd('\r')
            if (line.length > MaxHeaderLine) return null
            line.append(byte.toChar())
        }
    }

    private fun writeHead(out: OutputStream, response: LinkResponse) {
        val head = StringBuilder()
            .append("HTTP/1.1 ").append(response.status).append(' ').append(reason(response.status)).append("\r\n")
            .append("Content-Type: ").append(response.contentType).append("\r\n")
            .append("Connection: close\r\n")
        if (response.length >= 0) head.append("Content-Length: ").append(response.length).append("\r\n")
        response.headers.forEach { (name, value) -> head.append(name).append(": ").append(value).append("\r\n") }
        head.append("\r\n")
        out.write(head.toString().toByteArray(Charsets.US_ASCII))
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        206 -> "Partial Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        416 -> "Range Not Satisfiable"
        426 -> "Upgrade Required"
        else -> "Error"
    }

    private companion object {
        const val MaxConnections = 6
        const val ReadTimeoutMs = 15_000
        const val MaxBodyBytes = 8 * 1024 * 1024
        const val MaxHeaderLine = 16 * 1024
    }
}

