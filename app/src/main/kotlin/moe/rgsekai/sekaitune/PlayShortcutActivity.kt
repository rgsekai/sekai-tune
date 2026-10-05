/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import moe.rgsekai.sekaitune.playback.MusicService
import moe.rgsekai.sekaitune.widget.ACTION_PLAY

class PlayShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceIntent = Intent(ACTION_PLAY).setClass(this, MusicService::class.java)
        runCatching {
            ContextCompat.startForegroundService(this, serviceIntent)
        }.onFailure { error ->
            Log.e(TAG, "Failed to start playback service from shortcut", error)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        finish()
    }

    companion object {
        private const val TAG = "PlayShortcutActivity"
    }
}
