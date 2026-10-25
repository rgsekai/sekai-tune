/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.webdav

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Represents a single remote WebDAV resource (file or collection).
 */
data class WebDavEntry(
    val url: String,
    val displayName: String,
    val isCollection: Boolean,
    val contentType: String? = null,
    val contentLength: Long? = null,
    val lastModified: String? = null,
    val etag: String? = null,
)

/**
 * Result of scanning a remote library hierarchy.
 */
data class LibraryListing(
    val audio: List<WebDavEntry>,
    val images: List<WebDavEntry>,
)

/**
 * Granular connection test result.
 */
sealed interface WebDavTestResult {
    data object Success : WebDavTestResult
    data class AuthFailed(val code: Int) : WebDavTestResult
    data object NotFound : WebDavTestResult
    data class SslError(val message: String) : WebDavTestResult
    data object CleartextBlocked : WebDavTestResult
    data class NetworkError(val message: String) : WebDavTestResult
    data class Other(val code: Int, val message: String) : WebDavTestResult
}

/**
 * Pure OkHttp WebDAV client.
 *
 * Implements PROPFIND multi-status listing and connection verification
 * without third-party dependencies.
 */
object WebDavClient {

    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val PROPFIND_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()

    private val PROPFIND_XML_BODY: RequestBody = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:resourcetype/>
    <d:getcontenttype/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:getetag/>
  </d:prop>
</d:propfind>
""".trimIndent().toRequestBody(PROPFIND_MEDIA_TYPE)

    /**
     * Executes Depth: 0 PROPFIND to test server connectivity and credentials.
     * Returns typed result with granular error categorization.
     */
    fun testConnection(
        url: String,
        username: String,
        password: String? = null,
        trustSelfSignedCert: Boolean = false,
        baseClient: OkHttpClient = defaultClient,
    ): WebDavTestResult {
        val target = WebDavConfig.normalizeUrl(url)
        val address = WebDavConfig.splitAddress(target)
            ?: return WebDavTestResult.NetworkError("Invalid server URL")

        val client = if (trustSelfSignedCert) {
            WebDavAuth.update(target, username, password.orEmpty(), trustSelfSignedCert = true)
            WebDavAuth.createWebDavHttpClient(baseClient)
        } else {
            baseClient
        }

        val request = propfindRequest(
            url = target,
            depth = "0",
            username = username,
            password = password.orEmpty(),
        )

        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 204, 207 -> WebDavTestResult.Success
                    in 201..299 -> WebDavTestResult.Success
                    401, 403 -> WebDavTestResult.AuthFailed(response.code)
                    404 -> WebDavTestResult.NotFound
                    else -> WebDavTestResult.Other(response.code, response.message.ifBlank { "HTTP ${response.code}" })
                }
            }
        } catch (e: SSLException) {
            WebDavTestResult.SslError(e.localizedMessage ?: "SSL handshake failed. Try enabling 'Trust certificate'.")
        } catch (e: UnknownHostException) {
            WebDavTestResult.NetworkError(e.localizedMessage ?: "Hostname not found")
        } catch (e: ConnectException) {
            WebDavTestResult.NetworkError(e.localizedMessage ?: "Connection refused by host")
        } catch (e: SocketTimeoutException) {
            WebDavTestResult.NetworkError("Connection timed out")
        } catch (e: IOException) {
            val msg = e.localizedMessage.orEmpty()
            if (msg.contains("CLEARTEXT", ignoreCase = true) || msg.contains("Cleartext HTTP", ignoreCase = true)) {
                WebDavTestResult.CleartextBlocked
            } else {
                WebDavTestResult.NetworkError(msg.ifBlank { "I/O network failure" })
            }
        } catch (e: Exception) {
            WebDavTestResult.Other(-1, e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    /**
     * Walks the remote WebDAV collection with a breadth-first search (BFS) using Depth: 1.
     * Guaranteed never to request Depth: infinity.
     */
    fun walkLibrary(
        baseUrl: String,
        username: String,
        password: String,
        trustSelfSignedCert: Boolean = false,
        maxFiles: Int = WebDavConfig.DEFAULT_MAX_FILES,
        baseClient: OkHttpClient = defaultClient,
    ): Result<LibraryListing> = runCatching {
        val root = WebDavConfig.normalizeUrl(baseUrl)
        require(root.isNotBlank()) { "WebDAV URL is blank" }

        val client = if (trustSelfSignedCert) {
            WebDavAuth.update(root, username, password, trustSelfSignedCert = true)
            WebDavAuth.createWebDavHttpClient(baseClient)
        } else {
            baseClient
        }

        val foundAudio = LinkedHashMap<String, WebDavEntry>()
        val foundImages = LinkedHashMap<String, WebDavEntry>()
        val dirQueue = ArrayDeque<String>()
        val visitedDirs = HashSet<String>()

        dirQueue.add(root)

        while (dirQueue.isNotEmpty() && foundAudio.size < maxFiles) {
            val dir = dirQueue.removeFirst()
            val normalizedDir = dir.trimEnd('/').lowercase()
            if (!visitedDirs.add(normalizedDir)) continue

            val entries = listDirectory(dir, username, password, client).getOrThrow()
            for (entry in entries) {
                if (entry.isCollection) {
                    val normalizedSubDir = entry.url.trimEnd('/').lowercase()
                    if (!visitedDirs.contains(normalizedSubDir)) {
                        dirQueue.add(entry.url)
                    }
                } else {
                    val name = entry.displayName.ifBlank { entry.url }
                    when {
                        WebDavConfig.isAudioFile(name) -> foundAudio.putIfAbsent(entry.url, entry)
                        WebDavConfig.isImageFile(name) -> foundImages.putIfAbsent(entry.url, entry)
                    }
                }
            }
        }

        LibraryListing(
            audio = foundAudio.values.toList(),
            images = foundImages.values.toList(),
        )
    }

    /**
     * Single directory listing using Depth: 1.
     */
    fun listDirectory(
        dirUrl: String,
        username: String,
        password: String,
        client: OkHttpClient = defaultClient,
    ): Result<List<WebDavEntry>> = runCatching {
        val request = propfindRequest(dirUrl, depth = "1", username, password)
        client.newCall(request).execute().use { response ->
            if (response.code != 207 && response.code !in 200..299) {
                throw IOException("Listing failed with HTTP ${response.code}")
            }
            val body = response.body.string()
            if (body.isBlank()) return@runCatching emptyList()
            parseMultistatus(body, dirUrl)
        }
    }

    /**
     * Parses a PROPFIND multistatus XML response using platform DOM.
     * Filters out the directory itself so only child entries are returned.
     */
    fun parseMultistatus(xml: String, requestUrl: String): List<WebDavEntry> {
        if (xml.isBlank()) return emptyList()

        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
        }.getOrNull() ?: return emptyList()

        val responses = doc.getElementsByTagNameNS("*", "response")
        val result = ArrayList<WebDavEntry>(responses.length)

        for (i in 0 until responses.length) {
            val node = responses.item(i) as? Element ?: continue
            val hrefNode = node.getElementsByTagNameNS("*", "href").item(0) ?: continue
            val rawHref = hrefNode.textContent.trim()
            if (rawHref.isEmpty()) continue

            val resolvedUrl = resolveHref(requestUrl, rawHref)
            if (isSamePath(resolvedUrl, requestUrl)) {
                // Skip the self-referencing container element
                continue
            }

            val displayName = node.getElementsByTagNameNS("*", "displayname")
                .item(0)?.textContent?.trim().orEmpty()
                .ifBlank { decodeSegmentName(resolvedUrl) }

            val resourceType = node.getElementsByTagNameNS("*", "resourcetype").item(0) as? Element
            val isCollection = resourceType?.getElementsByTagNameNS("*", "collection")?.length?.let { it > 0 } == true

            val contentType = node.getElementsByTagNameNS("*", "getcontenttype")
                .item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }

            val contentLength = node.getElementsByTagNameNS("*", "getcontentlength")
                .item(0)?.textContent?.trim()?.toLongOrNull()

            val lastModified = node.getElementsByTagNameNS("*", "getlastmodified")
                .item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }

            val etag = node.getElementsByTagNameNS("*", "getetag")
                .item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }

            result.add(
                WebDavEntry(
                    url = resolvedUrl,
                    displayName = displayName,
                    isCollection = isCollection,
                    contentType = contentType,
                    contentLength = contentLength,
                    lastModified = lastModified,
                    etag = etag,
                ),
            )
        }

        return result
    }

    /**
     * Builds standard PROPFIND HTTP request.
     */
    fun propfindRequest(
        url: String,
        depth: String,
        username: String,
        password: String? = null,
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Depth", depth)
            .method("PROPFIND", PROPFIND_XML_BODY)

        WebDavConfig.basicAuthHeader(username, password.orEmpty())?.let {
            builder.header("Authorization", it)
        }

        return builder.build()
    }

    /**
     * Resolves an href (relative or absolute, encoded or plain) against the requested URL.
     */
    fun resolveHref(baseUrl: String, href: String): String {
        val trimmed = href.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }

        val base = WebDavConfig.normalizeUrl(baseUrl)
        val schemeEnd = base.indexOf("://")
        if (schemeEnd < 0) return trimmed

        val pathStart = base.indexOf('/', schemeEnd + 3)
        val schemeAndHost = if (pathStart < 0) base else base.substring(0, pathStart)

        return if (trimmed.startsWith("/")) {
            "$schemeAndHost$trimmed"
        } else {
            "${base.trimEnd('/')}/$trimmed"
        }
    }

    private fun isSamePath(urlA: String, urlB: String): Boolean {
        fun normalize(u: String) = u.trim().trimEnd('/').lowercase()
        return normalize(urlA) == normalize(urlB)
    }

    private fun decodeSegmentName(url: String): String = runCatching {
        val segment = url.trimEnd('/').substringAfterLast('/')
        URLDecoder.decode(segment, "UTF-8")
    }.getOrDefault(url.trimEnd('/').substringAfterLast('/'))
}
