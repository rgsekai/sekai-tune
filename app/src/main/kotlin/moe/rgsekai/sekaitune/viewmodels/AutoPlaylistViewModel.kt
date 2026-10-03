/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.viewmodels

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.constants.AutoPlaylistSongSortDescendingKey
import moe.rgsekai.sekaitune.constants.AutoPlaylistSongSortType
import moe.rgsekai.sekaitune.constants.AutoPlaylistSongSortTypeKey
import moe.rgsekai.sekaitune.constants.HideExplicitKey
import moe.rgsekai.sekaitune.constants.SongSortType
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.Song
import moe.rgsekai.sekaitune.extensions.filterExplicit
import moe.rgsekai.sekaitune.extensions.reversed
import moe.rgsekai.sekaitune.extensions.toEnum
import moe.rgsekai.sekaitune.extensions.toMediaItem
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.playback.CatalogMatchResult
import moe.rgsekai.sekaitune.playback.DownloadUtil
import moe.rgsekai.sekaitune.playback.ResolveUploadedCatalogMatchUseCase
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadItem
import moe.rgsekai.sekaitune.ui.utils.sendAddMissingDownloads
import moe.rgsekai.sekaitune.utils.SyncUtils
import moe.rgsekai.sekaitune.utils.dataStore
import moe.rgsekai.sekaitune.utils.get
import moe.rgsekai.sekaitune.utils.reportException
import androidx.media3.common.MediaItem
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        savedStateHandle: SavedStateHandle,
        private val syncUtils: SyncUtils,
        val resolveUploadedCatalogMatchUseCase: ResolveUploadedCatalogMatchUseCase,
    ) : ViewModel() {
        val playlist = savedStateHandle.get<String>("playlist")!!

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        fun playTrack(
            songs: List<Song>,
            startIndex: Int,
            onToast: (String) -> Unit,
            onReady: (List<MediaItem>, Int) -> Unit,
        ) {
            val targetSong = songs.getOrNull(startIndex) ?: return
            if (!targetSong.song.isUploaded) {
                onReady(songs.map { it.toMediaItem() }, startIndex)
                return
            }

            viewModelScope.launch {
                when (val result = resolveUploadedCatalogMatchUseCase(targetSong.song, targetSong.artists)) {
                    is CatalogMatchResult.Success -> {
                        if (!result.isHighConfidence) {
                            onToast(context.getString(R.string.playing_matched_version))
                        }
                        val mediaItems = songs.mapIndexed { idx, s ->
                            if (idx == startIndex) {
                                s.toMediaItem(streamMediaId = result.catalogId)
                            } else {
                                s.toMediaItem()
                            }
                        }
                        onReady(mediaItems, startIndex)
                    }
                    is CatalogMatchResult.NoMatch -> {
                        onToast(context.getString(R.string.no_catalog_match_found))
                    }
                }
            }
        }

        fun playAll(
            songs: List<Song>,
            shuffle: Boolean,
            onToast: (String) -> Unit,
            onReady: (List<MediaItem>) -> Unit,
        ) {
            if (songs.isEmpty()) return
            val orderedSongs = if (shuffle) songs.shuffled() else songs
            val firstSong = orderedSongs.first()
            if (!firstSong.song.isUploaded) {
                onReady(orderedSongs.map { it.toMediaItem() })
                return
            }

            viewModelScope.launch {
                when (val result = resolveUploadedCatalogMatchUseCase(firstSong.song, firstSong.artists)) {
                    is CatalogMatchResult.Success -> {
                        if (!result.isHighConfidence) {
                            onToast(context.getString(R.string.playing_matched_version))
                        }
                        val mediaItems = orderedSongs.mapIndexed { idx, s ->
                            if (idx == 0) {
                                s.toMediaItem(streamMediaId = result.catalogId)
                            } else {
                                s.toMediaItem()
                            }
                        }
                        onReady(mediaItems)
                    }
                    is CatalogMatchResult.NoMatch -> {
                        onToast(context.getString(R.string.no_catalog_match_found))
                    }
                }
            }
        }

        fun downloadPlaylist(
            songs: List<Song>,
            downloads: Map<String, Download>,
            onToast: (String) -> Unit,
        ) {
            viewModelScope.launch {
                var hasMediumConfidence = false
                var failedCount = 0
                val downloadItems = mutableListOf<HeaderDownloadItem>()

                for (song in songs) {
                    if (song.song.isUploaded) {
                        when (val result = resolveUploadedCatalogMatchUseCase(song.song, song.artists)) {
                            is CatalogMatchResult.Success -> {
                                if (!result.isHighConfidence) {
                                    hasMediumConfidence = true
                                }
                                downloadItems.add(
                                    HeaderDownloadItem(
                                        id = result.catalogId,
                                        title = song.song.title,
                                    ),
                                )
                            }
                            is CatalogMatchResult.NoMatch -> {
                                failedCount++
                            }
                        }
                    } else {
                        downloadItems.add(
                            HeaderDownloadItem(
                                id = song.song.id,
                                title = song.song.title,
                            ),
                        )
                    }
                }

                if (hasMediumConfidence) {
                    onToast(context.getString(R.string.downloading_matched_version))
                }
                if (failedCount > 0 && downloadItems.isEmpty()) {
                    onToast(context.getString(R.string.no_catalog_match_found))
                }

                if (downloadItems.isNotEmpty()) {
                    sendAddMissingDownloads(
                        context = context,
                        songs = downloadItems,
                        downloads = downloads,
                    )
                }
            }
        }

        private var uploadJob: Job? = null

        private val _isUploading = MutableStateFlow(false)
        val isUploading = _isUploading.asStateFlow()

        private val _uploadEvent = MutableSharedFlow<UploadEvent>()
        val uploadEvent = _uploadEvent.asSharedFlow()

        private val _uploadProgress = MutableStateFlow<List<UploadProgressItem>>(emptyList())
        val uploadProgress = _uploadProgress.asStateFlow()

        private fun AutoPlaylistSongSortType.toSongSortType(): SongSortType =
            when (this) {
                AutoPlaylistSongSortType.CREATE_DATE -> SongSortType.CREATE_DATE
                AutoPlaylistSongSortType.NAME -> SongSortType.NAME
                AutoPlaylistSongSortType.ARTIST -> SongSortType.ARTIST
                AutoPlaylistSongSortType.PLAY_TIME -> SongSortType.PLAY_TIME
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        val likedSongs =
            context.dataStore.data
                .map {
                    Pair(
                        it[AutoPlaylistSongSortTypeKey].toEnum(AutoPlaylistSongSortType.CREATE_DATE) to (
                            it[AutoPlaylistSongSortDescendingKey]
                                ?: true
                        ),
                        it[HideExplicitKey] ?: false,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (sortDesc, hideExplicit) ->
                    val (sortType, descending) = sortDesc
                    val songSortType = sortType.toSongSortType()
                    when (playlist) {
                        "liked" -> {
                            database.likedSongs(songSortType, descending).map { it.filterExplicit(hideExplicit) }
                        }

                        "uploaded" -> {
                            database.uploadedSongs(songSortType, descending).map { it.filterExplicit(hideExplicit) }
                        }

                        "downloaded" -> {
                            downloadUtil.downloads.flatMapLatest { downloads ->
                                database
                                    .allSongs()
                                    .flowOn(Dispatchers.IO)
                                    .map { songs ->
                                        songs.filter {
                                            val key = it.song.matchedCatalogId ?: it.id
                                            downloads[key]?.state == Download.STATE_COMPLETED || downloads[it.id]?.state == Download.STATE_COMPLETED
                                        }
                                    }.map { songs ->
                                        when (songSortType) {
                                            SongSortType.CREATE_DATE -> {
                                                songs.sortedBy {
                                                    val key = it.song.matchedCatalogId ?: it.id
                                                    downloads[key]?.updateTimeMs ?: downloads[it.id]?.updateTimeMs ?: 0L
                                                }
                                            }


                                            SongSortType.NAME -> {
                                                songs.sortedBy { it.song.title }
                                            }

                                            SongSortType.ARTIST -> {
                                                songs.sortedBy { song ->
                                                    song.artists.joinToString(separator = "") { artist -> artist.name }
                                                }
                                            }

                                            SongSortType.PLAY_TIME -> {
                                                songs.sortedBy { it.song.totalPlayTime }
                                            }
                                        }.reversed(descending).filterExplicit(hideExplicit)
                                    }
                            }
                        }

                        else -> {
                            MutableStateFlow(emptyList())
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        fun launchUploadPicker() {
            viewModelScope.launch {
                _uploadEvent.emit(UploadEvent.LaunchFilePicker)
            }
        }

        fun onFilesSelected(uris: List<Uri>) {
            if (uris.isEmpty()) return
            android.util.Log.w("UploadTracker", "onFilesSelected: ${uris.size} uris")
            System.err.println("UploadTracker: onFilesSelected: ${uris.size} uris")

            val newItems = uris.map { uri ->
                val name = getFileName(context, uri) ?: "audio_${System.currentTimeMillis()}"
                val size = getFileSize(context, uri)
                UploadProgressItem(
                    uri = uri,
                    filename = name,
                    totalBytes = size,
                    state = UploadState.Queued,
                )
            }

            _uploadProgress.value = _uploadProgress.value + newItems
            processUploadQueue()
        }

        fun pauseUpload(item: UploadProgressItem) {
            android.util.Log.w("UploadTracker", "pauseUpload requested for ${item.filename}")
            System.err.println("UploadTracker: pauseUpload requested for ${item.filename}")
            val current = _uploadProgress.value.find { it.uri == item.uri } ?: return
            if (current.state == UploadState.Uploading) {
                // Mark paused before cancelling job so cancellation handler knows it was user pause
                updateUploadItem(current.copy(state = UploadState.Paused))
                uploadJob?.cancel()
            } else if (current.state == UploadState.Queued) {
                updateUploadItem(current.copy(state = UploadState.Paused))
            }
        }

        fun resumeUpload(item: UploadProgressItem) {
            android.util.Log.w("UploadTracker", "resumeUpload requested for ${item.filename} (offset=${item.uploadedBytes})")
            System.err.println("UploadTracker: resumeUpload requested for ${item.filename} (offset=${item.uploadedBytes})")
            val current = _uploadProgress.value.find { it.uri == item.uri } ?: return
            updateUploadItem(
                current.copy(
                    state = UploadState.Queued,
                    errorMessage = null,
                )
            )
            processUploadQueue()
        }

        fun cancelUpload(item: UploadProgressItem) {
            android.util.Log.w("UploadTracker", "cancelUpload requested for ${item.filename}")
            System.err.println("UploadTracker: cancelUpload requested for ${item.filename}")
            val current = _uploadProgress.value.find { it.uri == item.uri }
            if (current != null && current.state == UploadState.Uploading) {
                _uploadProgress.value = _uploadProgress.value.filterNot { it.uri == item.uri }
                uploadJob?.cancel()
            } else {
                _uploadProgress.value = _uploadProgress.value.filterNot { it.uri == item.uri }
            }
            if (_uploadProgress.value.isEmpty()) {
                _isUploading.value = false
            } else {
                processUploadQueue()
            }
        }

        fun cancelUploads() {
            uploadJob?.cancel()
            uploadJob = null
            _isUploading.value = false
            _uploadProgress.value = emptyList()
        }

        private fun updateUploadItem(updated: UploadProgressItem) {
            _uploadProgress.value = _uploadProgress.value.map { item ->
                if (item.uri == updated.uri) updated else item
            }
        }

        private fun processUploadQueue() {
            if (uploadJob?.isActive == true) return

            uploadJob = viewModelScope.launch(Dispatchers.IO) {
                _isUploading.value = true
                var successCount = 0

                while (true) {
                    val nextItem = _uploadProgress.value.firstOrNull { it.state == UploadState.Queued }
                        ?: break

                    val ext = nextItem.filename.substringAfterLast('.', "").lowercase()
                    if (ext !in YouTube.SUPPORTED_UPLOAD_EXTENSIONS) {
                        android.util.Log.w("UploadTracker", "Unsupported format: ${nextItem.filename}")
                        updateUploadItem(
                            nextItem.copy(
                                state = UploadState.Failed,
                                errorMessage = "Unsupported format (.$ext)",
                            )
                        )
                        _uploadEvent.emit(UploadEvent.ShowToast("Unsupported format: ${nextItem.filename}"))
                        continue
                    }

                    if (nextItem.totalBytes <= 0L) {
                        android.util.Log.w("UploadTracker", "Invalid or empty file: ${nextItem.filename}")
                        updateUploadItem(
                            nextItem.copy(
                                state = UploadState.Failed,
                                errorMessage = "Invalid or empty file",
                            )
                        )
                        _uploadEvent.emit(UploadEvent.ShowToast("Invalid file: ${nextItem.filename}"))
                        continue
                    }

                    if (nextItem.totalBytes >= YouTube.MAX_UPLOAD_SIZE) {
                        android.util.Log.w("UploadTracker", "File too large: ${nextItem.filename}")
                        updateUploadItem(
                            nextItem.copy(
                                state = UploadState.Failed,
                                errorMessage = "File exceeds 300MB limit",
                            )
                        )
                        _uploadEvent.emit(UploadEvent.ShowToast("File too large (>300MB): ${nextItem.filename}"))
                        continue
                    }

                    updateUploadItem(nextItem.copy(state = UploadState.Uploading))

                    try {
                        val result = YouTube.uploadSong(
                            filename = nextItem.filename,
                            contentLength = nextItem.totalBytes,
                            content = {
                                context.contentResolver.openInputStream(nextItem.uri)
                                    ?: error("Unable to open stream for ${nextItem.filename}")
                            },
                            existingUploadUrl = nextItem.uploadUrl,
                            initialOffset = nextItem.uploadedBytes,
                            onSessionCreated = { url ->
                                val current = _uploadProgress.value.find { it.uri == nextItem.uri }
                                if (current != null) {
                                    updateUploadItem(current.copy(uploadUrl = url))
                                }
                            },
                            onProgress = { p, bytes ->
                                val current = _uploadProgress.value.find { it.uri == nextItem.uri }
                                if (current != null && current.state == UploadState.Uploading) {
                                    updateUploadItem(current.copy(progress = p, uploadedBytes = bytes))
                                }
                            },
                        )

                        result.fold(
                            onSuccess = {
                                successCount++
                                android.util.Log.w("UploadTracker", "Successfully uploaded ${nextItem.filename}")
                                System.err.println("UploadTracker: Successfully uploaded ${nextItem.filename}")
                                updateUploadItem(
                                    nextItem.copy(
                                        state = UploadState.Success,
                                        progress = 1f,
                                        uploadedBytes = nextItem.totalBytes,
                                    )
                                )
                            },
                            onFailure = { error ->
                                val msg = error.localizedMessage ?: "Upload failed"
                                android.util.Log.e("UploadTracker", "Upload failed for ${nextItem.filename}: $msg", error)
                                System.err.println("UploadTracker: Upload failed for ${nextItem.filename}: $msg")
                                updateUploadItem(
                                    nextItem.copy(
                                        state = UploadState.Failed,
                                        errorMessage = msg,
                                    )
                                )
                                _uploadEvent.emit(UploadEvent.ShowToast("Failed to upload ${nextItem.filename}: $msg"))
                            },
                        )
                    } catch (e: CancellationException) {
                        val current = _uploadProgress.value.find { it.uri == nextItem.uri }
                        if (current != null && current.state != UploadState.Paused) {
                            android.util.Log.w("UploadTracker", "Upload cancelled for ${nextItem.filename}")
                        }
                        // Stop queue iteration on cancellation
                        break
                    } catch (e: Exception) {
                        val msg = e.localizedMessage ?: "Upload error"
                        android.util.Log.e("UploadTracker", "Error uploading ${nextItem.filename}: $msg", e)
                        updateUploadItem(
                            nextItem.copy(
                                state = UploadState.Failed,
                                errorMessage = msg,
                            )
                        )
                        _uploadEvent.emit(UploadEvent.ShowToast("Error uploading ${nextItem.filename}: $msg"))
                    }
                }

                _isUploading.value = false

                if (successCount > 0) {
                    android.util.Log.w("UploadTracker", "Batch completed with $successCount successes. Syncing library...")
                    System.err.println("UploadTracker: Batch completed with $successCount successes. Syncing library...")
                    _uploadEvent.emit(UploadEvent.ShowToast("Successfully uploaded $successCount song(s)"))
                    viewModelScope.launch(Dispatchers.IO) {
                        delay(1000L)
                        syncUtils.syncUploadedSongs()
                    }
                }

                // Remove successfully uploaded items after short confirmation delay while keeping failed / paused items
                delay(1200L)
                val remaining = _uploadProgress.value.filterNot { it.state == UploadState.Success }
                _uploadProgress.value = remaining
            }
        }

        fun refresh() {
            if (_isRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                _isRefreshing.value = true
                try {
                    when (playlist) {
                        "liked" -> syncUtils.syncLikedSongs()
                        "uploaded" -> syncUtils.syncUploadedSongs()
                        else -> Unit
                    }
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        fun syncLikedSongs() {
            refresh()
        }

        fun refreshUploadedSongs() {
            refresh()
        }

        private fun getFileName(context: Context, uri: Uri): String? {
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) {
                                return cursor.getString(idx)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Error querying display name")
                }
            }
            return uri.lastPathSegment?.substringAfterLast('/')
        }

        private fun getFileSize(context: Context, uri: Uri): Long {
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.SIZE),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (idx != -1 && !cursor.isNull(idx)) {
                                return cursor.getLong(idx)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Error querying file size")
                }
            }
            return try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }

sealed class UploadEvent {
    data object LaunchFilePicker : UploadEvent()
    data class ShowToast(val message: String) : UploadEvent()
}

enum class UploadState {
    Queued,
    Uploading,
    Paused,
    Success,
    Failed,
}

data class UploadProgressItem(
    val uri: Uri,
    val filename: String,
    val totalBytes: Long,
    val uploadedBytes: Long = 0L,
    val progress: Float = 0f,
    val state: UploadState = UploadState.Queued,
    val errorMessage: String? = null,
    val uploadUrl: String? = null,
)
