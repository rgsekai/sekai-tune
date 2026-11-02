package moe.rgsekai.sekaitune.shared.data.listentogether

/**
 * The few parts of `scheme://host[:port]/path?query#fragment` the party code looks at, with
 * `java.net.URI`'s habit of answering null for a host it cannot make sense of.
 */
internal class SimpleUri(
    val scheme: String?,
    val host: String?,
    val port: Int,
    val path: String,
    val rawQuery: String?,
    val rawFragment: String?,
)

private const val ILLEGAL = "\"<>\\^`{|}"

internal fun parseUri(text: String): SimpleUri? {
    if (text.any { it in ILLEGAL || it.isWhitespace() }) return null
    val schemeEnd = text.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = text.substring(0, schemeEnd)
    if (!scheme[0].isLetter() || !scheme.all { it.isLetterOrDigit() || it in "+-." }) return null
    var rest = text.substring(schemeEnd + 3)
    val fragment = rest.substringAfter('#', "").takeIf { '#' in rest }
    rest = rest.substringBefore('#')
    val query = rest.substringAfter('?', "").takeIf { '?' in rest }
    rest = rest.substringBefore('?')
    val authority = rest.substringBefore('/').substringAfterLast('@')
    val path = rest.substring(rest.substringBefore('/').length)
    if (authority.isEmpty()) return null
    val host: String
    val portText: String
    if (authority.startsWith("[")) {
        val close = authority.indexOf(']')
        if (close < 0) return null
        host = authority.substring(0, close + 1)
        val after = authority.substring(close + 1)
        if (after.isNotEmpty() && !after.startsWith(":")) return null
        portText = after.removePrefix(":")
    } else {
        host = authority.substringBeforeLast(':')
        portText = if (':' in authority) authority.substringAfterLast(':') else ""
    }
    if (host.isEmpty() || ':' in host && !host.startsWith("[")) return null
    val port = if (portText.isEmpty()) -1 else if (portText.all { it.isDigit() }) portText.toIntOrNull() ?: Int.MAX_VALUE else return null
    return SimpleUri(scheme, host, port, path, query, fragment)
}
