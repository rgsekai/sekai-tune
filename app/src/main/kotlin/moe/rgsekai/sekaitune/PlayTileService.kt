/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import moe.rgsekai.sekaitune.playback.MusicService
import moe.rgsekai.sekaitune.widget.ACTION_PLAY

class PlayTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val serviceIntent = Intent(ACTION_PLAY).setClass(this, MusicService::class.java)
        runCatching {
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_play_label)
            icon = Icon.createWithResource(this@PlayTileService, R.drawable.ic_tile_play)
            updateTile()
        }
    }
}
