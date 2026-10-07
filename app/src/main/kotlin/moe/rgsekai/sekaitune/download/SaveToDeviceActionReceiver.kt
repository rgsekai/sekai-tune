/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import moe.rgsekai.sekaitune.R
import timber.log.Timber

class SaveToDeviceActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val songId = intent.getStringExtra(EXTRA_SONG_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Song"
        val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist"

        val workManager = WorkManager.getInstance(context)
        val store = PausedDeviceDownloadStore(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        Timber.d("SaveToDeviceActionReceiver: action=$action, songId=$songId")

        when (action) {
            ACTION_PAUSE_DOWNLOAD -> {
                // AudioDownloadWorker will persist state and post the Paused notification on isStopped
                workManager.cancelAllWorkByTag("song_id:$songId")
            }

            ACTION_RESUME_DOWNLOAD -> {
                store.remove(songId)
                notificationManager.cancel(getPausedNotificationId(songId))
                notificationManager.cancel(getForegroundNotificationId(songId))
                startDownload(context, songId, title, artist)
            }

            ACTION_CANCEL_DOWNLOAD -> {
                workManager.cancelAllWorkByTag("song_id:$songId")
                store.remove(songId)
                val partFile = PausedDeviceDownloadStore.getPartFile(context, songId)
                if (partFile.exists()) {
                    partFile.delete()
                }
                notificationManager.cancel(getPausedNotificationId(songId))
                notificationManager.cancel(getForegroundNotificationId(songId))
            }
        }
    }

    companion object {
        const val ACTION_PAUSE_DOWNLOAD = "moe.rgsekai.sekaitune.action.PAUSE_DEVICE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "moe.rgsekai.sekaitune.action.RESUME_DEVICE_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "moe.rgsekai.sekaitune.action.CANCEL_DEVICE_DOWNLOAD"

        const val EXTRA_SONG_ID = "extra_song_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        fun getForegroundNotificationId(songId: String): Int = Math.abs(songId.hashCode())
        fun getPausedNotificationId(songId: String): Int = Math.abs(songId.hashCode()) + 100_000

        fun postPausedNotification(
            context: Context,
            songId: String,
            title: String,
            artist: String,
            progress: Int,
        ) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "sekai_tune_downloads"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
                notificationManager.createNotificationChannel(channel)
            }

            val pausedNotificationId = getPausedNotificationId(songId)
            val resumeIntent = createResumePendingIntent(context, songId, title, artist, pausedNotificationId)
            val cancelIntent = createCancelPendingIntent(context, songId, pausedNotificationId)

            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle("Paused: $title")
                .setContentText("$artist • Download paused ($progress%)")
                .setSmallIcon(R.drawable.download)
                .setProgress(100, progress, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .addAction(R.drawable.play, "Resume", resumeIntent)
                .addAction(R.drawable.close, "Cancel", cancelIntent)
                .build()

            try {
                notificationManager.notify(pausedNotificationId, notification)
            } catch (e: Exception) {
                Timber.w(e, "Failed to post paused notification")
            }
        }

        fun createPausePendingIntent(
            context: Context,
            songId: String,
            title: String,
            artist: String,
            notificationId: Int = getForegroundNotificationId(songId),
        ): PendingIntent {
            val intent = Intent(context, SaveToDeviceActionReceiver::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra(EXTRA_SONG_ID, songId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, songId.hashCode() + 1, intent, flags)
        }

        fun createResumePendingIntent(
            context: Context,
            songId: String,
            title: String,
            artist: String,
            notificationId: Int = getPausedNotificationId(songId),
        ): PendingIntent {
            val intent = Intent(context, SaveToDeviceActionReceiver::class.java).apply {
                action = ACTION_RESUME_DOWNLOAD
                putExtra(EXTRA_SONG_ID, songId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, songId.hashCode() + 2, intent, flags)
        }

        fun createCancelPendingIntent(
            context: Context,
            songId: String,
            notificationId: Int = getPausedNotificationId(songId),
        ): PendingIntent {
            val intent = Intent(context, SaveToDeviceActionReceiver::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_SONG_ID, songId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, songId.hashCode() + 3, intent, flags)
        }
    }
}
