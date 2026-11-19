/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.playlist

import moe.rgsekai.sekaitune.db.entities.Playlist
import moe.rgsekai.sekaitune.db.entities.PlaylistSong
import moe.rgsekai.sekaitune.ui.utils.resize

sealed interface PlaylistHeroImageResult {
    data class Image(
        val urlOrUri: String,
        val isCustom: Boolean = false,
        val source: Source = Source.CUSTOM,
    ) : PlaylistHeroImageResult {
        enum class Source {
            CUSTOM,
            PLATFORM,
            FIRST_SONG,
        }
    }

    data object Placeholder : PlaylistHeroImageResult
}

object ResolvePlaylistHeroImage {

    /**
     * Resolves the hero image according to the specified priority:
     * 1. Custom image/poster set by the user (content://, file://, or explicitly custom)
     * 2. Platform cover art (YouTube Music, Spotify, Community playlists, etc.)
     * 3. First song's album art in the playlist
     * 4. Clean placeholder (if empty and no art)
     */
    fun resolve(
        customImageUri: String? = null,
        platformCoverUrl: String? = null,
        firstSongThumbnailUrl: String? = null,
    ): PlaylistHeroImageResult {
        val custom = customImageUri?.trim()?.takeIf { it.isNotEmpty() }
        if (custom != null) {
            return PlaylistHeroImageResult.Image(
                urlOrUri = custom,
                isCustom = true,
                source = PlaylistHeroImageResult.Image.Source.CUSTOM,
            )
        }

        val platform = platformCoverUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (platform != null) {
            return PlaylistHeroImageResult.Image(
                urlOrUri = upgradeThumbnailResolution(platform),
                isCustom = false,
                source = PlaylistHeroImageResult.Image.Source.PLATFORM,
            )
        }

        val firstSong = firstSongThumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (firstSong != null) {
            return PlaylistHeroImageResult.Image(
                urlOrUri = upgradeThumbnailResolution(firstSong),
                isCustom = false,
                source = PlaylistHeroImageResult.Image.Source.FIRST_SONG,
            )
        }

        return PlaylistHeroImageResult.Placeholder
    }

    /**
     * Resolves hero image from a database Playlist entity and its song list.
     */
    fun resolveFromPlaylist(
        playlist: Playlist?,
        songs: List<PlaylistSong>?,
    ): PlaylistHeroImageResult {
        val thumb = playlist?.playlist?.thumbnailUrl?.trim()
        val isLocalUri = thumb != null && (thumb.startsWith("content://") || thumb.startsWith("file://") || !thumb.startsWith("http"))
        val customImageUri = if (isLocalUri) thumb else null
        val platformCoverUrl = if (!isLocalUri && thumb?.startsWith("http") == true) thumb else null
        val firstSongThumbnail = songs?.firstOrNull()?.song?.song?.thumbnailUrl

        return resolve(
            customImageUri = customImageUri,
            platformCoverUrl = platformCoverUrl,
            firstSongThumbnailUrl = firstSongThumbnail,
        )
    }

    /**
     * Upgrades thumbnail URLs to the highest available resolution.
     * Handles Google User Content CDN, Spotify CDN, and YouTube image CDN.
     */
    fun upgradeThumbnailResolution(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed

        // Spotify image URLs: upgrade smaller sizes (1e02 = 300x300, 4851 = 64x64) to b273 (640x640)
        if (trimmed.contains("i.scdn.co/image/")) {
            return trimmed
                .replace("ab67616d00001e02", "ab67616d0000b273")
                .replace("ab67616d00004851", "ab67616d0000b273")
                .replace("ab67616100001e02", "ab6761610000b273")
                .replace("ab67616100004851", "ab6761610000b273")
        }

        // Google CDN / YouTube Music thumbnails: upgrade to 1200x1200 for crisp hero presentation
        if (trimmed.contains("googleusercontent.com") || trimmed.contains("ggpht.com")) {
            return trimmed.resize(1200, 1200)
        }

        // YouTube video thumbnails: upgrade hqdefault/mqdefault to maxresdefault if possible
        if (trimmed.contains("i.ytimg.com/vi/")) {
            return trimmed
                .replace("/mqdefault.jpg", "/maxresdefault.jpg")
                .replace("/default.jpg", "/hqdefault.jpg")
        }

        return trimmed
    }
}

