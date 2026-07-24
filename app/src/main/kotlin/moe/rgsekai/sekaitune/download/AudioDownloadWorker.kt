package moe.rgsekai.sekaitune.download

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
        val songId = inputData.getString("SONG_ID") ?: return@withContext Result.failure()
        val songTitle = inputData.getString("SONG_TITLE") ?: "Unknown Title"
        val songArtist = inputData.getString("SONG_ARTIST") ?: "Unknown Artist"

        val tempDir = java.io.File(applicationContext.cacheDir, "downloads")
        if (!tempDir.exists()) tempDir.mkdirs()

        try {
            // 1. Initialize downloader
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(applicationContext)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(applicationContext)

            // 1.5 UPDATE YT-DLP TO THE LATEST VERSION TO BYPASS YOUTUBE'S 403 BLOCK
            android.util.Log.d("AudioWorker", "Updating yt-dlp engine...")
            com.yausername.youtubedl_android.YoutubeDL.getInstance().updateYoutubeDL(applicationContext)

            val youtubeUrl = "https://music.youtube.com/watch?v=$songId"
            val request = com.yausername.youtubedl_android.YoutubeDLRequest(youtubeUrl)

            /// Impersonate the YouTube MUSIC app to bypass 403
            request.addOption("--extractor-args", "youtube:player_client=android_music")

            // Just extract audio and download the raw thumbnail (Do NOT embed yet!)
            request.addOption("-x")
            request.addOption("--audio-format", "m4a")
            request.addOption("--write-thumbnail")

            // (Delete --embed-thumbnail, --embed-metadata, and --convert-thumbnails if they are still there)

            // Set the global workspace for temporary files and name the final output
            request.addOption("--paths", tempDir.absolutePath)
            request.addOption("-o", "$songId.%(ext)s")

            // 2. Execute with explicit callback types to clear type inference errors
            android.util.Log.d("AudioWorker", "Starting yt-dlp download...")
            com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request) { progress: Float, etaInSeconds: Long, line: String ->
                android.util.Log.d("AudioWorker", "Progress: $progress% | ETA: $etaInSeconds sec")
            }

            val finishedFile = java.io.File(tempDir, "$songId.m4a")
            // --- THE YTDLNIS WAY: NATIVE CROP AND EMBED ---
            val rawAudio = java.io.File(tempDir, "$songId.m4a")
            // Note: yt-dlp usually downloads thumbnails as .webp or .jpg, we grab whichever exists
            val rawThumb = tempDir.listFiles()?.firstOrNull { it.name.startsWith(songId) && (it.name.endsWith(".webp") || it.name.endsWith(".jpg")) }
            val finalAudio = java.io.File(tempDir, "${songId}_final.m4a")

            if (rawAudio.exists() && rawThumb != null && rawThumb.exists()) {
                android.util.Log.d("AudioWorker", "Starting Full FFmpeg native crop...")

                // The ultimate command: Take audio, take thumbnail, slice image to perfect square, convert to JPG, and fuse into M4A
                val ffmpegCommand = "-i \"${rawAudio.absolutePath}\" -i \"${rawThumb.absolutePath}\" -map 0 -map 1 -c:a copy -c:v mjpeg -vf \"crop='min(iw,ih)':'min(iw,ih)'\" -disposition:v attached_pic \"${finalAudio.absolutePath}\""

                com.antonkarpenko.ffmpegkit.FFmpegKit.execute(ffmpegCommand)

                // Overwrite the raw audio with our perfect, natively cropped file and delete temp image
                if (finalAudio.exists()) {
                    rawAudio.delete()
                    finalAudio.renameTo(rawAudio)
                    rawThumb.delete()
                }
            }
            // ----------------------------------------------
            if (finishedFile.exists()) {
                exportToMediaStore(finishedFile, songTitle, songArtist, "m4a")
                finishedFile.delete()
                Result.success()
            } else {
                Result.failure()
            }

        } catch (e: Exception) {
            android.util.Log.e("AudioWorker", "yt-dlp crashed: ${e.message}", e)
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

        val songDetails = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, if (extension == "m4a") "audio/mp4" else "audio/ogg")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, title) // <-- The magic thumbnail scanner line!
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