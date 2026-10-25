/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.webdav

data class ParsedTrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
)

object WebDavMetadata {

    private val LEADING_TRACK_NUMBER_REGEX = Regex("""^(?:\d{1,3}\s*[-._]\s*|\d{1,3}\s+)""")

    val SIBLING_ART_BASENAMES = listOf(
        "cover",
        "folder",
        "front",
        "albumart",
        "album",
        "artwork",
    )

    val SIBLING_ART_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /**
     * Parses title, artist, and album from a resource URL / filename and its parent folder name.
     *
     * Rules:
     * - Filename is stripped of its audio extension.
     * - Leading track numbers ("01 - ", "01. ", "01 ", "1 - ", etc.) are stripped.
     * - If the remaining name contains " - ", splits into artist and title ("Artist - Title").
     * - Otherwise, the entire name is used as the title and artist defaults to "Unknown Artist".
     * - Also strips any leading track numbers from the title if formatted as "Artist - 01 - Title".
     * - Album defaults to parentFolderName if not blank, else "WebDAV".
     */
    fun parseTrack(
        filenameOrUrl: String,
        parentFolderName: String? = null,
        defaultArtist: String = "Unknown Artist",
        defaultAlbum: String = "WebDAV",
    ): ParsedTrackMetadata {
        val rawFilename = extractFilename(filenameOrUrl)
        val nameWithoutExt = stripExtension(rawFilename)

        val cleanedOverall = stripLeadingTrackNumber(nameWithoutExt).trim()

        val (rawArtist, rawTitle) = if (cleanedOverall.contains(" - ")) {
            val parts = cleanedOverall.split(" - ", limit = 2)
            val artistPart = parts[0].trim()
            val titlePart = parts[1].trim()
            if (artistPart.isNotBlank() && titlePart.isNotBlank()) {
                artistPart to titlePart
            } else {
                defaultArtist to cleanedOverall
            }
        } else {
            defaultArtist to cleanedOverall
        }

        val cleanTitle = stripLeadingTrackNumber(rawTitle).trim().ifBlank {
            stripExtension(rawFilename).ifBlank { "Unknown Title" }
        }
        val cleanArtist = rawArtist.ifBlank { defaultArtist }
        val cleanAlbum = parentFolderName?.trim()?.takeIf { it.isNotBlank() && !isHostOrRoot(it, filenameOrUrl) } ?: defaultAlbum

        return ParsedTrackMetadata(
            title = cleanTitle,
            artist = cleanArtist,
            album = cleanAlbum,
        )
    }

    fun isHostOrRoot(name: String, urlOrPath: String? = null): Boolean {
        val trimmed = name.trim().lowercase(java.util.Locale.ROOT)
        if (trimmed.isEmpty()) return true
        if (trimmed.contains("://")) return true
        if (trimmed.matches(Regex("""^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}(?::\d+)?$"""))) return true
        if (trimmed.matches(Regex("""^\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?$"""))) return true
        if (urlOrPath != null) {
            val host = runCatching { java.net.URI(urlOrPath).host }.getOrNull()?.lowercase(java.util.Locale.ROOT)
            if (host != null && (trimmed == host || host.contains(trimmed) || trimmed.contains(host))) {
                return true
            }
        }
        return false
    }

    fun stripLeadingTrackNumber(input: String): String {
        return input.replace(LEADING_TRACK_NUMBER_REGEX, "")
    }

    fun extractFilename(urlOrPath: String): String {
        val trimmed = urlOrPath.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        val filename = if (lastSlash >= 0) trimmed.substring(lastSlash + 1) else trimmed
        return runCatching { java.net.URLDecoder.decode(filename, "UTF-8") }.getOrDefault(filename)
    }

    fun extractParentFolder(urlOrPath: String): String {
        val trimmed = urlOrPath.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        if (lastSlash <= 0) return ""
        val parentPath = trimmed.substring(0, lastSlash)
        val schemeIdx = parentPath.indexOf("://")
        if (schemeIdx >= 0) {
            val pathAfterScheme = parentPath.substring(schemeIdx + 3)
            val firstSlash = pathAfterScheme.indexOf('/')
            if (firstSlash < 0) {
                return ""
            }
        }
        val prevSlash = parentPath.lastIndexOf('/')
        val parentName = if (prevSlash >= 0) parentPath.substring(prevSlash + 1) else parentPath
        val decoded = runCatching { java.net.URLDecoder.decode(parentName, "UTF-8") }.getOrDefault(parentName)
        if (isHostOrRoot(decoded, urlOrPath)) return ""
        return decoded
    }

    fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    /**
     * Given a list of sibling image URLs / filenames in the same directory,
     * returns the most preferred cover art URL, or null if none match.
     *
     * Preferred order: cover, folder, front, albumart, album, artwork.
     * Supported extensions: jpg, jpeg, png, webp.
     */
    fun pickSiblingCoverArt(imageUrls: List<String>): String? {
        if (imageUrls.isEmpty()) return null

        val candidates = imageUrls.mapNotNull { url ->
            val filename = extractFilename(url).lowercase()
            val dot = filename.lastIndexOf('.')
            if (dot <= 0) return@mapNotNull null
            val base = filename.substring(0, dot)
            val ext = filename.substring(dot + 1)
            if (ext !in SIBLING_ART_EXTENSIONS) return@mapNotNull null
            url to base
        }

        for (preferredBase in SIBLING_ART_BASENAMES) {
            val match = candidates.firstOrNull { it.second == preferredBase }
            if (match != null) return match.first
        }

        for (preferredBase in SIBLING_ART_BASENAMES) {
            val match = candidates.firstOrNull { it.second.contains(preferredBase) }
            if (match != null) return match.first
        }

        return candidates.firstOrNull()?.first
    }
}
