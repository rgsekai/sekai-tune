/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources.webdav

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import moe.rgsekai.sekaitune.db.entities.WebDavSongEntity
import moe.rgsekai.sekaitune.utils.isWifiConnected
import moe.rgsekai.sekaitune.webdav.WebDavAuth
import moe.rgsekai.sekaitune.webdav.WebDavTagReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import timber.log.Timber
import java.util.concurrent.TimeUnit

sealed interface WebDavTagScanState {
    data object Idle : WebDavTagScanState
    data class Reading(val completed: Int, val total: Int) : WebDavTagScanState
    data class Done(val totalProcessed: Int) : WebDavTagScanState
    data class Error(val message: String) : WebDavTagScanState
}

object WebDavTagScanner {
    const val WORK_NAME = "webdav_tag_scan"
    private const val HEADER_READ_SIZE = 65536L // 64 KB

    private val _tagScanState = MutableStateFlow<WebDavTagScanState>(WebDavTagScanState.Idle)
    val tagScanState: StateFlow<WebDavTagScanState> = _tagScanState.asStateFlow()

    fun updateState(state: WebDavTagScanState) {
        _tagScanState.value = state
    }

    fun startTagScan(context: Context, forceWifiOnly: Boolean? = null, resetArt: Boolean = false) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WebDavEntryPoint::class.java)
        val credentialStore = entryPoint.credentialStore()
        val wifiOnly = forceWifiOnly ?: credentialStore.wifiOnlyTags

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val inputData = androidx.work.workDataOf("reset_art" to resetArt)
        val workRequest = OneTimeWorkRequestBuilder<WebDavTagWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        val policy = if (resetArt) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            policy,
            workRequest,
        )
    }

    fun cancelTagScan(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        _tagScanState.value = WebDavTagScanState.Idle
    }
}

class WebDavTagWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WebDavEntryPoint::class.java)
        val database = entryPoint.database()
        val credentialStore = entryPoint.credentialStore()

        if (!credentialStore.isConfigured) {
            WebDavTagScanner.updateState(WebDavTagScanState.Idle)
            return@withContext Result.success()
        }

        if (inputData.getBoolean("reset_art", false)) {
            database.webDavSongDao.resetUncheckedArt()
        }

        if (credentialStore.wifiOnlyTags && !isWifiConnected(context)) {
            Timber.d("WebDavTagWorker paused: Wi-Fi only enabled but device is on cellular/offline")
            return@withContext Result.retry()
        }

        val baseClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
        val client = WebDavAuth.createWebDavHttpClient(baseClient)

        val semaphore = Semaphore(4)
        var totalProcessed = 0

        while (!isStopped) {
            if (credentialStore.wifiOnlyTags && !isWifiConnected(context)) {
                return@withContext Result.retry()
            }

            val unreadBatch = database.webDavSongDao.getSongsWithoutTags(limit = 100)
            if (unreadBatch.isEmpty()) {
                break
            }

            val totalInBatch = unreadBatch.size
            var completedInBatch = 0
            WebDavTagScanner.updateState(
                WebDavTagScanState.Reading(
                    completed = totalProcessed,
                    total = totalProcessed + totalInBatch,
                ),
            )

            coroutineScope {
                unreadBatch.map { song ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            if (isStopped) return@withPermit
                            processSongTags(song, client, database, context)
                            synchronized(this@WebDavTagWorker) {
                                totalProcessed++
                                completedInBatch++
                                WebDavTagScanner.updateState(
                                    WebDavTagScanState.Reading(
                                        completed = totalProcessed,
                                        total = totalProcessed + (totalInBatch - completedInBatch),
                                    ),
                                )
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        WebDavTagScanner.updateState(WebDavTagScanState.Done(totalProcessed = totalProcessed))
        Result.success()
    }

    private suspend fun processSongTags(
        song: WebDavSongEntity,
        client: OkHttpClient,
        database: moe.rgsekai.sekaitune.db.MusicDatabase,
        context: Context,
    ) {
        val initialBytes = fetchBytesRange(song.path, client, start = 0L, end = 65535L)
        if (initialBytes == null || initialBytes.size < 8) {
            database.webDavSongDao.markTagsRead(song.path)
            return
        }

        val ext = song.path.substringAfterLast('.', "")
        var parsed = WebDavTagReader.parse(initialBytes, ext)

        // If no embedded artwork was extracted, check if the declared tag/metadata size extends beyond 64KB
        if (parsed?.pictureBytes == null) {
            val neededSize = WebDavTagReader.determineRequiredHeaderSize(
                bytes = initialBytes,
                extension = ext,
                maxCapBytes = 3L * 1024 * 1024, // 3 MB cap
            )
            if (neededSize != null && neededSize > initialBytes.size) {
                Timber.d("Song '%s' declared tag size %d bytes > %d bytes, fetching expanded range", song.title, neededSize, initialBytes.size)
                val fullBytes = fetchBytesRange(song.path, client, start = 0L, end = neededSize - 1)
                if (fullBytes != null && fullBytes.isNotEmpty()) {
                    val fullParsed = WebDavTagReader.parse(fullBytes, ext)
                    if (fullParsed != null) {
                        parsed = fullParsed
                    }
                }
            }
        }

        // For MP4/M4A, if moov atom with cover art is at the end of the file, inspect tail 512KB
        if (parsed?.pictureBytes == null && ext in setOf("m4a", "mp4", "aac") && song.contentLength > 65536L) {
            val tailLen = minOf(song.contentLength, 524288L)
            val tailStart = song.contentLength - tailLen
            val tailBytes = fetchBytesRange(song.path, client, start = tailStart, end = song.contentLength - 1)
            if (tailBytes != null && tailBytes.isNotEmpty()) {
                val tailParsed = WebDavTagReader.parseMp4(tailBytes)
                if (tailParsed?.pictureBytes != null) {
                    parsed = parsed?.copy(
                        pictureBytes = tailParsed.pictureBytes,
                        pictureMimeType = tailParsed.pictureMimeType,
                        title = parsed.title ?: tailParsed.title,
                        artist = parsed.artist ?: tailParsed.artist,
                        album = parsed.album ?: tailParsed.album,
                    ) ?: tailParsed
                }
            }
        }

        val newTitle = parsed?.title?.takeIf { it.isNotBlank() } ?: song.title
        val newArtist = parsed?.artist?.takeIf { it.isNotBlank() } ?: song.artist

        // Clean album: sanitize any hostname or empty values to "WebDAV"
        val rawAlbum = parsed?.album?.takeIf { it.isNotBlank() } ?: song.album
        val newAlbum = if (moe.rgsekai.sekaitune.webdav.WebDavMetadata.isHostOrRoot(rawAlbum, song.path)) {
            "WebDAV"
        } else if (rawAlbum.isBlank()) {
            "WebDAV"
        } else {
            rawAlbum
        }

        val newAlbumArtist = parsed?.albumArtist?.takeIf { it.isNotBlank() } ?: song.albumArtist
        val newTrackNumber = parsed?.trackNumber ?: song.trackNumber
        val newDiscNumber = parsed?.discNumber ?: song.discNumber

        var newArtUrl = song.artUrl
        val pictureBytes = parsed?.pictureBytes
        if (pictureBytes != null && pictureBytes.isNotEmpty()) {
            val cachedUri = WebDavArtworkCache.saveArtwork(context, pictureBytes)
            if (cachedUri != null) {
                newArtUrl = cachedUri
                Timber.i("Extracted and cached WebDAV artwork for '%s' -> %s", newTitle, newArtUrl)
            }
        }

        database.webDavSongDao.updateTags(
            path = song.path,
            title = newTitle,
            artist = newArtist,
            album = newAlbum,
            albumArtist = newAlbumArtist,
            trackNumber = newTrackNumber,
            discNumber = newDiscNumber,
            artUrl = newArtUrl,
        )

        if (!newArtUrl.isNullOrBlank()) {
            val songId = "webdav:${song.path}"
            val existingSong = database.song(songId).firstOrNull()
            if (existingSong != null && existingSong.song.thumbnailUrl != newArtUrl) {
                database.update(existingSong.song.copy(thumbnailUrl = newArtUrl))
            }
        }
    }

    private fun fetchBytesRange(path: String, client: OkHttpClient, start: Long, end: Long): ByteArray? {
        return try {
            val request = Request.Builder()
                .url(path)
                .header("Range", "bytes=$start-$end")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206 && response.code != 200) {
                    Timber.w("HTTP %d fetching range %d-%d for %s", response.code, start, end, moe.rgsekai.sekaitune.webdav.WebDavConfig.cleanUrl(path))
                    return null
                }
                val body = response.body ?: return null
                val source = body.source()
                val buffer = Buffer()
                val maxToRead = end - start + 1
                while (!source.exhausted() && buffer.size < maxToRead) {
                    val read = source.read(buffer, maxToRead - buffer.size)
                    if (read == -1L) break
                }
                val result = buffer.readByteArray()
                Timber.d("Fetched %d bytes (requested %d-%d) for %s", result.size, start, end, moe.rgsekai.sekaitune.webdav.WebDavConfig.cleanUrl(path))
                result
            }
        } catch (e: Throwable) {
            Timber.w(e, "Failed to read bytes range %d-%d for %s", start, end, moe.rgsekai.sekaitune.webdav.WebDavConfig.cleanUrl(path))
            null
        }
    }
}
