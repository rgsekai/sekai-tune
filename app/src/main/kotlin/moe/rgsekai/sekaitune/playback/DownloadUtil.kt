/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadProgress
import androidx.media3.exoplayer.offline.DownloaderFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.constants.AudioQuality
import moe.rgsekai.sekaitune.constants.AudioQualityKey
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.FormatEntity
import moe.rgsekai.sekaitune.db.entities.SongEntity
import moe.rgsekai.sekaitune.di.DownloadCache
import moe.rgsekai.sekaitune.di.PlayerCache
import moe.rgsekai.sekaitune.downloads.DownloadedArtworkRepository
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.playback.stream.AudioStreamRequest
import moe.rgsekai.sekaitune.playback.stream.ResolveAudioStreamUseCase
import moe.rgsekai.sekaitune.playback.stream.ResolvedAudioStream
import moe.rgsekai.sekaitune.playback.stream.StreamPurpose
import moe.rgsekai.sekaitune.utils.StreamClientUtils
import moe.rgsekai.sekaitune.utils.YTPlayerUtils
import moe.rgsekai.sekaitune.utils.enumPreference
import moe.rgsekai.sekaitune.utils.isLowDataModeActive
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.EOFException
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        val database: MusicDatabase,
        val databaseProvider: DatabaseProvider,
        @DownloadCache val downloadCache: Cache,
        @PlayerCache val playerCache: Cache,
        private val downloadedArtworkRepository: DownloadedArtworkRepository,
        private val resolveAudioStream: ResolveAudioStreamUseCase,
    ) {
        private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
        private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val downloadExecutor = Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS)
        private val artworkJobs = mutableMapOf<String, Job>()
        private val downloadPreloadLock = Any()
        private val playbackCacheReuseIds = ConcurrentHashMap.newKeySet<String>()
        private val persistedMetadata = ConcurrentHashMap<String, DownloadMetadataSignature>()

        private var downloadPreloadTargetId: String? = null
        private var downloadPreloadJob: Job? = null

        private val mediaOkHttpClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .proxy(YouTube.streamOkHttpProxy)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .dispatcher(
                    okhttp3.Dispatcher().apply {
                        maxRequests = MAX_DOWNLOAD_HTTP_REQUESTS
                        maxRequestsPerHost = MAX_DOWNLOAD_HTTP_REQUESTS
                    },
                ).connectionPool(
                    ConnectionPool(
                        MAX_IDLE_DOWNLOAD_CONNECTIONS,
                        DOWNLOAD_CONNECTION_KEEP_ALIVE_MINUTES,
                        TimeUnit.MINUTES,
                    ),
                ).addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                    val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
                    val requestBuilder = request.newBuilder()
                    if (request.header("User-Agent") == null) {
                        requestBuilder.header("User-Agent", requestProfile.userAgent)
                    }
                    if (request.header("Origin") == null && requestProfile.origin != null) {
                        requestBuilder.header("Origin", requestProfile.origin)
                    }
                    if (request.header("Referer") == null && requestProfile.referer != null) {
                        requestBuilder.header("Referer", requestProfile.referer)
                    }
                    chain.proceed(requestBuilder.build())
                }.build()
        }

        val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

        private val resolvedNetworkDataSourceFactory =
            ResolvingDataSource.Factory(
                OkHttpDataSource.Factory(mediaOkHttpClient),
                ::resolveDownloadDataSpec,
            )

        private val invalidatingNetworkDataSourceFactory =
            DataSource.Factory {
                InvalidatingDataSource(
                    upstream = resolvedNetworkDataSourceFactory.createDataSource(),
                    onFailure = ::invalidateResolvedStream,
                )
            }

        private val playbackCacheDataSourceFactory =
            CacheDataSource
                .Factory()
                .setCache(playerCache)
                .setUpstreamDataSourceFactory(invalidatingNetworkDataSourceFactory)
                .setCacheWriteDataSinkFactory(null)
                .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

        private val downloadUpstreamDataSourceFactory =
            DataSource.Factory {
                CompletePlaybackCacheDataSource(
                    downloadCache = downloadCache,
                    playerCache = playerCache,
                    playbackCacheDataSourceFactory = playbackCacheDataSourceFactory,
                    networkDataSourceFactory = invalidatingNetworkDataSourceFactory,
                    playbackCacheReuseIds = playbackCacheReuseIds,
                )
            }

        val downloadNotificationHelper =
            DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

        private val downloaderFactory =
            DefaultDownloaderFactory(
                CacheDataSource
                    .Factory()
                    .setCache(downloadCache)
                    .setUpstreamDataSourceFactory(downloadUpstreamDataSourceFactory)
                    .setCacheWriteDataSinkFactory(
                        CacheDataSink.Factory()
                            .setCache(downloadCache)
                            .setBufferSize(DOWNLOAD_WRITE_BUFFER_SIZE),
                    ).setFlags(FLAG_IGNORE_CACHE_ON_ERROR),
                downloadExecutor,
            )

        val downloadManager: DownloadManager =
            DownloadManager(
                context,
                DefaultDownloadIndex(databaseProvider),
                DownloaderFactory { request ->
                    ResumingDownloader(request, downloaderFactory, ::resetDownloadContent)
                },
            ).apply {
                maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
                minRetryCount = DOWNLOAD_MIN_RETRY_COUNT
                addListener(
                    object : DownloadManager.Listener {
                        override fun onInitialized(downloadManager: DownloadManager) {
                            refreshActiveDownloadSnapshots()
                            updateNextDownloadPreload(downloadManager)
                        }

                        override fun onDownloadChanged(
                            downloadManager: DownloadManager,
                            download: Download,
                            finalException: Exception?,
                        ) {
                            downloads.update { map ->
                                map.toMutableMap().apply {
                                    set(download.request.id, download.toProgressSnapshot())
                                }
                            }
                            if (download.state == Download.STATE_COMPLETED) {
                                scheduleDownloadedArtwork(download.request.id)
                                playbackCacheReuseIds.remove(download.request.id)
                            } else if (download.state == Download.STATE_FAILED) {
                                playbackCacheReuseIds.remove(download.request.id)
                                invalidateResolvedStream(download.request.id)
                                Timber.tag(TAG).e(
                                    "Download failed for %s (reason=%d, bytes=%d, contentLength=%d, cause=%s)",
                                    download.request.id,
                                    download.failureReason,
                                    download.bytesDownloaded,
                                    download.contentLength,
                                    finalException.sanitizedCauseChain(),
                                )
                            }
                            updateNextDownloadPreload(downloadManager)
                        }

                        override fun onDownloadRemoved(
                            downloadManager: DownloadManager,
                            download: Download,
                        ) {
                            downloads.update { map -> map - download.request.id }
                            cancelDownloadedArtworkJob(download.request.id)
                            playbackCacheReuseIds.remove(download.request.id)
                            persistedMetadata.remove(download.request.id)
                            downloadScope.launch {
                                downloadedArtworkRepository.remove(download.request.id)
                            }
                            updateNextDownloadPreload(downloadManager)
                        }
                    },
                )
            }

        init {
            downloadScope.launch {
                val result = mutableMapOf<String, Download>()
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        result[cursor.download.request.id] = cursor.download.toProgressSnapshot()
                    }
                }
                downloads.update { current ->
                    result.apply { putAll(current) }
                }
                downloadedArtworkRepository.retainForDownloads(result.keys)
                for (download in result.values) {
                    if (download.state == Download.STATE_COMPLETED) {
                        scheduleDownloadedArtwork(download.request.id).join()
                    }
                }
            }
            downloadScope.launch {
                while (isActive) {
                    delay(DOWNLOAD_PROGRESS_REFRESH_INTERVAL_MS)
                    refreshActiveDownloadSnapshots()
                }
            }
        }

        private fun refreshActiveDownloadSnapshots() {
            val activeDownloads = downloadManager.currentDownloads
            if (activeDownloads.isEmpty()) return
            downloads.update { current ->
                current.toMutableMap().apply {
                    activeDownloads.forEach { download ->
                        set(download.request.id, download.toProgressSnapshot())
                    }
                }
            }
        }

        private fun invalidateResolvedStream(mediaId: String) =
            resolveAudioStream.invalidate(mediaId, StreamPurpose.DOWNLOAD)

        private fun resetDownloadContent(mediaId: String) {
            downloadCache.removeResource(mediaId)
            val mutations =
                ContentMetadataMutations()
                    .remove(DOWNLOAD_FORMAT_ID_METADATA_KEY)
                    .remove(DOWNLOAD_CONTENT_LENGTH_METADATA_KEY)
            ContentMetadataMutations.setContentLength(mutations, C.LENGTH_UNSET.toLong())
            ContentMetadataMutations.setRedirectedUri(mutations, null)
            downloadCache.applyContentMetadataMutations(mediaId, mutations)
            playbackCacheReuseIds.remove(mediaId)
            persistedMetadata.remove(mediaId)
        }

        private fun resolveDownloadDataSpec(dataSpec: DataSpec): DataSpec {
            val mediaId = dataSpec.key ?: throw IOException("Download has no media id")
            val request = createDownloadStreamRequest(mediaId)
            val resolved =
                try {
                    resolveAudioStream.resolveBlocking(request)
                } catch (exception: YTPlayerUtils.BotDetectionPlaybackException) {
                    throw IOException("Download stream token resolution failed", exception)
                } catch (exception: YTPlayerUtils.BadStreamPlayerResponseException) {
                    throw IOException("Download stream response was invalid", exception)
                }
            val metadata = downloadCache.getContentMetadata(mediaId)
            val previousLength =
                metadata.get(DOWNLOAD_CONTENT_LENGTH_METADATA_KEY, -1L)
                    .takeIf { it > 0L }
                    ?: ContentMetadata.getContentLength(metadata)
            val formatChanged =
                resolved.format.itag > 0 &&
                    downloadCache.getContentMetadata(mediaId).get(DOWNLOAD_FORMAT_ID_METADATA_KEY, -1L) > 0L &&
                    downloadCache.getContentMetadata(mediaId).get(DOWNLOAD_FORMAT_ID_METADATA_KEY, -1L) != resolved.format.itag.toLong()
            val resolvedContentLength = resolved.format.contentLength ?: 0L
            val lengthChanged =
                previousLength > 0L && resolvedContentLength > 0L &&
                    previousLength != resolvedContentLength
            if (formatChanged || lengthChanged) {
                throw DownloadContentChangedException()
            }
            val mutations = ContentMetadataMutations()
            if (resolved.format.itag > 0) {
                mutations.set(DOWNLOAD_FORMAT_ID_METADATA_KEY, resolved.format.itag.toLong())
            }
            if (resolvedContentLength > 0L) {
                mutations.set(DOWNLOAD_CONTENT_LENGTH_METADATA_KEY, resolvedContentLength)
            }
            downloadCache.applyContentMetadataMutations(mediaId, mutations)
            persistPlaybackMetadata(mediaId, resolved)
            return dataSpec.withResolvedStream(resolved.url, resolvedContentLength)
        }

        private fun Download.toProgressSnapshot(): Download {
            val progressSnapshot =
                DownloadProgress().apply {
                    bytesDownloaded = this@toProgressSnapshot.bytesDownloaded
                    percentDownloaded = this@toProgressSnapshot.percentDownloaded
                }
            return Download(
                request,
                state,
                startTimeMs,
                updateTimeMs,
                contentLength,
                stopReason,
                failureReason,
                progressSnapshot,
            )
        }

        fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

        private fun resolveDownloadAudioQuality(lowDataModeActive: Boolean): AudioQuality =
            if (lowDataModeActive) AudioQuality.LOW else audioQuality

        private fun createDownloadStreamRequest(mediaId: String): AudioStreamRequest {
            val lowDataModeActive = context.isLowDataModeActive()
            return AudioStreamRequest(
                mediaId = mediaId,
                quality = resolveDownloadAudioQuality(lowDataModeActive),
                networkMetered = lowDataModeActive,
                purpose = StreamPurpose.DOWNLOAD,
                authState = YouTube.currentPlaybackAuthState(),
            )
        }

        private fun updateNextDownloadPreload(downloadManager: DownloadManager) {
            val currentDownloads = downloadManager.currentDownloads
            val activeDownloadCount =
                currentDownloads.count { download ->
                    download.state == Download.STATE_DOWNLOADING ||
                        download.state == Download.STATE_RESTARTING
                }
            val nextTargetId =
                if (activeDownloadCount >= MAX_PARALLEL_DOWNLOADS) {
                    currentDownloads
                        .firstOrNull { download -> download.state == Download.STATE_QUEUED }
                        ?.request
                        ?.id
                } else {
                    null
                }
            val jobs =
                synchronized(downloadPreloadLock) {
                    if (downloadPreloadTargetId == nextTargetId) return

                    val previousJob = downloadPreloadJob
                    val nextJob =
                        nextTargetId?.let { mediaId ->
                            downloadScope.launch(start = CoroutineStart.LAZY) {
                                try {
                                    val request = createDownloadStreamRequest(mediaId)
                                    if (resolveAudioStream.peek(request) == null) {
                                        resolveAudioStream.preload(request)
                                    }
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (exception: Exception) {
                                    Timber.w(exception, "Failed to preload queued download stream for %s", mediaId)
                                } finally {
                                    val runningJob = currentCoroutineContext()[Job]
                                    synchronized(downloadPreloadLock) {
                                        if (downloadPreloadJob === runningJob) {
                                            downloadPreloadJob = null
                                        }
                                    }
                                }
                            }
                        }

                    downloadPreloadTargetId = nextTargetId
                    downloadPreloadJob = nextJob
                    previousJob to nextJob
                }
            jobs.first?.cancel()
            jobs.second?.start()
        }

        private fun persistPlaybackMetadata(
            mediaId: String,
            resolved: ResolvedAudioStream,
        ) {
            val format = resolved.format
            val contentLength = format.contentLength ?: 0L
            val resolvedCodecs =
                format.mimeType
                    .substringAfter("codecs=", "")
                    .removeSurrounding("\"")
                    .substringBefore("\"")

            val signature = DownloadMetadataSignature(
                formatId = format.itag,
                mimeType = format.mimeType.split(";")[0],
                codecs = resolvedCodecs,
                bitrate = format.bitrate,
                sampleRate = format.audioSampleRate,
                contentLength = contentLength,
                title = resolved.videoDetails?.title,
                durationSeconds = resolved.videoDetails?.lengthSeconds?.toIntOrNull(),
                thumbnailUrl = resolved.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
            )
            if (persistedMetadata.put(mediaId, signature) == signature) return
            downloadScope.launch {
                try {
                    val artworkUrls =
                        database.withTransaction {
                            val existingFormat = database.getFormatByIdBlocking(mediaId)
                            database.upsert(
                                FormatEntity(
                                    id = mediaId,
                                    itag = if (format.itag > 0) format.itag else existingFormat?.itag ?: -1,
                                    mimeType = format.mimeType.split(";")[0].ifBlank { existingFormat?.mimeType.orEmpty() },
                                    codecs = resolvedCodecs.ifBlank { existingFormat?.codecs.orEmpty() },
                                    bitrate = if (format.bitrate > 0) format.bitrate else existingFormat?.bitrate ?: 0,
                                    sampleRate = format.audioSampleRate ?: existingFormat?.sampleRate,
                                    contentLength = if (contentLength > 0L) contentLength else existingFormat?.contentLength ?: 0L,
                                    loudnessDb = resolved.audioConfig?.loudnessDb ?: existingFormat?.loudnessDb,
                                    perceptualLoudnessDb = resolved.audioConfig?.perceptualLoudnessDb ?: existingFormat?.perceptualLoudnessDb,
                                    playbackUrl = resolved.playbackTracking?.videostatsPlaybackUrl?.baseUrl ?: existingFormat?.playbackUrl,
                                ),
                            )

                            val now = LocalDateTime.now()
                            val existing = database.getSongByIdBlocking(mediaId)?.song
                            val resolvedThumbnailUrl =
                                resolved.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url?.takeIf { it.isNotBlank() }

                            val updatedSong =
                                if (existing != null) {
                                    existing.copy(
                                        thumbnailUrl = existing.thumbnailUrl?.takeIf { it.isNotBlank() } ?: resolvedThumbnailUrl,
                                        dateDownload = existing.dateDownload ?: now,
                                    )
                                } else {
                                    SongEntity(
                                        id = mediaId,
                                        title = resolved.videoDetails?.title ?: mediaId,
                                        duration = resolved.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
                                        thumbnailUrl = resolvedThumbnailUrl,
                                        dateDownload = now,
                                    )
                                }

                            database.upsert(updatedSong)
                            listOf(updatedSong.thumbnailUrl, resolvedThumbnailUrl)
                        }
                    scheduleDownloadedArtwork(mediaId, artworkUrls)
                } catch (exception: CancellationException) {
                    persistedMetadata.remove(mediaId, signature)
                    throw exception
                } catch (exception: Exception) {
                    persistedMetadata.remove(mediaId, signature)
                    Timber.w(exception, "Failed to persist download metadata")
                }
            }
        }

        private fun DataSpec.withResolvedStream(url: String, contentLength: Long): DataSpec =
            buildUpon()
                .setUri(url.toUri())
                .apply {
                    val remainingLength = contentLength - position
                    if (contentLength > 0L && remainingLength > 0L) {
                        setLength(
                            if (length == C.LENGTH_UNSET.toLong()) remainingLength else minOf(length, remainingLength),
                        )
                    }
                }
                .build()

        private fun scheduleDownloadedArtwork(
            mediaId: String,
            knownSourceUrls: Collection<String?> = emptyList(),
        ): Job =
            synchronized(artworkJobs) {
                val runningJob = artworkJobs[mediaId]
                if (runningJob?.isActive == true) return runningJob

                val job =
                    downloadScope.launch(start = CoroutineStart.LAZY) {
                        try {
                            val databaseThumbnailUrl = database.getSongByIdBlocking(mediaId)?.song?.thumbnailUrl
                            downloadedArtworkRepository.cache(
                                mediaId = mediaId,
                                sourceUrls = knownSourceUrls + databaseThumbnailUrl,
                            )
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            Timber.w(exception, "Failed to cache downloaded artwork")
                        } finally {
                            val activeJob = currentCoroutineContext()[Job]
                            synchronized(artworkJobs) {
                                if (artworkJobs[mediaId] === activeJob) {
                                    artworkJobs.remove(mediaId)
                                }
                            }
                        }
                    }
                artworkJobs[mediaId] = job
                job.start()
                job
            }

        private fun cancelDownloadedArtworkJob(mediaId: String) {
            synchronized(artworkJobs) {
                artworkJobs.remove(mediaId)?.cancel()
            }
        }

        private data class DownloadMetadataSignature(
            val formatId: Int,
            val mimeType: String,
            val codecs: String,
            val bitrate: Int,
            val sampleRate: Int?,
            val contentLength: Long,
            val title: String?,
            val durationSeconds: Int?,
            val thumbnailUrl: String?,
        )

        private class InvalidatingDataSource(
            private val upstream: DataSource,
            private val onFailure: (String) -> Unit,
        ) : DataSource {
            private var mediaId: String? = null
            private var invalidated = false
            private var bytesRemaining = C.LENGTH_UNSET.toLong()

            override fun addTransferListener(transferListener: TransferListener) {
                upstream.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                mediaId = dataSpec.key
                invalidated = false
                return try {
                    upstream.open(dataSpec).also { bytesRemaining = it }
                } catch (exception: IOException) {
                    invalidateOnce()
                    throw exception
                }
            }

            override fun read(
                buffer: ByteArray,
                offset: Int,
                length: Int,
            ): Int =
                try {
                    val bytesRead = upstream.read(buffer, offset, length)
                    if (bytesRead == C.RESULT_END_OF_INPUT && bytesRemaining > 0L) {
                        throw EOFException("Download stream ended before the requested range was complete")
                    }
                    if (bytesRead > 0 && bytesRemaining != C.LENGTH_UNSET.toLong()) {
                        bytesRemaining -= bytesRead
                    }
                    bytesRead
                } catch (exception: IOException) {
                    invalidateOnce()
                    throw exception
                }

            override fun getUri(): Uri? = upstream.uri

            override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

            override fun close() {
                try {
                    upstream.close()
                } catch (exception: IOException) {
                    invalidateOnce()
                    throw exception
                } finally {
                    mediaId = null
                    bytesRemaining = C.LENGTH_UNSET.toLong()
                }
            }

            private fun invalidateOnce() {
                if (invalidated) return
                invalidated = true
                mediaId?.let(onFailure)
            }
        }

        private class CompletePlaybackCacheDataSource(
            private val downloadCache: Cache,
            private val playerCache: Cache,
            private val playbackCacheDataSourceFactory: DataSource.Factory,
            private val networkDataSourceFactory: DataSource.Factory,
            private val playbackCacheReuseIds: MutableSet<String>,
        ) : DataSource {
            private val transferListeners = mutableListOf<TransferListener>()
            private var delegate: DataSource? = null

            override fun addTransferListener(transferListener: TransferListener) {
                transferListeners += transferListener
                delegate?.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                val mediaId = dataSpec.key
                val selectedFactory =
                    if (mediaId != null && shouldUsePlaybackCache(mediaId)) {
                        playbackCacheDataSourceFactory
                    } else {
                        networkDataSourceFactory
                    }
                return selectedFactory
                    .createDataSource()
                    .also { selected ->
                        transferListeners.forEach(selected::addTransferListener)
                        delegate = selected
                    }.open(dataSpec)
            }

            override fun read(
                buffer: ByteArray,
                offset: Int,
                length: Int,
            ): Int = checkNotNull(delegate).read(buffer, offset, length)

            override fun getUri(): Uri? = delegate?.uri

            override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

            override fun close() {
                val selected = delegate
                delegate = null
                selected?.close()
            }

            private fun shouldUsePlaybackCache(mediaId: String): Boolean {
                if (mediaId in playbackCacheReuseIds) return true
                if (downloadCache.getCachedSpans(mediaId).isNotEmpty()) return false
                val contentLength =
                    ContentMetadata
                        .getContentLength(playerCache.getContentMetadata(mediaId))
                        .takeIf { it > 0L }
                        ?: return false
                if (!playerCache.isCached(mediaId, 0L, contentLength)) return false
                playbackCacheReuseIds += mediaId
                return true
            }
        }

        private fun Throwable?.sanitizedCauseChain(): String =
            generateSequence(this) { throwable -> throwable.cause }
                .take(MAX_LOGGED_CAUSE_DEPTH)
                .joinToString(separator = " <- ") { throwable ->
                    if (throwable is HttpDataSource.InvalidResponseCodeException) {
                        "${throwable.javaClass.simpleName}(HTTP ${throwable.responseCode})"
                    } else {
                        throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name }
                    }
                }.ifBlank { "unknown" }

        companion object {
            private const val TAG = "DownloadUtil"
            private const val DOWNLOAD_FORMAT_ID_METADATA_KEY = "sekaitune_download_format_id"
            private const val DOWNLOAD_CONTENT_LENGTH_METADATA_KEY = "sekaitune_download_content_length"
            private const val MAX_PARALLEL_DOWNLOADS = 3
            private const val DOWNLOAD_MIN_RETRY_COUNT = 10
            private const val MAX_IDLE_DOWNLOAD_CONNECTIONS = 12
            private const val MAX_DOWNLOAD_HTTP_REQUESTS = MAX_PARALLEL_DOWNLOADS
            private const val DOWNLOAD_READ_TIMEOUT_SECONDS = 30L
            private const val DOWNLOAD_PROGRESS_REFRESH_INTERVAL_MS = 500L
            private const val DOWNLOAD_CONNECTION_KEEP_ALIVE_MINUTES = 5L
            private const val DOWNLOAD_WRITE_BUFFER_SIZE = 256 * 1024
            private const val MAX_LOGGED_CAUSE_DEPTH = 6
        }
    }
