package moe.rgsekai.sekaitune.data.model

import kotlinx.serialization.Serializable

/** A credited artist reference. */
@Serializable
data class ArtistRef(
    val name: String,
    val browseId: String? = null,
)

/** A playable track model for Replay & Stats. */
@Serializable
data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationText: String? = null,
    val artistId: String? = null,
    val artists: List<ArtistRef> = emptyList(),
    val albumId: String? = null,
    val albumName: String? = null,
    val isVideo: Boolean = false,
    val isVideoOrigin: Boolean = isVideo,
    val setVideoId: String? = null,
    val durationSeconds: Int? = null,
)

fun moe.rgsekai.sekaitune.models.MediaMetadata.toStatsSong(): Song {
    return Song(
        videoId = id,
        title = title,
        artist = artists.joinToString { it.name },
        thumbnailUrl = thumbnailUrl,
        durationText = if (duration > 0) "${duration / 60}:${(duration % 60).toString().padStart(2, '0')}" else null,
        artistId = artists.firstOrNull()?.id,
        artists = artists.map { ArtistRef(name = it.name, browseId = it.id) },
        albumId = album?.id,
        albumName = album?.title,
        isVideo = isMusicVideo,
        isVideoOrigin = isMusicVideo,
        setVideoId = setVideoId,
        durationSeconds = duration,
    )
}

fun Song.artworkAt(px: Int): String? = thumbnailUrl.artworkAt(px)

fun String?.artworkAt(px: Int): String? {
    if (this == null) return null
    val sized = this.replace(SIZE_HINT, "w$px-h$px")
    return if (isGoogleImageHost(this)) {
        sized.replace(PARAM_SIZE_HINT, "=s$px")
    } else {
        sized
    }
}

private fun isGoogleImageHost(url: String): Boolean {
    val host = url.substringAfter("://", "")
        .ifEmpty { url.substringAfter("//", "") }
        .substringBefore('/')
        .substringBefore(':')
        .substringBefore('?')
        .lowercase()
    return host == "ggpht.com" || host.endsWith(".ggpht.com") ||
        host == "googleusercontent.com" || host.endsWith(".googleusercontent.com")
}

private val SIZE_HINT = Regex("""w\d+-h\d+""")
private val PARAM_SIZE_HINT = Regex("""=s\d+""")

const val ROW_ART_PX = 160
const val CARD_ART_PX = 480
const val HEADER_ART_PX = 720
const val NOTIFICATION_ART_PX = 544

fun Song.durationMillis(): Long = durationText.durationMillis()

fun String?.durationMillis(): Long {
    val parts = this?.trim()?.takeIf { it.isNotEmpty() }?.split(":") ?: return 0L
    val numbers = parts.map { it.trim().toLongOrNull() ?: return 0L }
    val seconds = when (numbers.size) {
        2 -> numbers[0] * 60 + numbers[1]
        3 -> numbers[0] * 3_600 + numbers[1] * 60 + numbers[2]
        else -> return 0L
    }
    return (seconds * 1_000).coerceAtLeast(0L)
}

