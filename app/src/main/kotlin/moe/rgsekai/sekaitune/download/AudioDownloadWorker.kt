package moe.rgsekai.sekaitune.download

import android.content.pm.ServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.utils.YTPlayerUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class AudioDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // EVERYTHING is now inside the try-block so we can catch and log silent killers!
            val songId = inputData.getString("SONG_ID") ?: return@withContext Result.failure()
            val songTitle = inputData.getString("SONG_TITLE") ?: "Unknown Title"
            val songArtist = inputData.getString("SONG_ARTIST") ?: "Unknown Artist"

// Add these two lines:
            val currentSongNumber = inputData.getInt("CURRENT_SONG_NUMBER", 1)
            val totalSongs = inputData.getInt("TOTAL_SONGS", 1)

            val tempDir = java.io.File(applicationContext.cacheDir, "downloads")
            if (!tempDir.exists()) tempDir.mkdirs()

            // --- NOTIFICATION SETUP ---
            val notificationManager = applicationContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "sekai_tune_downloads"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(channelId, "Downloads", android.app.NotificationManager.IMPORTANCE_LOW)
                notificationManager.createNotificationChannel(channel)
            }

            // FIX 1: Ensure Notification ID is strictly positive
            val notificationId = Math.abs(songId.hashCode())

            // FIX 2: Create the WorkManager Cancel Intent
            val cancelIntent = androidx.work.WorkManager.getInstance(applicationContext)
                .createCancelPendingIntent(id)

            // Create a smart title
            val notificationTitle = if (totalSongs > 1) {
                "[$currentSongNumber/$totalSongs] Downloading: $songTitle"
            } else {
                "Downloading: $songTitle"
            }

