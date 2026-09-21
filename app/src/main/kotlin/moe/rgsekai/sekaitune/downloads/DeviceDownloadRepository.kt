/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.downloads

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import moe.rgsekai.sekaitune.download.TAG_SAVE_TO_DEVICE
import moe.rgsekai.sekaitune.download.startDownload
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.ui.utils.formatFileSize
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceDownloadRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val workManager = WorkManager.getInstance(context)

        fun observeDownloaded(): Flow<List<DownloadEntryUiModel>> =
            callbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean, uri: Uri?) {
                            trySend(queryDeviceDownloads())
                        }
                    }

                context.contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer,
                )

                // Initial emit
                trySend(queryDeviceDownloads())

                awaitClose {
                    context.contentResolver.unregisterContentObserver(observer)
                }
            }.flowOn(Dispatchers.IO)

        fun observeInProgress(): Flow<List<DownloadEntryUiModel>> =
            workManager
                .getWorkInfosByTagFlow(TAG_SAVE_TO_DEVICE)
                .map { workInfos ->
                    workInfos
                        .filter { info ->
                            info.state == WorkInfo.State.RUNNING ||
                                info.state == WorkInfo.State.ENQUEUED ||
                                info.state == WorkInfo.State.BLOCKED ||
                                info.state == WorkInfo.State.FAILED
                        }
                        .map { info ->
                            val workId = info.id.toString()
                            val progressData = info.progress
                            val percent = progressData.getInt("PROGRESS", 0)
                            val stage = progressData.getString("STAGE") ?: if (info.state == WorkInfo.State.ENQUEUED) "queued" else "running"
                            val songTitle = progressData.getString("SONG_TITLE")
                                ?: info.tags.firstOrNull { it.startsWith("title:") }?.removePrefix("title:")
                                ?: "Exporting song..."
                            val songArtist = progressData.getString("SONG_ARTIST")
                                ?: info.tags.firstOrNull { it.startsWith("artist:") }?.removePrefix("artist:")
                                ?: "Save to Device"
                            val songId = progressData.getString("SONG_ID")
                                ?: info.tags.firstOrNull { it.startsWith("song_id:") }?.removePrefix("song_id:")
                                ?: workId

                            val isFailed = info.state == WorkInfo.State.FAILED
                            val supportingText = when {
                                isFailed -> "Download failed"
                                stage == "queued" -> "Queued"
                                stage == "resolving" -> "Resolving stream..."
                                stage == "downloading" -> "Downloading... $percent%"
                                stage == "artwork" -> "Processing artwork..."
                                stage == "tagging" -> "Tagging and finalizing..."
                                else -> "$songArtist • In Progress"
                            }

                            DownloadEntryUiModel(
                                id = workId,
                                title = songTitle,
                                supportingText = supportingText,
                                thumbnailUrl = null,
                                destinationRoute = null,
                                playbackMetadata = null,
                                durationSeconds = null,
                                songIds = listOf(songId),
                                totalCount = 1,
                                progress = (percent / 100f).coerceIn(0f, 1f),
                                percent = percent,
                                speedBytesPerSecond = 0L,
                                paused = false,
                                failed = isFailed,
                            )
                        }
                }.flowOn(Dispatchers.IO)

        fun cancel(workId: String) {
            runCatching {
                workManager.cancelWorkById(UUID.fromString(workId))
            }
        }

        fun retry(entry: DownloadEntryUiModel) {
            val songId = entry.songIds.firstOrNull() ?: return
            startDownload(
                context = context,
                songId = songId,
                title = entry.title,
                artist = entry.supportingText?.substringBefore(" •") ?: "Unknown Artist",
            )
        }

        fun delete(entry: DownloadEntryUiModel) {
            val uriStr = entry.playbackMetadata?.id ?: entry.id
            val uri = Uri.parse(uriStr)
            runCatching {
                context.contentResolver.delete(uri, null, null)
            }
        }

        private fun queryDeviceDownloads(): List<DownloadEntryUiModel> {
            val projection = buildList {
                add(MediaStore.Audio.Media._ID)
                add(MediaStore.Audio.Media.TITLE)
                add(MediaStore.Audio.Media.DISPLAY_NAME)
                add(MediaStore.Audio.Media.ARTIST)
                add(MediaStore.Audio.Media.ALBUM)
                add(MediaStore.Audio.Media.DURATION)
                add(MediaStore.Audio.Media.SIZE)
                add(MediaStore.Audio.Media.MIME_TYPE)
                add(MediaStore.Audio.Media.DATE_MODIFIED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.MediaColumns.RELATIVE_PATH)
                    add(MediaStore.MediaColumns.IS_PENDING)
                } else {
                    add(MediaStore.MediaColumns.DATA)
                }
            }.toTypedArray()

            val selection = buildList {
                add("${MediaStore.Audio.Media.SIZE} > 0")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add("${MediaStore.MediaColumns.RELATIVE_PATH} LIKE 'Music/Sekai Tune%'")
                    add("${MediaStore.MediaColumns.IS_PENDING} = 0")
                } else {
                    add("${MediaStore.MediaColumns.DATA} LIKE '%/Music/Sekai Tune/%'")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add("is_trashed = 0")
                }
            }.joinToString(" AND ")

            val result = mutableListOf<DownloadEntryUiModel>()

            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.DATE_MODIFIED} DESC, ${MediaStore.Audio.Media._ID} DESC",
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idIndex)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                    val title = cursor.getString(titleIndex)?.takeIf(String::isNotBlank)
                        ?: cursor.getString(displayNameIndex)?.substringBeforeLast(".")
                        ?: "Unknown"
                    val artist = cursor.getString(artistIndex)?.takeIf(String::isNotBlank) ?: "Unknown Artist"
                    val album = cursor.getString(albumIndex)?.takeIf(String::isNotBlank) ?: title
                    val durationMs = cursor.getLong(durationIndex)
                    val sizeBytes = cursor.getLong(sizeIndex)
                    val dateModified = cursor.getLong(dateModifiedIndex)
                    val displayName = cursor.getString(displayNameIndex).orEmpty()
                    val mimeType = cursor.getString(mimeTypeIndex).orEmpty()

                    val formatBadge = when {
                        displayName.endsWith(".opus", ignoreCase = true) || mimeType.contains("ogg") || mimeType.contains("opus") -> "OPUS"
                        displayName.endsWith(".flac", ignoreCase = true) || mimeType.contains("flac") -> "FLAC"
                        displayName.endsWith(".m4a", ignoreCase = true) || mimeType.contains("mp4") || mimeType.contains("m4a") || mimeType.contains("aac") -> "M4A"
                        else -> "MP3"
                    }

                    val formattedSize = formatFileSize(sizeBytes)
                    val supportingText = "$artist • $formatBadge • $formattedSize"
                    val artUri = extractArtworkUri(contentUri, dateModified, sizeBytes)

                    val metadata = MediaMetadata(
                        id = contentUri.toString(),
                        title = title,
                        artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
                        duration = (durationMs / 1000).toInt(),
                        thumbnailUrl = artUri,
                        album = MediaMetadata.Album(id = "", title = album),
                        explicit = false,
                        isMusicVideo = false,
                    )

                    result.add(
                        DownloadEntryUiModel(
                            id = contentUri.toString(),
                            title = title,
                            supportingText = supportingText,
                            thumbnailUrl = artUri,
                            destinationRoute = null,
                            playbackMetadata = metadata,
                            durationSeconds = (durationMs / 1000).toInt(),
                            songIds = listOf(contentUri.toString()),
                            totalCount = 1,
                            progress = 1f,
                            percent = 100,
                            speedBytesPerSecond = 0L,
                            paused = false,
                            failed = false,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying device downloads from MediaStore")
        }

        return result
    }

    private fun extractArtworkUri(contentUri: Uri, dateModified: Long, size: Long): String? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, contentUri)
            val artworkBytes = retriever.embeddedPicture ?: return null
            val extension = if (artworkBytes.size > 3 && artworkBytes[0] == 0x89.toByte() && artworkBytes[1] == 0x50.toByte()) "png" else "jpg"
            val fileName = "device_art_${contentUri.lastPathSegment}_${dateModified}_$size.$extension"
            val dir = java.io.File(context.cacheDir, "device_artwork")
            val file = java.io.File(dir, fileName)
            if (!file.exists() || file.length() != artworkBytes.size.toLong()) {
                dir.mkdirs()
                file.writeBytes(artworkBytes)
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
    }
