/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources.webdav

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import moe.rgsekai.sekaitune.db.entities.AlbumEntity
import moe.rgsekai.sekaitune.db.entities.ArtistEntity
import moe.rgsekai.sekaitune.db.entities.Song
import moe.rgsekai.sekaitune.db.entities.SongEntity
import moe.rgsekai.sekaitune.db.entities.WebDavSongEntity
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.webdav.WebDavConfig

object WebDavMediaItemSpec {
    fun buildMediaId(path: String): String = "webdav:$path"
    fun buildCustomCacheKey(path: String): String = "webdav:${WebDavConfig.cleanUrl(path)}"
    fun buildCleanArtUrl(artUrl: String?): String? = artUrl?.takeIf { it.isNotBlank() }?.let { WebDavConfig.cleanUrl(it) }
}

fun WebDavSongEntity.toSong(): Song {
    val durationSec = durationMs?.let { (it / 1000).toInt() } ?: -1
    val mediaId = WebDavMediaItemSpec.buildMediaId(path)
    val songEntity = SongEntity(
        id = mediaId,
        title = title,
        duration = durationSec,
        thumbnailUrl = artUrl,
        albumName = album.takeIf { it.isNotBlank() },
        isLocal = true,
    )
    val artistEntity = ArtistEntity(
        id = "webdav_artist:${artist.hashCode()}",
        name = artist,
    )
    val albumEntity = album.takeIf { it.isNotBlank() }?.let {
        AlbumEntity(
            id = "webdav_album:${it.hashCode()}",
            title = it,
            thumbnailUrl = artUrl,
            songCount = 1,
            duration = durationSec.coerceAtLeast(0),
            isLocal = true,
        )
    }
    return Song(
        song = songEntity,
        artists = listOf(artistEntity),
        album = albumEntity,
    )
}

fun WebDavSongEntity.toMediaItem(): MediaItem {
    val fullUrl = path
    val mediaId = WebDavMediaItemSpec.buildMediaId(path)
    val cacheKey = WebDavMediaItemSpec.buildCustomCacheKey(path)
    val artUri = artUrl?.takeIf { it.isNotBlank() }?.toUri()

    val appMetadata = MediaMetadata(
        id = mediaId,
        title = title,
        artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
        duration = durationMs?.let { (it / 1000).toInt() } ?: -1,
        thumbnailUrl = artUrl,
        album = MediaMetadata.Album(id = album, title = album),
    )

    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(fullUrl.toUri())
        .setCustomCacheKey(cacheKey)
        .setTag(appMetadata)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(artist)
                .setArtist(artist)
                .setAlbumArtist(albumArtist)
                .setTrackNumber(trackNumber)
                .setDiscNumber(discNumber)
                .setAlbumTitle(album)
                .setArtworkUri(artUri)
                .setIsPlayable(true)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setExtras(
                    Bundle().apply {
                        putBoolean("moe.rgsekai.sekaitune.extra.IS_WEBDAV", true)
                    },
                )
                .build(),
        )
        .build()
}
