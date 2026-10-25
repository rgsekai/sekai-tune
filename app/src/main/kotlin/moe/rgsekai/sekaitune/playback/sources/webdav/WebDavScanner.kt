/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources.webdav

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.WebDavSongEntity
import moe.rgsekai.sekaitune.webdav.WebDavClient
import moe.rgsekai.sekaitune.webdav.WebDavEntry
import moe.rgsekai.sekaitune.webdav.WebDavMetadata
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

sealed interface WebDavScanState {
    data object Idle : WebDavScanState
    data class Scanning(val foundCount: Int = 0) : WebDavScanState
    data class Done(
        val count: Int,
        val timestamp: Long = System.currentTimeMillis(),
        val hitHardCap: Boolean = false,
    ) : WebDavScanState
    data class Error(val message: String) : WebDavScanState
}

data class ScanDiffResult(
    val toInsert: List<WebDavSongEntity>,
    val toUpdate: List<WebDavSongEntity>,
    val toDeletePaths: List<String>,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WebDavEntryPoint {
    fun database(): MusicDatabase
    fun credentialStore(): WebDavCredentialStore
}

object WebDavScanner {
    const val WORK_NAME = "webdav_library_scan"
    private const val PREFS_NAME = "sekaitune_webdav_scanner"
    private const val KEY_LAST_SCAN = "last_scan_timestamp"
    const val HARD_CAP = 10_000

    private val _scanState = MutableStateFlow<WebDavScanState>(WebDavScanState.Idle)
    val scanState: StateFlow<WebDavScanState> = _scanState.asStateFlow()

    fun updateScanState(state: WebDavScanState) {
        _scanState.value = state
    }

    fun startScan(context: Context, force: Boolean = false) {
        val inputData = androidx.work.workDataOf("force" to force)
        val workRequest = OneTimeWorkRequestBuilder<WebDavScanWorker>()
            .setInputData(inputData)
            .build()
        val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, workRequest)
    }

    fun cancelScan(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        _scanState.value = WebDavScanState.Idle
    }

    fun triggerScanIfOlderThan(context: Context, maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastScan = prefs.getLong(KEY_LAST_SCAN, 0L)
        if (System.currentTimeMillis() - lastScan > maxAgeMs) {
            startScan(context, force = false)
        } else {
            WebDavTagScanner.startTagScan(context)
        }
    }

    fun recordLastScanTime(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SCAN, System.currentTimeMillis())
            .apply()
    }

    fun clearCachedData(context: Context, database: MusicDatabase) {
        cancelScan(context)
        WebDavTagScanner.cancelTagScan(context)
        CoroutineScope(Dispatchers.IO).launch {
            database.webDavSongDao.deleteAll()
            WebDavArtworkCache.clearCache(context)
        }
        _scanState.value = WebDavScanState.Idle
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_SCAN)
            .apply()
    }

    fun parseLastModified(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        return runCatching {
            ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }.recoverCatching {
            Instant.parse(header).toEpochMilli()
        }.getOrNull()
    }

    fun computeDiff(
        existing: List<WebDavSongEntity>,
        scannedAudio: List<WebDavEntry>,
        imagesByFolder: Map<String, List<String>>,
        serverId: String,
        now: Long = System.currentTimeMillis(),
    ): ScanDiffResult {
        val existingByPath = existing.associateBy { it.path }
        val scannedPathSet = HashSet<String>(scannedAudio.size)

        val toInsert = ArrayList<WebDavSongEntity>()
        val toUpdate = ArrayList<WebDavSongEntity>()

        for (entry in scannedAudio) {
            val path = entry.url
            scannedPathSet.add(path)

            val parentFolder = WebDavMetadata.extractParentFolder(path)
            val folderUrl = path.substringBeforeLast('/') + "/"
            val siblingArt = WebDavMetadata.pickSiblingCoverArt(imagesByFolder[folderUrl].orEmpty())
            val meta = WebDavMetadata.parseTrack(entry.displayName.ifBlank { path }, parentFolder)
            val lastModifiedMs = parseLastModified(entry.lastModified)
            val size = entry.contentLength ?: 0L

            val match = existingByPath[path]
            if (match == null) {
                toInsert.add(
                    WebDavSongEntity(
                        path = path,
                        serverId = serverId,
                        title = meta.title,
                        artist = meta.artist,
                        album = meta.album,
                        durationMs = null,
                        etag = entry.etag,
                        lastModified = lastModifiedMs,
                        contentLength = size,
                        artUrl = siblingArt,
                        scannedAt = now,
                    ),
                )
            } else {
                val hasChanged = (entry.etag != null && entry.etag != match.etag) ||
                    (lastModifiedMs != null && lastModifiedMs != match.lastModified) ||
                    (entry.contentLength != null && entry.contentLength != match.contentLength) ||
                    (siblingArt != match.artUrl)

                if (hasChanged) {
                    toUpdate.add(
                        match.copy(
                            title = meta.title,
                            artist = meta.artist,
                            album = meta.album,
                            etag = entry.etag ?: match.etag,
                            lastModified = lastModifiedMs ?: match.lastModified,
                            contentLength = size,
                            artUrl = siblingArt,
                            scannedAt = now,
                            tagsRead = false,
                        ),
                    )
                }
            }
        }

        val toDeletePaths = existing.mapNotNull {
            if (it.path !in scannedPathSet) it.path else null
        }

        return ScanDiffResult(
            toInsert = toInsert,
            toUpdate = toUpdate,
            toDeletePaths = toDeletePaths,
        )
    }
}

class WebDavScanWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WebDavEntryPoint::class.java)
        val database = entryPoint.database()
        val credentialStore = entryPoint.credentialStore()

        if (!credentialStore.isConfigured) {
            WebDavScanner.updateScanState(WebDavScanState.Idle)
            return@withContext Result.success()
        }

        val url = credentialStore.url
        val username = credentialStore.username
        val password = credentialStore.password
        val trustSelfSigned = credentialStore.trustSelfSigned

        WebDavScanner.updateScanState(WebDavScanState.Scanning(foundCount = 0))

        val scanResult = WebDavClient.walkLibrary(
            baseUrl = url,
            username = username,
            password = password,
            trustSelfSignedCert = trustSelfSigned,
            maxFiles = WebDavScanner.HARD_CAP,
        )

        if (scanResult.isFailure) {
            val errorMsg = scanResult.exceptionOrNull()?.message ?: "WebDAV scan failed"
            // CRITICAL: on failure, do NOT delete anything
            WebDavScanner.updateScanState(WebDavScanState.Error(errorMsg))
            return@withContext Result.failure()
        }

        val listing = scanResult.getOrThrow()
        val hitHardCap = listing.audio.size >= WebDavScanner.HARD_CAP

        val imagesByFolder = listing.images.groupBy { entry ->
            entry.url.substringBeforeLast('/') + "/"
        }.mapValues { (_, entries) -> entries.map { it.url } }

        val existingSongs = database.webDavSongDao.getAllSnapshot()
        val diff = WebDavScanner.computeDiff(
            existing = existingSongs,
            scannedAudio = listing.audio,
            imagesByFolder = imagesByFolder,
            serverId = url,
        )

        database.withTransaction {
            if (diff.toDeletePaths.isNotEmpty()) {
                database.webDavSongDao.deleteByPaths(diff.toDeletePaths)
            }
            if (diff.toInsert.isNotEmpty()) {
                database.webDavSongDao.insertAll(diff.toInsert)
            }
            if (diff.toUpdate.isNotEmpty()) {
                database.webDavSongDao.upsertAll(diff.toUpdate)
            }
        }

        WebDavScanner.updateScanState(
            WebDavScanState.Done(
                count = listing.audio.size,
                timestamp = System.currentTimeMillis(),
                hitHardCap = hitHardCap,
            ),
        )
        val isForce = inputData.getBoolean("force", false)
        WebDavScanner.recordLastScanTime(context)
        WebDavTagScanner.startTagScan(context, resetArt = isForce)

        Result.success()
    }
}
