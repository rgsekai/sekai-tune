/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.extensions

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import moe.rgsekai.sekaitune.db.entities.Song
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import moe.rgsekai.sekaitune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.models.toMediaMetadata
import moe.rgsekai.sekaitune.ui.utils.YTThumbQuality
import moe.rgsekai.sekaitune.ui.utils.buildYTThumbnailUrl
import moe.rgsekai.sekaitune.ui.utils.resize
import moe.rgsekai.sekaitune.utils.isLocalMediaId

const val ExtraIsMusicVideo = "moe.rgsekai.sekaitune.extra.IS_MUSIC_VIDEO"
const val ExtraIsUploaded = "moe.rgsekai.sekaitune.extra.IS_UPLOADED"
const val ExtraUploadEntityId = "moe.rgsekai.sekaitune.extra.UPLOAD_ENTITY_ID"
private const val NotificationArtworkSizePx = 1080

val MediaItem.metadata: MediaMetadata?
    get() {
        val meta = localConfiguration?.tag as? MediaMetadata ?: return null
        val matched = moe.rgsekai.sekaitune.playback.MusicService.getMatchedCatalogId(mediaId)
        return if (matched != null && meta.matchedCatalogId == null) {
            meta.copy(matchedCatalogId = matched)
        } else {
            meta
        }
    }

private fun String?.toNotificationArtworkUri(): Uri? {
    if (this.isNullOrBlank()) return null
    return runCatching {
        if (startsWith("content://") || startsWith("file://") || startsWith("android.resource://")) {
            toUri()
        } else {
            resize(NotificationArtworkSizePx, NotificationArtworkSizePx).toUri()
        }
    }.getOrNull()
}

private fun MediaItem.Builder.setCacheKeyIfRemote(mediaId: String): MediaItem.Builder {
    if (!mediaId.isLocalMediaId()) {
        setCustomCacheKey(mediaId)
    }
    return this
}

fun Song.toMediaItem(streamMediaId: String? = null): MediaItem {
    val actualMediaId = streamMediaId ?: song.matchedCatalogId ?: song.id
    val isWebDav = actualMediaId.startsWith("webdav:")
    val playUri = if (isWebDav) {
        val path = actualMediaId.removePrefix("webdav:")
        path.toUri()
    } else {
        actualMediaId.toUri()
    }
    val customCacheKey = if (isWebDav) {
        val path = actualMediaId.removePrefix("webdav:")
        moe.rgsekai.sekaitune.playback.sources.webdav.WebDavMediaItemSpec.buildCustomCacheKey(path)
    } else null

    val builder = MediaItem
        .Builder()
        .setMediaId(actualMediaId)
        .setUri(playUri)

    if (customCacheKey != null) {
        builder.setCustomCacheKey(customCacheKey)
    } else {
        builder.setCacheKeyIfRemote(actualMediaId)
    }

    return builder
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(song.title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(
                    song.thumbnailUrl.toNotificationArtworkUri()
                        ?: if (!song.id.isLocalMediaId() && !isWebDav) buildYTThumbnailUrl(song.id, YTThumbQuality.HQ).toUri() else null,
                )
                .setAlbumTitle(song.albumName)
                .setIsPlayable(true)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setExtras(
                    Bundle().apply {
                        putBoolean(ExtraIsMusicVideo, false)
                        putBoolean(ExtraIsUploaded, song.isUploaded || song.thumbnailUrl?.contains("vi_locker") == true)
                        putString(ExtraUploadEntityId, song.uploadEntityId)
                        if (isWebDav) {
                            putBoolean("moe.rgsekai.sekaitune.extra.IS_WEBDAV", true)
                        }
                    },
                ).build(),
        ).build()
}

fun SongItem.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCacheKeyIfRemote(id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(
                    if (isMusicVideo()) {
                        buildYTThumbnailUrl(id, YTThumbQuality.HQ).toUri()
                    } else {
                        thumbnail.toNotificationArtworkUri()
                            ?: if (!id.isLocalMediaId()) buildYTThumbnailUrl(id, YTThumbQuality.HQ).toUri() else null
                    },
                ).setAlbumTitle(album?.name)
                .setIsPlayable(true)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setExtras(
                    Bundle().apply {
                        putBoolean(ExtraIsMusicVideo, isMusicVideo())
                        putBoolean(ExtraIsUploaded, uploadEntityId != null || thumbnail?.contains("vi_locker") == true)
                        putString(ExtraUploadEntityId, uploadEntityId)
                    },
                ).build(),
        ).build()

fun MediaMetadata.toMediaItem(): MediaItem {
    if (id.startsWith("webdav:")) {
        val path = id.removePrefix("webdav:")
        val cleanUrl = moe.rgsekai.sekaitune.webdav.WebDavConfig.cleanUrl(path)
        val cacheKey = "webdav:$cleanUrl"
        val artUri = thumbnailUrl?.takeIf { it.isNotBlank() }?.toUri()
        return MediaItem
            .Builder()
            .setMediaId(id)
            .setUri(path.toUri())
            .setCustomCacheKey(cacheKey)
            .setTag(this)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setSubtitle(artists.joinToString { it.name })
                    .setArtist(artists.joinToString { it.name })
                    .setArtworkUri(artUri)
                    .setAlbumTitle(album?.title)
                    .setIsPlayable(true)
                    .setMediaType(MEDIA_TYPE_MUSIC)
                    .setExtras(
                        Bundle().apply {
                            putBoolean("moe.rgsekai.sekaitune.extra.IS_WEBDAV", true)
                        },
                    ).build(),
            ).build()
    }

    return MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCacheKeyIfRemote(id)
        .setTag(this)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(
                    if (isMusicVideo) {
                        buildYTThumbnailUrl(id, YTThumbQuality.HQ).toUri()
                    } else {
                        thumbnailUrl.toNotificationArtworkUri()
                            ?: if (!id.isLocalMediaId() && !id.startsWith("webdav:")) buildYTThumbnailUrl(id, YTThumbQuality.HQ).toUri() else null
                    },
                ).setAlbumTitle(album?.title)
                .setIsPlayable(true)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setExtras(
                    Bundle().apply {
                        putBoolean(ExtraIsMusicVideo, isMusicVideo)
                        putBoolean(ExtraIsUploaded, isUploaded || thumbnailUrl?.contains("vi_locker") == true)
                        putString(ExtraUploadEntityId, uploadEntityId)
                    },
                ).build(),
        ).build()
}

private fun SongItem.isMusicVideo(): Boolean {
    val musicVideoType = endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
    return musicVideoType == MUSIC_VIDEO_TYPE_OMV || musicVideoType == MUSIC_VIDEO_TYPE_UGC
}




