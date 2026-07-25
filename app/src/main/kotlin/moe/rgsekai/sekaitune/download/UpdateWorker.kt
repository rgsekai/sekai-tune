package moe.rgsekai.sekaitune.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("UpdateWorker", "Checking for yt-dlp engine updates...")
            // Initialize if needed
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(applicationContext)
            // Perform the single update
            val status = com.yausername.youtubedl_android.YoutubeDL.getInstance().updateYoutubeDL(applicationContext)

            android.util.Log.d("UpdateWorker", "Update status: ${status?.name}")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("UpdateWorker", "Failed to update engine: ${e.message}", e)
            // Even if the update fails (e.g., no internet), we still want the downloads to TRY and run
            Result.success()
        }
    }
}
