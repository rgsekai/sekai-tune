/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.aod

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.link.LinkPlayer
import moe.rgsekai.sekaitune.playback.MusicService
import moe.rgsekai.sekaitune.playback.PlayerConnection
import javax.inject.Inject

@AndroidEntryPoint
class AodLockscreenActivity : ComponentActivity() {

    companion object {
        @Volatile
        var isShowing: Boolean = false
            internal set
    }

    @Inject
    lateinit var database: MusicDatabase

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var isMusicServiceBound = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                isMusicServiceBound = true
                if (service is MusicService.MusicBinder) {
                    if (playerConnection == null) {
                        playerConnection =
                            LinkPlayer.current()
                                ?: PlayerConnection(
                                    this@AodLockscreenActivity,
                                    service,
                                    database,
                                    lifecycleScope,
                                )
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isMusicServiceBound = false
                playerConnection = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        AodLogger.log(this, "AodLockscreenActivity.onCreate() - isShowing=true")
        MusicService.dismissLaunchOverlay()
        applyLockscreenFlags()
        hideSystemBars()

        // Immediate zero-latency connection
        val directBinder = MusicService.currentBinder
        playerConnection =
            LinkPlayer.current()
                ?: directBinder?.let {
                    PlayerConnection(
                        this@AodLockscreenActivity,
                        it,
                        database,
                        lifecycleScope,
                    )
                }

        setContent {
            AodLockscreenScreen(
                playerConnection = playerConnection,
                onDismiss = {
                    AodLogger.log(this@AodLockscreenActivity, "AodLockscreenActivity onDismiss (swipe-up)")
                    finish()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(
                            OVERRIDE_TRANSITION_CLOSE,
                            0,
                            0,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                },
                onSleep = {
                    AodLogger.log(this@AodLockscreenActivity, "AodLockscreenActivity onSleep (double-tap)")
                    finish()
                },
            )
        }

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            bindService(
                Intent(this@AodLockscreenActivity, MusicService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
        val now = System.currentTimeMillis()
        val t0 = AodLogger.screenOffTimeMs
        val latencyStr = if (t0 > 0) "${now - t0} ms" else "unknown"
        val path = MusicService.lastSuccessfulLaunchPath
        val preAttached = MusicService.wasPreAttachedOnScreenOff
        AodLogger.log(this, "AodLockscreenActivity.onResume() - isShowing=true | latency from SCREEN_OFF: $latencyStr | preAttached: $preAttached | launchPath: $path")
        MusicService.dismissLaunchOverlay()
        (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(MusicService.AOD_FALLBACK_NOTIFICATION_ID)
        applyLockscreenFlags()
        hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        isShowing = false
        AodLogger.log(this, "AodLockscreenActivity.onPause() - isShowing=false")
    }

    override fun onStop() {
        super.onStop()
        isShowing = false
        AodLogger.log(this, "AodLockscreenActivity.onStop() - isShowing=false")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AodLogger.log(this, "AodLockscreenActivity.onNewIntent()")
        applyLockscreenFlags()
        hideSystemBars()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AodLogger.log(this, "AodLockscreenActivity.onAttachedToWindow()")
        applyLockscreenFlags()
        hideSystemBars()
    }

    private fun applyLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        super.onDestroy()
        isShowing = false
        AodLogger.log(this, "AodLockscreenActivity.onDestroy() - isShowing=false")
        MusicService.currentBinder?.service?.updatePreAttachedOverlay()
        if (isMusicServiceBound) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {
            }
            isMusicServiceBound = false
        }
    }
}
