package moe.rgsekai.sekaitune.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

const val TAG_SAVE_TO_DEVICE = "save_to_device"

fun createSaveToDeviceWorkRequest(
    songId: String,
    title: String,
    artist: String,
    currentSongNumber: Int = 1,
    totalSongs: Int = 1,
): OneTimeWorkRequest {
    val inputData = Data.Builder()
        .putString("SONG_ID", songId)
        .putString("SONG_TITLE", title)
        .putString("SONG_ARTIST", artist)
        .putInt("CURRENT_SONG_NUMBER", currentSongNumber)
        .putInt("TOTAL_SONGS", totalSongs)
        .build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    return OneTimeWorkRequestBuilder<AudioDownloadWorker>()
        .setInputData(inputData)
        .setConstraints(constraints)
        .addTag(TAG_SAVE_TO_DEVICE)
        .addTag("song_id:$songId")
        .addTag("title:$title")
        .addTag("artist:$artist")
        .build()
}

fun startDownload(
    context: Context,
    songId: String,
    title: String,
    artist: String
) {
    val downloadRequest = createSaveToDeviceWorkRequest(
        songId = songId,
        title = title,
        artist = artist,
    )
    WorkManager.getInstance(context).enqueue(downloadRequest)
}