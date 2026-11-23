/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.aod

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.LockscreenAodEnabledKey
import moe.rgsekai.sekaitune.link.LinkPlayer
import moe.rgsekai.sekaitune.utils.PreferenceStore
import moe.rgsekai.sekaitune.utils.dataStore

class AodModeTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val enabled = dataStore.data.first()[LockscreenAodEnabledKey] ?: false
            val newEnabled = !enabled
            PreferenceStore.launchEdit(dataStore) {
                this[LockscreenAodEnabledKey] = newEnabled
            }
            updateTile(newEnabled)

            if (newEnabled && LinkPlayer.current()?.isPlaying?.value == true) {
                val launchIntent =
                    Intent(this@AodModeTileService, AodLockscreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent =
                        PendingIntent.getActivity(
                            this@AodModeTileService,
                            0,
                            launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(launchIntent)
                }
            }
        }
    }

    private fun updateTile(explicitEnabled: Boolean? = null) {
        val tile = qsTile ?: return
        scope.launch {
            val isEnabled = explicitEnabled ?: (dataStore.data.first()[LockscreenAodEnabledKey] ?: false)
            tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.lockscreen_aod)
            tile.icon = Icon.createWithResource(this@AodModeTileService, R.drawable.bedtime)
            tile.updateTile()
        }
    }
}
