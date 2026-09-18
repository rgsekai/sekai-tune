/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.canvas.CanvasRequestPolicy
import moe.rgsekai.sekaitune.canvas.CanvasSource
import moe.rgsekai.sekaitune.canvas.SekaiTuneCanvas
import moe.rgsekai.sekaitune.canvas.models.CanvasArtwork
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.ui.utils.highRes
import timber.log.Timber

internal suspend fun resolveCanvasArtworkForPlayback(
    mediaId: String,
    songTitleRaw: String,
    artistNameRaw: String,
    albumId: String? = null,
    albumTitleRaw: String? = null,
    storefront: String,
    requireVertical: Boolean,
    allowNetwork: Boolean,
    canvasPolicy: CanvasRequestPolicy = CanvasRequestPolicy(),
): CanvasArtwork? {
    withContext(Dispatchers.IO) {
        CanvasArtworkPlaybackCache.get(
            mediaId = mediaId,
            preferCachedOnly = true,
        )
    }?.takeIf { artwork -> artwork.hasRequiredCanvasVariant(requireVertical) }
        ?.let { return it }

    if (!allowNetwork || mediaId.isBlank()) {
        Timber.tag(CanvasArtworkLogTag).d("Skipping canvas network lookup for %s", mediaId)
        return null
    }

    return withContext(Dispatchers.IO) {
        val fetched =
            fetchCanvasArtworkForPlayback(
                songTitleRaw = songTitleRaw,
                artistNameRaw = artistNameRaw,
                storefront = storefront,
                requireVertical = requireVertical,
                canvasPolicy = canvasPolicy,
            ) ?: fetchCanvasArtworkByAlbumFallback(
                albumId = albumId,
                albumTitleRaw = albumTitleRaw,
                artistNameRaw = artistNameRaw,
                storefront = storefront,
                requireVertical = requireVertical,
            )

        if (fetched == null) {
            Timber.tag(CanvasArtworkLogTag).d("No playable canvas resolved for %s", mediaId)
            return@withContext null
        }

        CanvasArtworkPlaybackCache.put(mediaId, fetched)
    }
}

internal suspend fun fetchCanvasArtworkForPlayback(
    songTitleRaw: String,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
    canvasPolicy: CanvasRequestPolicy = CanvasRequestPolicy(),
): CanvasArtwork? {
    val songTitle = normalizeCanvasSongTitle(songTitleRaw)
    val artistName = normalizeCanvasArtistName(artistNameRaw)

    val (splitArtist, splitTitle) =
        if (artistName.isBlank() || artistName.equals("Unknown Artist", ignoreCase = true) || artistName.equals("Cloud Audio", ignoreCase = true)) {
            if (songTitleRaw.contains(" - ")) {
                val parts = songTitleRaw.split(" - ", limit = 2)
                normalizeCanvasArtistName(parts[0]) to normalizeCanvasSongTitle(parts[1])
            } else {
                artistName to songTitle
            }
        } else {
            artistName to songTitle
        }

    val candidates =
        linkedSetOf(
            songTitle to artistName,
            splitTitle to splitArtist,
            songTitleRaw to artistName,
            songTitle to artistNameRaw,
            songTitleRaw to artistNameRaw,
            splitTitle to artistName,
            songTitle to "",
            splitTitle to "",
            songTitleRaw to "",
        ).filter { (song, _) ->
            song.isNotBlank()
        }.map { (song, artist) ->
            val cleanArtist =
                if (artist.equals("Unknown Artist", ignoreCase = true) || artist.equals("Cloud Audio", ignoreCase = true)) {
                    ""
                } else {
                    artist
                }
            song to cleanArtist
        }

    val primaryResolved =
        candidates.firstNotNullOfOrNull { (song, artist) ->
            SekaiTuneCanvas
                .getCanvas(
                    song = song,
                    artists = if (artist.isNotBlank()) listOf(artist) else emptyList(),
                    durationMs = null,
                    policy = canvasPolicy,
                    storefront = storefront,
                )?.takeIf { artwork ->
                    artwork.hasRequiredCanvasVariant(requireVertical)
                }
        }

    if (primaryResolved != null) return primaryResolved

    // Fallback: search YouTube Music for official high-resolution song thumbnail
    return runCatching {
        val query =
            if (artistName.isNotBlank() && !artistName.equals("Unknown Artist", ignoreCase = true) && !artistName.equals("Cloud Audio", ignoreCase = true)) {
                "$artistName $songTitle".trim()
            } else if (splitArtist.isNotBlank() && !splitArtist.equals("Unknown Artist", ignoreCase = true)) {
                "$splitArtist $splitTitle".trim()
            } else {
                songTitle.ifBlank { songTitleRaw }
            }
        if (query.isBlank()) return@runCatching null

        val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
        val songItem = searchResult?.items?.filterIsInstance<SongItem>()?.firstOrNull() ?: return@runCatching null
        val thumb = songItem.thumbnail?.highRes() ?: songItem.thumbnail
        CanvasArtwork(
            name = songItem.title,
            artist = songItem.artists.firstOrNull()?.name,
            albumName = songItem.album?.name,
            static = thumb,
        ).takeIf { artwork -> artwork.hasRequiredCanvasVariant(requireVertical) }
    }.getOrNull()
}

