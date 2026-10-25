/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.webdav

import java.net.URI
import java.util.Base64
import java.util.Locale

/**
 * Configuration, URL normalization, and basic utilities for WebDAV sources.
 */
object WebDavConfig {

    const val DEFAULT_MAX_FILES: Int = 10_000

    val audioExtensions: Set<String> = setOf(
        "mp3", "m4a", "flac", "ogg", "opus", "aac", "wav", "wma", "aif", "aiff", "webm",
    )

    val imageExtensions: Set<String> = setOf(
        "jpg", "jpeg", "png", "webp",
    )

    data class ParsedUrlCredentials(
        val cleanUrl: String,
        val username: String?,
        val password: String?,
    )

    /**
     * Strips credentials from a URL, returning only the clean scheme, authority, and path.
     */
    fun cleanUrl(raw: String): String = extractCredentialsFromUrl(raw).cleanUrl

    /**
     * Extracts and strips embedded user credentials (user:pass@) from a URL.
     * Decodes percent-encoded characters in username and password.
     */
    fun extractCredentialsFromUrl(raw: String): ParsedUrlCredentials {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParsedUrlCredentials("", null, null)

        val schemeEnd = trimmed.indexOf("://")
        val schemePart: String
        val rest: String

        if (schemeEnd >= 0) {
            schemePart = trimmed.substring(0, schemeEnd + 3)
            rest = trimmed.substring(schemeEnd + 3)
        } else {
            schemePart = ""
            rest = trimmed
        }

        val firstSlash = rest.indexOf('/')
        val authority = if (firstSlash >= 0) rest.substring(0, firstSlash) else rest
        val pathAndQuery = if (firstSlash >= 0) rest.substring(firstSlash) else ""

        val atIndex = authority.lastIndexOf('@')
        if (atIndex < 0) {
            return ParsedUrlCredentials(trimmed, null, null)
        }

        val userInfo = authority.substring(0, atIndex)
        val cleanAuthority = authority.substring(atIndex + 1)
        val cleanUrl = "$schemePart$cleanAuthority$pathAndQuery"

        val colonIndex = userInfo.indexOf(':')
        val username = if (colonIndex >= 0) {
            userInfo.substring(0, colonIndex)
        } else {
            userInfo
        }
        val password = if (colonIndex >= 0) {
            userInfo.substring(colonIndex + 1)
        } else {
            null
        }

        val decodedUser = try {
            java.net.URLDecoder.decode(username, "UTF-8").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            username.takeIf { it.isNotBlank() }
        }

        val decodedPass = try {
            password?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        } catch (_: Exception) {
            password
        }

        return ParsedUrlCredentials(cleanUrl, decodedUser, decodedPass)
    }

    /**
     * Normalizes WebDAV URL:
     * - Strips any embedded credentials (user:pass@)
     * - Trims leading/trailing whitespace
     * - Keeps http:// if explicitly typed
     * - Adds https:// if no scheme is provided
     * - Strips trailing duplicate slashes
     */
    fun normalizeUrl(raw: String): String {
        val extracted = extractCredentialsFromUrl(raw)
        val trimmed = extracted.cleanUrl.trim()
        if (trimmed.isEmpty()) return ""

        val withScheme = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val schemeEnd = withScheme.indexOf("://")
        if (schemeEnd < 0) return withScheme.trimEnd('/')

        val scheme = withScheme.substring(0, schemeEnd).lowercase(Locale.ROOT)
        var path = withScheme.substring(schemeEnd + 3)

        // Strip trailing duplicate slashes
        while (path.endsWith("/")) {
            path = path.dropLast(1)
        }

        return "$scheme://$path"
    }

    /**
     * Redacts any sensitive data (Authorization headers, userinfo in URLs, passwords)
     * from logs and error messages.
     */
    fun sanitizeForLogging(input: String): String {
        return input
            .replace(Regex("""(?i)(authorization\s*[:=]\s*)(basic|bearer)\s+[^\s\r\n,;]+"""), "$1[REDACTED]")
            .replace(Regex("""(?i)(https?://)([^:/@\s]+)(:[^@\s]+)?@"""), "$1[REDACTED]@")
            .replace(Regex("""(?i)(password\s*[:=]\s*)[^\s\r\n,;]+"""), "$1[REDACTED]")
    }

    /**
     * Checks if the filename extension belongs to supported audio extensions.
     */
    fun isAudioFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in audioExtensions
    }

    /**
     * Checks if the filename extension belongs to supported image extensions (for covers/artwork).
     */
    fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in imageExtensions
    }

    /**
     * Generates standard HTTP Basic Authentication header value.
     * Returns null if both username and password are blank.
     */
    fun basicAuthHeader(username: String, password: String): String? {
        if (username.isBlank() && password.isBlank()) return null
        val credentials = "$username:$password"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }

    data class ServerAddress(
        val scheme: String,
        val host: String,
        val port: Int,
    )

    /**
     * Extracts normalized scheme, host, and port for authentication scoping.
     */
    fun splitAddress(url: String): ServerAddress? = runCatching {
        val normalized = normalizeUrl(url)
        val uri = URI(normalized)
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return@runCatching null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return@runCatching null
        val defaultPort = if (scheme == "http") 80 else 443
        val port = if (uri.port != -1) uri.port else defaultPort
        ServerAddress(scheme, host, port)
    }.getOrNull()
}
