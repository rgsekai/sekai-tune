package moe.rgsekai.sekaitune.shared.data.listentogether

import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ParsedJamInvite(
    val code: String,
    val serverUrl: String? = null,
)

/** Relays a Sekai Tune web or scheme invite from the platform's link handler to Compose. */
object JamInviteLink {

    const val ORIGIN = ""

    private const val HOST = ""
    private const val CUSTOM_SCHEME = "Sekai Tune"
    private const val CUSTOM_HOST = "party"

    private val _pending = MutableStateFlow<ParsedJamInvite?>(null)
    val pending: StateFlow<ParsedJamInvite?> = _pending.asStateFlow()

    /** Hands over an invite the platform has just received; false when [url] is not one. */
    fun offer(url: String?): Boolean {
        _pending.value = parseInvite(url) ?: return false
        return true
    }

    fun handled() {
        _pending.value = null
    }

    /** Returns the normalized party code only for the public invite URL shape. */
    fun parse(value: String?): String? = parseInvite(value)?.code

    /**
     * Parses an incoming invite:
     * 1. Sekai Tune://party/<CODE>?server=<SERVER>
     * 2. https://sekai-party.internal/invite/<CODE>?server=<SERVER>
     */
    fun parseInvite(value: String?): ParsedJamInvite? {
        val uri = parseUri(value ?: return null) ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val query = uri.rawQuery
        val server = extractQueryParam(query, "server")?.let { sanitizeServerUrl(it) }

        // 1. Custom scheme: Sekai Tune://party/<CODE> or Sekai Tune://party?code=<CODE>
        if (scheme == CUSTOM_SCHEME && host == CUSTOM_HOST) {
            val pathPart = uri.path.trim('/').takeIf { it.isNotBlank() }
            val candidate = pathPart ?: extractQueryParam(query, "code") ?: return null
            val code = cleanCode(candidate) ?: return null
            return ParsedJamInvite(code = code, serverUrl = server)
        }

        // 2. Official web domain: https://sekai-party.internal/invite/<CODE>
        if (scheme == "https" && host == HOST) {
            val match = INVITE_PATH.matchEntire(uri.path) ?: return null
            val code = match.groupValues[1].uppercase()
            return ParsedJamInvite(code = code, serverUrl = server)
        }

        return null
    }

    fun url(code: String, customServer: String? = null): String {
        val base = customServer?.trim()?.trimEnd('/')
        return if (!base.isNullOrBlank() && !base.equals(ORIGIN, ignoreCase = true)) {
            "$base/invite/${code.uppercase()}"
        } else {
            "$ORIGIN/invite/${code.uppercase()}"
        }
    }

    fun schemeUrl(code: String, customServer: String? = null): String {
        val normalizedCode = code.uppercase()
        val base = customServer?.trim()?.trimEnd('/')
        return if (!base.isNullOrBlank()) {
            val encoded = base.encodeURLParameter(spaceToPlus = true)
            "Sekai Tune://party/$normalizedCode?server=$encoded"
        } else {
            "Sekai Tune://party/$normalizedCode"
        }
    }

    private fun cleanCode(raw: String): String? {
        val cleaned = raw.filter { it.isLetterOrDigit() }.uppercase()
        return if (cleaned.length == ListenTogether.CODE_LENGTH) cleaned else null
    }

    private fun extractQueryParam(query: String?, paramName: String): String? {
        if (query.isNullOrBlank()) return null
        return query.split('&').asSequence()
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.isNotEmpty() && it[0].equals(paramName, ignoreCase = true) }
            ?.getOrNull(1)
            ?.let { runCatching { it.decodeURLQueryComponent(plusIsSpace = true) }.getOrDefault(it) }
    }

    private fun sanitizeServerUrl(raw: String?): String? {
        val trimmed = raw?.trim()?.trimEnd('/') ?: return null
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = parseUri(withScheme) ?: return null
        if (uri.host.isNullOrBlank()) return null
        return withScheme
    }

    private val INVITE_PATH = Regex("""/invite/([A-Za-z0-9]{${ListenTogether.CODE_LENGTH}})""")
}

