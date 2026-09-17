/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.buddy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import moe.rgsekai.sekaitune.MainActivity
import moe.rgsekai.sekaitune.R

object BuddyNotificationManager {
    const val CHANNEL_ID = "channel_buddy_requests"
    const val CHANNEL_NAME = "Buddy Requests & Invites"
    const val CHANNEL_DESCRIPTION = "Notifications for buddy requests, responses, and Together Online session invites"

    const val EXTRA_JOIN_TOGETHER_CODE = "JOIN_TOGETHER_CODE"
    const val EXTRA_JOIN_TOGETHER_SESSION_ID = "JOIN_TOGETHER_SESSION_ID"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showInviteNotification(
        context: Context,
        sessionId: String,
        hostDisplayName: String,
        sessionCode: String,
    ) {
        createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_JOIN_TOGETHER_CODE, sessionCode)
            putExtra(EXTRA_JOIN_TOGETHER_SESSION_ID, sessionId)
            putExtra("navigate_to", "settings/music_together")
        }

        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val joinIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_JOIN_TOGETHER_CODE, sessionCode)
            putExtra(EXTRA_JOIN_TOGETHER_SESSION_ID, sessionId)
            putExtra("navigate_to", "settings/music_together")
        }

        val joinPendingIntent = PendingIntent.getActivity(
            context,
            sessionId.hashCode() + 1,
            joinIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(context.getString(R.string.together_invite_notification_title))
            .setContentText(context.getString(R.string.together_invite_notification_text, hostDisplayName, sessionCode))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.join,
                context.getString(R.string.together_join_action),
                joinPendingIntent,
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(sessionId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }

    fun cancelInviteNotification(context: Context, sessionId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(sessionId.hashCode())
        } catch (e: Exception) {
            // Silently fail
        }
    }
}

