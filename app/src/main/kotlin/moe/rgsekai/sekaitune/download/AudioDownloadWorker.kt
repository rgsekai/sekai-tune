package moe.rgsekai.sekaitune.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.AudioQuality
import moe.rgsekai.sekaitune.ui.utils.resize
import moe.rgsekai.sekaitune.utils.YTPlayerUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File

class AudioDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val httpClient = OkHttpClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString("SONG_ID") ?: return@withContext Result.failure()
        val songTitle = inputData.getString("SONG_TITLE") ?: "Unknown Title"
        val songArtist = inputData.getString("SONG_ARTIST") ?: "Unknown Artist"
        val currentSongNumber = inputData.getInt("CURRENT_SONG_NUMBER", 1)
        val totalSongs = inputData.getInt("TOTAL_SONGS", 1)

        val tempDir = File(applicationContext.cacheDir, "downloads").apply {
            if (!exists()) mkdirs()
        }

        val pausedStore = PausedDeviceDownloadStore(applicationContext)

        // --- NOTIFICATION SETUP ---
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sekai_tune_downloads"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = Math.abs(songId.hashCode())
        val pauseIntent = SaveToDeviceActionReceiver.createPausePendingIntent(
            applicationContext,
            songId,
            songTitle,
            songArtist,
            notificationId
        )
        val cancelIntent = SaveToDeviceActionReceiver.createCancelPendingIntent(
            applicationContext,
            songId,
            notificationId
        )

        val notificationTitle = if (totalSongs > 1) {
            "[$currentSongNumber/$totalSongs] Downloading: $songTitle"
        } else {
            "Downloading: $songTitle"
        }

        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(notificationTitle)
            .setContentText("Resolving audio stream...")
            .setSmallIcon(R.drawable.download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .addAction(R.drawable.pause, "Pause", pauseIntent)
            .addAction(R.drawable.close, "Cancel", cancelIntent)

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notificationBuilder.build(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notificationBuilder.build())
        }

        try {
            setForeground(foregroundInfo)
        } catch (e: Exception) {
            Timber.w(e, "Foreground service blocked by system, running silently.")
        }

        val sharedPrefs = applicationContext.getSharedPreferences("sekai_tune_prefs", Context.MODE_PRIVATE)
        val userChosenFormat = sharedPrefs.getString("audio_format", "mp3")?.lowercase() ?: "mp3"

        var tempSourceFile: File? = null
        var tempArtworkFile: File? = null
        var croppedCoverFile: File? = null
        var tempOutputFile: File? = null

        try {
            val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: error("No connectivity manager available")

            // 1. Resolve stream URL via InnerTube client
            setProgress(
                androidx.work.workDataOf(
                    "PROGRESS" to 0,
                    "STAGE" to "resolving",
                    "SONG_ID" to songId,
                    "SONG_TITLE" to songTitle,
                    "SONG_ARTIST" to songArtist,
                )
            )

            val playbackData = YTPlayerUtils.playerResponseForPlayback(
                videoId = songId,
                audioQuality = AudioQuality.HIGH,
                connectivityManager = connectivityManager,
            ).getOrThrow()

            if (isStopped) {
                Timber.d("Worker stopped during resolution for songId=$songId")
                return@withContext Result.failure()
            }

            tempSourceFile = PausedDeviceDownloadStore.getPartFile(applicationContext, songId)
            tempArtworkFile = File.createTempFile("dl_art_${songId}_", ".jpg", tempDir)
            croppedCoverFile = File.createTempFile("dl_crop_${songId}_", ".jpg", tempDir)
            tempOutputFile = File.createTempFile("dl_out_${songId}_", ".$userChosenFormat", tempDir)

            // 2. Download audio stream (resumable)
            notificationBuilder.setContentText("Downloading audio stream...")
            notificationBuilder.setProgress(100, 0, false)
            try { notificationManager.notify(notificationId, notificationBuilder.build()) } catch (_: Exception) {}

            var lastProgress = -1
            val startTime = System.currentTimeMillis()
            downloadAudioStream(
                songId = songId,
                songTitle = songTitle,
                songArtist = songArtist,
                userChosenFormat = userChosenFormat,
                playbackData = playbackData,
                destFile = tempSourceFile,
                pausedStore = pausedStore,
            ) { percent, bytesWritten, totalBytes ->
                if (percent != lastProgress) {
                    lastProgress = percent
                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                    val bytesPerSec = if (elapsedSec > 0) bytesWritten / elapsedSec else 0.0
                    val remainingBytes = (totalBytes - bytesWritten).coerceAtLeast(0)
                    val etaSec = if (bytesPerSec > 0) (remainingBytes / bytesPerSec).toLong() else 0L

                    notificationBuilder.setProgress(100, percent, false)
                    notificationBuilder.setContentText("Downloading... $percent% (ETA: ${etaSec}s)")
                    try { notificationManager.notify(notificationId, notificationBuilder.build()) } catch (_: Exception) {}

                    setProgressAsync(
                        androidx.work.workDataOf(
                            "PROGRESS" to percent,
                            "STAGE" to "downloading",
                            "BYTES_WRITTEN" to bytesWritten,
                            "TOTAL_BYTES" to totalBytes,
                            "SONG_ID" to songId,
                            "SONG_TITLE" to songTitle,
                            "SONG_ARTIST" to songArtist,
                        )
                    )
                }
            }

            if (isStopped) {
                Timber.d("Worker stopped during downloadAudioStream for songId=$songId")
                return@withContext Result.failure()
            }

            // 3. Resolve & download artwork, then native 1:1 center crop
            notificationBuilder.setProgress(0, 0, true)
            notificationBuilder.setContentText("Processing album artwork...")
            try { notificationManager.notify(notificationId, notificationBuilder.build()) } catch (_: Exception) {}

            setProgress(
                androidx.work.workDataOf(
                    "PROGRESS" to 85,
                    "STAGE" to "artwork",
                    "SONG_ID" to songId,
                    "SONG_TITLE" to songTitle,
                    "SONG_ARTIST" to songArtist,
                )
            )

            val artworkDownloaded = downloadArtwork(playbackData, songId, tempArtworkFile)
            val hasCroppedCover = if (artworkDownloaded) {
                cropSquareAndCompress(tempArtworkFile, croppedCoverFile, targetSize = 1200)
            } else {
                false
            }

            if (isStopped) {
                Timber.d("Worker stopped before transcode for songId=$songId")
                return@withContext Result.failure()
            }

            // 4. FFmpeg audio transcode and metadata tagging
            notificationBuilder.setContentText("Tagging and finalizing audio...")
            try { notificationManager.notify(notificationId, notificationBuilder.build()) } catch (_: Exception) {}

            setProgress(
                androidx.work.workDataOf(
                    "PROGRESS" to 95,
                    "STAGE" to "tagging",
                    "SONG_ID" to songId,
                    "SONG_TITLE" to songTitle,
                    "SONG_ARTIST" to songArtist,
                )
            )

            val ffmpegCommand = buildFfmpegCommand(
                inputPath = tempSourceFile.absolutePath,
                outputPath = tempOutputFile.absolutePath,
                format = userChosenFormat,
                title = songTitle,
                artist = songArtist,
                album = songTitle,
                coverFile = if (hasCroppedCover) croppedCoverFile else null,
            )

            val session = FFmpegKit.execute(ffmpegCommand)
            val returnCode = session.returnCode
            if (returnCode == null || !ReturnCode.isSuccess(returnCode)) {
                error("FFmpeg transcode/tagging failed: ${session.output}")
            }

            if (!tempOutputFile.exists() || tempOutputFile.length() <= 0L) {
                error("Exported audio file is missing or empty")
            }

            // 5. Export to Android MediaStore
            exportToMediaStore(tempOutputFile, songTitle, songArtist, userChosenFormat)

            // Remove paused store entry upon complete finish
            pausedStore.remove(songId)

            notificationBuilder.setContentText("Download Complete!")
            notificationBuilder.setProgress(100, 100, false)
            notificationBuilder.setOngoing(false)
            try { notificationManager.notify(notificationId, notificationBuilder.build()) } catch (_: Exception) {}

            Result.success(
                androidx.work.workDataOf(
                    "PROGRESS" to 100,
                    "STAGE" to "completed",
                    "SONG_ID" to songId,
                    "SONG_TITLE" to songTitle,
                    "SONG_ARTIST" to songArtist,
                )
            )
        } catch (e: Exception) {
            if (isStopped) {
                Timber.d("AudioDownloadWorker cancelled/stopped for songId=$songId")
                return@withContext Result.failure()
            }
            Timber.e(e, "Fatal crash or failure in AudioDownloadWorker for songId=$songId")
            notificationBuilder.setContentText("Download Failed: ${e.message ?: "Unknown error"}")
            notificationBuilder.setProgress(0, 0, false)
            notificationBuilder.setOngoing(false)
            try { notificationManager.notify(notificationId, notificationBuilder.build()) } catch (_: Exception) {}
            Result.failure()
        } finally {
            if (!isStopped) {
                tempSourceFile?.delete()
            }
            tempArtworkFile?.delete()
            croppedCoverFile?.delete()
            tempOutputFile?.delete()
        }
    }

    private fun downloadAudioStream(
        songId: String,
        songTitle: String,
        songArtist: String,
        userChosenFormat: String,
        playbackData: YTPlayerUtils.PlaybackData,
        destFile: File,
        pausedStore: PausedDeviceDownloadStore,
        onProgress: (percent: Int, bytesWritten: Long, totalBytes: Long) -> Unit,
    ) {
        val totalLength = playbackData.format.contentLength ?: 10_000_000L
        var startOffset = if (destFile.exists()) destFile.length() else 0L

        if (startOffset >= totalLength && totalLength > 0L) {
            destFile.delete()
            startOffset = 0L
        }

        var streamResponse: okhttp3.Response? = null
        var isAppend = false

        if (startOffset > 0L) {
            val rangedUrl = "${playbackData.streamUrl}&range=$startOffset-$totalLength"
            val request = Request.Builder()
                .url(rangedUrl)
                .addHeader("Range", "bytes=$startOffset-")
                .build()
            val resp = runCatching { httpClient.newCall(request).execute() }.getOrNull()
            if (resp != null && (resp.code == 206 || resp.isSuccessful)) {
                streamResponse = resp
                isAppend = (resp.code == 206)
                if (!isAppend) {
                    destFile.delete()
                    startOffset = 0L
                }
            } else {
                resp?.close()
                Timber.w("Range request failed (HTTP ${resp?.code}), resetting .part file from 0")
                destFile.delete()
                startOffset = 0L
            }
        }

        if (streamResponse == null) {
            val rangedUrl = "${playbackData.streamUrl}&range=0-$totalLength"
            val request = Request.Builder().url(rangedUrl).build()
            val resp = httpClient.newCall(request).execute()
            if (!resp.isSuccessful) {
                resp.close()
                error("Audio stream request failed with HTTP ${resp.code}")
            }
            streamResponse = resp
            isAppend = false
        }

        streamResponse.use { response ->
            val body = response.body ?: error("No response body received from audio stream")
            val effectiveTotalBytes = if (isAppend) {
                val bodyLen = body.contentLength()
                if (bodyLen > 0) startOffset + bodyLen else totalLength
            } else {
                val bodyLen = body.contentLength()
                if (bodyLen > 0) bodyLen else totalLength
            }

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesWritten = startOffset

            java.io.FileOutputStream(destFile, isAppend).use { output ->
                val input = body.byteStream()
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    if (isStopped) {
                        output.flush()
                        val percent = if (effectiveTotalBytes > 0) ((bytesWritten * 100) / effectiveTotalBytes).toInt().coerceIn(0, 99) else 0
                        pausedStore.savePaused(
                            songId = songId,
                            title = songTitle,
                            artist = songArtist,
                            bytesWritten = bytesWritten,
                            totalBytes = effectiveTotalBytes,
                            progress = percent,
                            format = userChosenFormat,
                        )
                        Timber.d("AudioDownloadWorker isStopped=true. Paused at bytesWritten=$bytesWritten/$effectiveTotalBytes ($percent%)")
                        return
                    }
                    output.write(buffer, 0, read)
                    bytesWritten += read
                    val percent = if (effectiveTotalBytes > 0) ((bytesWritten * 100) / effectiveTotalBytes).toInt().coerceIn(0, 99) else 0
                    onProgress(percent, bytesWritten, effectiveTotalBytes)
                }
                output.flush()
            }

            if (isStopped) return

            if (effectiveTotalBytes > 0 && bytesWritten < effectiveTotalBytes) {
                error("Incomplete download: wrote $bytesWritten of $effectiveTotalBytes bytes")
            }
        }
    }

    private fun downloadArtwork(playbackData: YTPlayerUtils.PlaybackData, songId: String, destFile: File): Boolean {
        val videoThumbnails = playbackData.videoDetails?.thumbnail?.thumbnails
        val bestThumb = videoThumbnails?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url

        val candidateUrls = mutableListOf<String>()
        if (!bestThumb.isNullOrBlank()) {
            candidateUrls.add(bestThumb.resize(1200, 1200))
            candidateUrls.add(bestThumb)
        }
        candidateUrls.add("https://i.ytimg.com/vi/$songId/maxresdefault.jpg")
        candidateUrls.add("https://i.ytimg.com/vi/$songId/sddefault.jpg")
        candidateUrls.add("https://i.ytimg.com/vi/$songId/hqdefault.jpg")

        for (url in candidateUrls.distinct()) {
            val downloaded = runCatching {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val body = response.body ?: return@use false
                    body.byteStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }
                    destFile.exists() && destFile.length() > 0L
                }
            }.getOrDefault(false)

            if (downloaded) return true
        }
        return false
    }

    private fun cropSquareAndCompress(sourceFile: File, outputFile: File, targetSize: Int = 1200): Boolean {
        return runCatching {
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return false
            val sourceMinDim = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - sourceMinDim) / 2
            val y = (bitmap.height - sourceMinDim) / 2
            val cropped = Bitmap.createBitmap(bitmap, x, y, sourceMinDim, sourceMinDim)

            // Only scale down if source is larger than targetSize; do NOT upscale past source resolution
            val finalSize = minOf(sourceMinDim, targetSize)
            val scaled = if (cropped.width != finalSize || cropped.height != finalSize) {
                Bitmap.createScaledBitmap(cropped, finalSize, finalSize, true).also {
                    if (it != cropped) cropped.recycle()
                }
            } else {
                cropped
            }

            outputFile.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
            }
            scaled.recycle()
            bitmap.recycle()
            outputFile.exists() && outputFile.length() > 0L
        }.getOrDefault(false)
    }

    private fun buildMetadataBlockPictureBase64(imageFile: File): String? {
        return runCatching {
            val imageBytes = imageFile.readBytes()
            if (imageBytes.isEmpty()) return null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile.absolutePath, options)
            val width = if (options.outWidth > 0) options.outWidth else 1200
            val height = if (options.outHeight > 0) options.outHeight else 1200

            val mime = "image/jpeg"
            val mimeBytes = mime.toByteArray(Charsets.US_ASCII)
            val descriptionBytes = ByteArray(0)

            val buffer = java.nio.ByteBuffer.allocate(32 + mimeBytes.size + descriptionBytes.size + imageBytes.size)
            buffer.order(java.nio.ByteOrder.BIG_ENDIAN)

            // 1. Picture type: 3 = Cover (front)
            buffer.putInt(3)
            // 2. MIME type length & MIME string
            buffer.putInt(mimeBytes.size)
            buffer.put(mimeBytes)
            // 3. Description length & Description
            buffer.putInt(descriptionBytes.size)
            buffer.put(descriptionBytes)
            // 4. Width, Height, Color depth (24), Number of indexed colors (0)
            buffer.putInt(width)
            buffer.putInt(height)
            buffer.putInt(24)
            buffer.putInt(0)
            // 5. Picture data length & binary image bytes
            buffer.putInt(imageBytes.size)
            buffer.put(imageBytes)

            android.util.Base64.encodeToString(buffer.array(), android.util.Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun buildFfmpegCommand(
        inputPath: String,
        outputPath: String,
        format: String,
        title: String,
        artist: String,
        album: String,
        coverFile: File?,
    ): String {
        val escapedInput = inputPath.ffmpegEscape()
        val escapedOutput = outputPath.ffmpegEscape()
        val titleMeta = title.ffmpegEscape()
        val artistMeta = artist.ffmpegEscape()
        val albumMeta = album.ffmpegEscape()

        if (format.lowercase() == "opus") {
            val metadataBlock = coverFile?.let { buildMetadataBlockPictureBase64(it) }
            val pictureMetaFlag = if (!metadataBlock.isNullOrBlank()) {
                " -metadata METADATA_BLOCK_PICTURE='${metadataBlock.ffmpegEscape()}'"
            } else {
                ""
            }
            return "-y -i '$escapedInput' -c:a libopus -b:a 160k -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$pictureMetaFlag '$escapedOutput'"
        }

        val codecArgs = when (format.lowercase()) {
            "flac" -> "-c:a flac"
            "m4a" -> "-c:a aac -b:a 256k"
            else -> "-c:a libmp3lame -b:a 320k -id3v2_version 3" // default mp3
        }

        return if (coverFile != null && coverFile.exists()) {
            val escapedCover = coverFile.absolutePath.ffmpegEscape()
            "-y -i '$escapedInput' -i '$escapedCover' -map 0:a -map 1:v -c:v mjpeg -disposition:v attached_pic $codecArgs -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta' '$escapedOutput'"
        } else {
            "-y -i '$escapedInput' $codecArgs -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta' '$escapedOutput'"
        }
    }

    private fun String.ffmpegEscape(): String = replace("'", "'\\''")

    private fun exportToMediaStore(fileToExport: File, title: String, artist: String, extension: String) {
        val resolver = context.contentResolver
        val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val safeArtist = artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val fileName = "$safeTitle - $safeArtist.$extension"

        val mimeType = when (extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "opus" -> "audio/ogg"
            else -> "audio/mp4"
        }

        val songDetails = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Sekai Tune")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val finalUri = resolver.insert(audioCollection, songDetails)
        if (finalUri != null) {
            resolver.openOutputStream(finalUri)?.use { outputStream ->
                fileToExport.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                songDetails.clear()
                songDetails.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(finalUri, songDetails, null, null)
            }
        }
    }
}