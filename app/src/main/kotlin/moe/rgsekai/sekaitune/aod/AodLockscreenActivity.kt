/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.aod

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
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.link.LinkPlayer
import moe.rgsekai.sekaitune.playback.MusicService
import moe.rgsekai.sekaitune.playback.PlayerConnection
import javax.inject.Inject

@AndroidEntryPoint
class AodLockscreenActivity : ComponentActivity() {

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
        Log.d("AodLockscreen", "[${System.currentTimeMillis()}] AodLockscreenActivity.onCreate()")
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

        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )

        setContent {
            AodLockscreenScreen(
                playerConnection = playerConnection,
                onDismiss = {
                    Log.d("AodLockscreen", "[${System.currentTimeMillis()}] AodLockscreenActivity onDismiss (swipe-up)")
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
                    Log.d("AodLockscreen", "[${System.currentTimeMillis()}] AodLockscreenActivity onSleep (double-tap)")
                    finish()
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("AodLockscreen", "[${System.currentTimeMillis()}] AodLockscreenActivity.onResume()")
        applyLockscreenFlags()
        hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        Log.d("AodLockscreen", "[${System.currentTimeMillis()}] AodLockscreenActivity.onStop()")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLockscreenFlags()
        hideSystemBars()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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
        Log.d("AodLockscreen", "[${System.currentTimeMillis()}] AodLockscreenActivity.onDestroy()")
        if (isMusicServiceBound) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {
            }
            isMusicServiceBound = false
        }
    }
}