private suspend fun fetchCanvasArtworkByAlbumFallback(
    albumId: String?,
    albumTitleRaw: String?,
    artistNameRaw: String,
    storefront: String,
    requireVertical: Boolean,
): CanvasArtwork? {
    albumId
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { nonBlankAlbumId ->
            SekaiTuneCanvas
                .getByAlbumId(nonBlankAlbumId)
                ?.takeIf { artwork -> artwork.hasRequiredCanvasVariant(requireVertical) }
                ?.let { return it }
        }

    val albumTitle = normalizeCanvasSongTitle(albumTitleRaw.orEmpty())
    val artistName = normalizeCanvasArtistName(artistNameRaw)
    if (albumTitle.isBlank() || artistName.isBlank() || artistName.equals("Unknown Artist", ignoreCase = true)) return null

    return SekaiTuneCanvas
        .getBySongArtist(
            song = albumTitle,
            artist = artistName,
            storefront = storefront,
        )?.takeIf { artwork -> artwork.hasRequiredCanvasVariant(requireVertical) }
}

private fun CanvasArtwork.hasRequiredCanvasVariant(requireVertical: Boolean): Boolean =
    if (requireVertical) {
        !preferredVerticalAnimationUrl.isNullOrBlank() || !preferredAnimationUrl.isNullOrBlank() || !static.isNullOrBlank()
    } else {
        !preferredAnimationUrl.isNullOrBlank() || !preferredVerticalAnimationUrl.isNullOrBlank() || !static.isNullOrBlank()
    }

private const val CanvasArtworkLogTag = "CanvasArtwork"

private fun normalizeCanvasSongTitle(raw: String): String {
    val stripped =
        raw
            .replace(Regex("""^\d+[\s.\-_]+"""), "")
            .replace(Regex("""(?i)\s*\((?:mp3|flac|wav|m4a|aac|opus|ogg|320k|320kbps|128k|128kbps|256k|256kbps|audio|official|video|mv|lyrics?|visualizer|remaster(?:ed)?|version|edit|mix|remix|hq|hd|yt|youtube|spotify-downloader\.com)\b[^)]*\)"""), "")
            .replace(Regex("""(?i)\s*\[(?:mp3|flac|wav|m4a|aac|opus|ogg|320k|320kbps|128k|128kbps|256k|256kbps|audio|official|video|mv|lyrics?|visualizer|remaster(?:ed)?|version|edit|mix|remix|hq|hd|yt|youtube|spotify-downloader\.com)\b[^]]*\]"""), "")
            .replace(Regex("""(?i)\s*-\s*(?:mp3|flac|wav|320k|320kbps|audio|official|video|mv|lyrics?|visualizer|remaster(?:ed)?|version|edit|mix|remix|hq|hd)\b.*$"""), "")
            .replace(
                Regex(
                    "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    return stripped
        .trim('-', '_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun normalizeCanvasArtistName(raw: String): String {
    val first =
        raw
            .split(
                Regex(
                    "(?:\\s*,\\s*|\\s*&\\s*|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
                    RegexOption.IGNORE_CASE,
                ),
                limit = 2,
            ).firstOrNull()
            .orEmpty()
            .replace('_', ' ')

    return first.replace(Regex("\\s+"), " ").trim()
}




