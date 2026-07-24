package moe.rgsekai.sekaitune.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

fun startDownload(
    context: Context,
    songId: String,
    title: String,
    artist: String
) {
    // 1. Pass song info to the worker
    val inputData = Data.Builder()
        .putString("SONG_ID", songId)
        .putString("SONG_TITLE", title)
        .putString("SONG_ARTIST", artist)
        .build()

    // 2. Require internet connection
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // 3. Create the download request
    val downloadRequest = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
        .setInputData(inputData)
        .setConstraints(constraints)
        .build()

    // 4. Send it to WorkManager to run in the background
    WorkManager.getInstance(context).enqueue(downloadRequest)
}