// FIX 3: Stop using Android System icons. Using your app's native download icon!
            val notificationBuilder = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
                .setContentTitle(notificationTitle) // Using the smart title here!
                .setContentText("Starting download...")
                .setSmallIcon(moe.rgsekai.sekaitune.R.drawable.download)
                .setOngoing(true)
                .setProgress(100, 0, true)
                .addAction(0, "Cancel / Stop All", cancelIntent) // '0' means no icon for the button, keeping it extremely safe

            val foregroundInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                androidx.work.ForegroundInfo(
                    notificationId,
                    notificationBuilder.build(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                androidx.work.ForegroundInfo(notificationId, notificationBuilder.build())
            }

            try {
                setForeground(foregroundInfo)
            } catch (e: Exception) {
                // This safely catches the Android 12+ foreground crash, AND any older Android crashes,
                // allowing WorkManager to finish the download silently in the background!
                android.util.Log.w("AudioWorker", "Foreground service blocked by system, running silently.", e)
            }
            // --------------------------

            // Initialize downloader
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(applicationContext)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(applicationContext)

            // --- SMART ENGINE UPDATE ---
            try {
                val prefs = applicationContext.getSharedPreferences("sekai_tune_prefs", android.content.Context.MODE_PRIVATE)
                val lastUpdate = prefs.getLong("last_ytdlp_update", 0L)
                val currentTime = System.currentTimeMillis()

                // 86400000 milliseconds = 24 hours. It will only update ONCE per day!
                if (currentTime - lastUpdate > 86400000L) {
                    android.util.Log.d("AudioWorker", "Running daily yt-dlp update check...")
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
                    prefs.edit().putLong("last_ytdlp_update", currentTime).apply()
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioWorker", "Update check failed: ${e.message}")
            }
// ---------------------------

            val youtubeUrl = "https://music.youtube.com/watch?v=$songId"
            val request = com.yausername.youtubedl_android.YoutubeDLRequest(youtubeUrl)

            request.addOption("--extractor-args", "youtube:player_client=android,web")

            val sharedPrefs = applicationContext.getSharedPreferences("sekai_tune_prefs", android.content.Context.MODE_PRIVATE)
            val userChosenFormat = sharedPrefs.getString("audio_format", "mp3") ?: "mp3"

            request.addOption("-x")
            request.addOption("--audio-format", userChosenFormat)
            request.addOption("--write-thumbnail")
            request.addOption("--paths", tempDir.absolutePath)
            request.addOption("-o", "$songId.%(ext)s")

            android.util.Log.d("AudioWorker", "Starting yt-dlp download for $songTitle...")

            // FIX 4: Throttle Notification Updates!
            // Only updates notification when the integer percentage actually changes to prevent IPC spam blocks.
            var lastProgress = -1
            com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request) { progress: Float, etaInSeconds: Long, line: String ->
                val currentProgress = progress.toInt()
                if (currentProgress != lastProgress) {
                    lastProgress = currentProgress
                    notificationBuilder.setProgress(100, currentProgress, false)
                    notificationBuilder.setContentText("Downloading... $currentProgress% (ETA: ${etaInSeconds}s)")

                    // Safe notify ignores missing notification permissions so download doesn't abort
                    try { notificationManager.notify(notificationId, notificationBuilder.build()) } catch (e: Exception) {}
                }
            }

            notificationBuilder.setProgress(0, 0, true)
            notificationBuilder.setContentText("Processing audio and album art...")
            try { notificationManager.notify(notificationId, notificationBuilder.build()) } catch (e: Exception) {}

            val finishedFile = java.io.File(tempDir, "$songId.$userChosenFormat")
            val rawAudio = java.io.File(tempDir, "$songId.$userChosenFormat")
            val rawThumb = tempDir.listFiles()?.firstOrNull { it.name.startsWith(songId) && (it.name.endsWith(".webp") || it.name.endsWith(".jpg")) }
            val finalAudio = java.io.File(tempDir, "${songId}_final.$userChosenFormat")

            if (rawAudio.exists() && rawThumb != null && rawThumb.exists()) {
                android.util.Log.d("AudioWorker", "Starting FFmpeg crop...")
                val ffmpegCommand = "-i \"${rawAudio.absolutePath}\" -i \"${rawThumb.absolutePath}\" -map 0 -map 1 -c:a copy -c:v mjpeg -vf \"crop='min(iw,ih)':'min(iw,ih)'\" -disposition:v attached_pic \"${finalAudio.absolutePath}\""
                com.antonkarpenko.ffmpegkit.FFmpegKit.execute(ffmpegCommand)

                if (finalAudio.exists()) {
                    rawAudio.delete()
                    finalAudio.renameTo(rawAudio)
                    rawThumb.delete()
                }
            }

            if (finishedFile.exists()) {
                exportToMediaStore(finishedFile, songTitle, songArtist, userChosenFormat)
                finishedFile.delete()

                notificationBuilder.setContentText("Download Complete!")
                notificationBuilder.setProgress(100, 100, false)
                notificationBuilder.setOngoing(false)
                try { notificationManager.notify(notificationId, notificationBuilder.build()) } catch (e: Exception) {}

                Result.success()
            } else {
                android.util.Log.e("AudioWorker", "File failed to export to MediaStore.")
                Result.failure()
            }

        } catch (e: Exception) {
            // FIX 5: Now if absolutely ANYTHING crashes, it will print brightly in your Logcat!
            android.util.Log.e("AudioWorker", "FATAL CRASH in Worker: ${e.message}", e)
            Result.failure()
        }
    }
    private fun getResizedArtworkBytes(imageFile: File): ByteArray {
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return imageFile.readBytes()
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 600, 600, true)
            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            imageFile.readBytes()
        }
    }

    // --- Helper Functions ---

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

        // Dynamically assign correct MIME type based on user selection
        val mimeType = when (extension) {
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

    private suspend fun downloadFileInChunks(urlString: String, outputFile: File) =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val chunkSize = 5 * 1024 * 1024L // 5MB chunks

            val initialRequest =
                Request.Builder().url(urlString).header("Range", "bytes=0-0").build()
            val initialResponse = client.newCall(initialRequest).execute()
            val contentRange = initialResponse.header("Content-Range")
            initialResponse.close()

            val totalSize = contentRange?.substringAfter("/")?.toLongOrNull() ?: -1L

            if (totalSize <= 0) {
                downloadFileDirect(urlString, outputFile)
                return@withContext
            }

            outputFile.outputStream().use { fos ->
                var uploaded = 0L
                while (uploaded < totalSize) {
                    val end = (uploaded + chunkSize - 1).coerceAtMost(totalSize - 1)
                    val chunkRequest = Request.Builder()
                        .url(urlString)
                        .header("Range", "bytes=$uploaded-$end")
                        .build()

                    client.newCall(chunkRequest).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Chunk failed")
                        response.body?.byteStream()?.use { input -> input.copyTo(fos) }
                    }
                    uploaded = end + 1
                }
                fos.flush()
            }
        }

    private fun downloadFileDirect(urlString: String, outputFile: File) {
        val request = Request.Builder().url(urlString).build()
        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP error: ${response.code}") // This forces it to stop if YouTube sends an error
            response.body?.byteStream()?.use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}