/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import moe.rgsekai.sekaitune.R

@UnstableApi
class SekaiTuneMediaNotificationProvider(
    private val context: Context,
    @DrawableRes smallIconResId: Int,
) : MediaNotification.Provider {
    private val delegate =
        DefaultMediaNotificationProvider(
            context,
            { MusicService.NOTIFICATION_ID },
            MusicService.CHANNEL_ID,
            R.string.music_player,
        ).apply {
            setSmallIcon(smallIconResId)
        }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val mediaNotification =
            delegate.createNotification(
                mediaSession,
                mediaButtonPreferences,
                actionFactory,
                onNotificationChangedCallback,
            )
        val player = mediaSession.player
        val shouldBeOngoing =
            player.playWhenReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED

        val notification = mediaNotification.notification
        if (shouldBeOngoing) {
            notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
        } else {
            notification.flags = notification.flags and Notification.FLAG_ONGOING_EVENT.inv()
        }

        return MediaNotification(mediaNotification.notificationId, notification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo = delegate.notificationChannelInfo
}




