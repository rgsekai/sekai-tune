// PhoneLinkService.kt

package moe.rgsekai.sekaitune.link.phone

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import moe.rgsekai.sekaitune.MainActivity
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.link.isTelevision
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// keeps the phone reachable for its paired devices while Sekai Tune is closed, with a quiet notification saying so
@AndroidEntryPoint
class PhoneLinkService : Service() {
    @Inject
    lateinit var host: PhoneLinkHost

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop || host.devices.value.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NotificationId,
                notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        }
        if (started.isFailure) {
            Timber.tag("Link").w(started.exceptionOrNull(), "couldn't stay in the background")
            stopSelf()
            return START_NOT_STICKY
        }
        host.refresh()
        watchState()
        return START_STICKY
    }

    private fun watchState() {
        if (watching) return
        watching = true
        scope.launch {
            kotlinx.coroutines.flow.combine(host.devices, host.live, host.lastStream, ticks()) { _, _, _, _ -> status() }
                .distinctUntilChanged()
                .collect { NotificationManagerCompat.from(this@PhoneLinkService).runCatching { notify(NotificationId, notification(it)) } }
        }
    }

    private fun ticks() = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(30_000)
        }
    }

    private fun status(): Pair<String, String> {
        val now = System.currentTimeMillis()
        val around = host.liveDevices(now)
        val streaming = around.filter { device -> host.lastStream.value[device.id]?.let { now - it < StreamingForMs } == true }
        val names = host.devices.value.joinToString { it.name }
        return when {
            streaming.isNotEmpty() -> getString(R.string.tv_link_status_streaming, streaming.joinToString { it.name }) to getString(R.string.tv_link_status_streaming_detail)
            around.isNotEmpty() -> getString(R.string.tv_link_status_connected, around.joinToString { it.name }) to
                getString(if (around.any { it.kind.isTv }) R.string.tv_link_status_connected_detail else R.string.devices_status_connected_detail)
            else -> getString(R.string.devices_notification_title) to getString(R.string.devices_notification_text, names)
        }
    }

    private fun notification(status: Pair<String, String> = status()): android.app.Notification {
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(ChannelId, NotificationManagerCompat.IMPORTANCE_MIN)
                .setName(getString(R.string.tv_link_channel))
                .setShowBadge(false)
                .build(),
        )
        val (title, text) = status
        val open = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(ActionOpenTvLink),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val ChannelId = "tv_link"
        private const val NotificationId = 4_711
        private const val ActionStop = "moe.rgsekai.sekaitune.link.STOP"
        const val ActionOpenTvLink = "moe.rgsekai.sekaitune.action.TV_LINK"

        private const val StreamingForMs = 3 * 60_000L

        fun start(context: Context) {
            if (context.isTelevision()) return
            if (!PhoneLinkHost.hasPairedDevices(context) || !PhoneLinkHost.staysReachable(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PhoneLinkService::class.java)) }
                .onFailure { Timber.tag("Link").i("background link not started: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PhoneLinkService::class.java)) }
        }
    }
}

class PhoneLinkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        PhoneLinkService.start(context)
    }
}

