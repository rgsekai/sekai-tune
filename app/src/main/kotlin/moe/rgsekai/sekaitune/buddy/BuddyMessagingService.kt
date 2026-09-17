/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.buddy

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.MainActivity
import moe.rgsekai.sekaitune.R
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BuddyMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var fcmTokenManager: BuddyFcmTokenManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag("BuddyFCM").d("FCM onNewToken received: $token")
        serviceScope.launch {
            fcmTokenManager.updateToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Timber.tag("BuddyFCM").d("FCM message received from: ${remoteMessage.from}, data: ${remoteMessage.data}")

        val data = remoteMessage.data
        val messageType = data["type"]

        when (messageType) {
            "together_invite" -> {
                val hostDisplayName = data["hostDisplayName"]?.ifBlank { null }
                    ?: getString(R.string.together_role_guest)
                val sessionCode = data["sessionCode"].orEmpty()
                val sessionId = data["sessionId"]?.ifBlank { null } ?: sessionCode

                BuddyNotificationManager.showInviteNotification(
                    context = this,
                    sessionId = sessionId,
                    hostDisplayName = hostDisplayName,
                    sessionCode = sessionCode,
                )
            }
            "buddy_request" -> {
                val title = remoteMessage.notification?.title
                    ?: data["title"]
                    ?: getString(R.string.app_name)
                val body = remoteMessage.notification?.body
                    ?: data["body"]
                    ?: getString(R.string.together_buddy_request_received)

                showBuddyRequestNotification(title, body)
            }
            else -> {
                val title = remoteMessage.notification?.title
                    ?: data["title"]
                    ?: getString(R.string.app_name)
                val body = remoteMessage.notification?.body
                    ?: data["body"]
                    ?: return

                showGenericNotification(title, body)
            }
        }
    }

    private fun showBuddyRequestNotification(title: String, body: String) {
        BuddyNotificationManager.createNotificationChannel(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "settings/buddy_list")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, BuddyNotificationManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(1001, notification)
        } catch (_: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }

    private fun showGenericNotification(title: String, body: String) {
        BuddyNotificationManager.createNotificationChannel(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, BuddyNotificationManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
}
