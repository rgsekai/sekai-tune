/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.innertube.utils

import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.pages.LibraryPage
import moe.rgsekai.sekaitune.innertube.pages.PlaylistContinuationPage
import moe.rgsekai.sekaitune.innertube.pages.PlaylistPage
import java.security.MessageDigest

// Cap for LibraryPage.continued() (e.g. FEmusic_liked_playlists / generated mixes)
// to prevent hundreds of sequential continuation requests on the home screen.
private const val LIBRARY_COMPLETION_MAX_REQUESTS = 50

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> =
    runCatching {
        val page = getOrThrow()
        completePlaylistPage(page) { continuation ->
            YouTube.playlistContinuation(continuation, page.playlist.id).getOrNull()
        }
    }

internal suspend fun completePlaylistPage(
    page: PlaylistPage,
    fetchContinuationPage: suspend (String) -> PlaylistContinuationPage?,
): PlaylistPage {
    val songs = page.songs.toMutableList()
    var continuation =
        page.songsContinuation.normalizedContinuation()
            ?: page.continuation.normalizedContinuation()
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 500
    var consecutiveEmptyResponses = 0

    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++

        val continuationPage = fetchContinuationPage(continuation) ?: break

        if (continuationPage.songs.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            songs += continuationPage.songs
        }

        continuation = continuationPage.continuation.normalizedContinuation()
    }

    return page.copy(
        songs = songs,
        songsContinuation = null,
        continuation = null,
    )
}

@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> =
    runCatching {
        val page = getOrThrow()
        val items = page.items.toMutableList()
        var continuation = page.continuation
        val seenContinuations = mutableSetOf<String>()
        var requestCount = 0
        val maxRequests = LIBRARY_COMPLETION_MAX_REQUESTS
        var consecutiveEmptyResponses = 0

        while (continuation != null && requestCount < maxRequests) {
            if (continuation in seenContinuations) {
                break
            }
            seenContinuations.add(continuation)
            requestCount++

            val continuationPage = YouTube.libraryContinuation(continuation).getOrNull() ?: break

            if (continuationPage.items.isEmpty()) {
                consecutiveEmptyResponses++
                if (consecutiveEmptyResponses >= 2) break
            } else {
                consecutiveEmptyResponses = 0
                items += continuationPage.items
            }

            continuation = continuationPage.continuation
        }
        LibraryPage(
            items = items,
            continuation = null,
        )
    }

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie
        .split(";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) {
                null
            } else {
                val key = part.substring(0, splitIndex).trim()
                if (key.isEmpty()) null else key to part.substring(splitIndex + 1).trim()
            }
        }.toMap()

fun hasYouTubeLoginCookie(cookie: String?): Boolean = youtubeLoginCookieValue(cookie) != null

fun youtubeLoginCookieValue(cookie: String?): String? {
    val cookieMap = cookie?.let(::parseCookieString).orEmpty()
    return YOUTUBE_LOGIN_COOKIE_NAMES.firstNotNullOfOrNull { cookieName ->
        cookieMap[cookieName]?.takeIf(String::isNotBlank)
    }
}

private val YOUTUBE_LOGIN_COOKIE_NAMES =
    listOf(
        "SAPISID",
        "__Secure-3PAPISID",
        "__Secure-1PAPISID",
        "APISID",
    )

fun String.parseTime(): Int? {
    val normalized =
        buildString(length) {
            for (char in this@parseTime) {
                val digit = Character.digit(char, 10)
                when {
                    digit >= 0 -> append(digit)
                    char.isDurationSeparator() -> append(':')
                    char.isIgnorableDurationChar() -> Unit
                    else -> return null
                }
            }
        }

    val parts = normalized.split(':')
    if (parts.any { it.isBlank() || it.length > 3 }) return null
    if (parts.size !in 2..3) return null
    if (parts.drop(1).any { it.length !in 1..2 }) return null

    val values = parts.map { it.toIntOrNull() ?: return null }
    if (values.drop(1).any { it !in 0..59 }) return null

    return when (values.size) {
        2 -> values[0] * 60 + values[1]
        3 -> values[0] * 3600 + values[1] * 60 + values[2]
        else -> null
    }
}

private fun Char.isDurationSeparator(): Boolean =
    this == ':' ||
        this == '.' ||
        this == ',' ||
        this == '：' ||
        this == '．' ||
        this == '﹕' ||
        this == '꞉' ||
        this == '∶' ||
        this == '٫'

private fun Char.isIgnorableDurationChar(): Boolean =
    isWhitespace() ||
        Character.getType(this) == Character.FORMAT.toInt()

fun isPrivateId(browseId: String): Boolean = browseId.contains("privately")

private fun String?.normalizedContinuation(): String? = this?.takeUnless(String::isBlank)

fun parseSongCount(text: String?): Int? {
    if (text.isNullOrBlank()) return null
    val clean = text.trim()

    // 1. Compact notation like "1.2K", "5K+", "2.5M"
    val kMatch = Regex("""([\d.,]+)\s*[kK]""").find(clean)
    if (kMatch != null) {
        val numStr = kMatch.groupValues[1].replace(",", ".")
        return (numStr.toDoubleOrNull()?.times(1000))?.toInt()
    }
    val mMatch = Regex("""([\d.,]+)\s*[mM]""").find(clean)
    if (mMatch != null) {
        val numStr = mMatch.groupValues[1].replace(",", ".")
        return (numStr.toDoubleOrNull()?.times(1_000_000))?.toInt()
    }

    // 2. Specific song/track/video indicator: e.g. "1,200 songs", "5,000+ tracks", "100 videos", "50+ items"
    val songWordPattern = Regex("""(\d{1,3}(?:[,\s.]\d{3})+|\d+)\s*\+?\s*(?:songs?|tracks?|titles?|videos?|items?|views?|song|track)""", RegexOption.IGNORE_CASE)
    val songWordMatch = songWordPattern.find(clean)
    if (songWordMatch != null) {
        val digitsOnly = songWordMatch.groupValues[1].filter { it.isDigit() }
        return digitsOnly.toIntOrNull()
    }

    // 3. Fallback: match digits but ignore release years (1950..2035)
    val numberPattern = Regex("""(\d{1,3}(?:[,\s.]\d{3})+|\d+)""")
    val numberMatch = numberPattern.find(clean)?.value ?: return null
    val digitsOnly = numberMatch.filter { it.isDigit() }
    val number = digitsOnly.toIntOrNull() ?: return null

    if (number in 1950..2035 && !clean.contains(Regex("""(?i)\b(songs?|tracks?|titles?|videos?|items?)\b"""))) {
        return null
    }

    return number
}




