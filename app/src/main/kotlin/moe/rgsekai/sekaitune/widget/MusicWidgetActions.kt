/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import moe.rgsekai.sekaitune.playback.MusicService

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        sendWidgetAction(context, ACTION_PLAY_PAUSE)
    }
}

class SkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        sendWidgetAction(context, ACTION_SKIP_NEXT)
    }
}

class SkipPrevAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        sendWidgetAction(context, ACTION_SKIP_PREV)
    }
}

private const val ACTION_PLAY_PAUSE = "moe.rgsekai.sekaitune.WIDGET_PLAY_PAUSE"
private const val ACTION_SKIP_NEXT = "moe.rgsekai.sekaitune.WIDGET_SKIP_NEXT"
private const val ACTION_SKIP_PREV = "moe.rgsekai.sekaitune.WIDGET_SKIP_PREV"
private const val TAG = "MusicWidgetActions"

private fun sendWidgetAction(
    context: Context,
    action: String,
) {
    val intent = Intent(action).setClass(context, MusicService::class.java)
    runCatching {
        context.startService(intent)
    }.onFailure { error ->
        Log.e(TAG, "Failed to send widget action: $action", error)
    }
}




