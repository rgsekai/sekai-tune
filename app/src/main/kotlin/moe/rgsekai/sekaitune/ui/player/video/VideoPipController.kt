package moe.rgsekai.sekaitune.ui.player.video

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.playback.PlayerConnection

private const val ACTION_PIP_PREVIOUS = "moe.rgsekai.sekaitune.pip.ACTION_PREVIOUS"
private const val ACTION_PIP_PLAY_PAUSE = "moe.rgsekai.sekaitune.pip.ACTION_PLAY_PAUSE"
private const val ACTION_PIP_NEXT = "moe.rgsekai.sekaitune.pip.ACTION_NEXT"

val LocalInPipMode = compositionLocalOf { false }

fun isPipSupported(context: Context): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
}

@Composable
fun rememberPipBroadcastReceiver(playerConnection: PlayerConnection?) {
    val context = LocalContext.current
    val currentConnection by rememberUpdatedState(playerConnection)

    DisposableEffect(context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@DisposableEffect onDispose {}
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val connection = currentConnection ?: return
                when (intent?.action) {
                    ACTION_PIP_PREVIOUS -> connection.player.seekToPrevious()
                    ACTION_PIP_PLAY_PAUSE -> {
                        if (connection.player.isPlaying) {
                            connection.player.pause()
                        } else {
                            connection.player.play()
                        }
                    }
                    ACTION_PIP_NEXT -> connection.player.seekToNext()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_PREVIOUS)
            addAction(ACTION_PIP_PLAY_PAUSE)
            addAction(ACTION_PIP_NEXT)
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun buildPipParams(
    context: Context,
    aspect: Float,
    isPlaying: Boolean,
): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()

    // Aspect ratio: must be within 1:2.39 (0.4184) and 2.39:1 (2.39)
    val safeAspect = if (aspect in 0.42f..2.38f) aspect else (16f / 9f)
    val numerator = (safeAspect * 1000).toInt()
    val denominator = 1000
    try {
        builder.setAspectRatio(Rational(numerator, denominator))
    } catch (_: Exception) {
        builder.setAspectRatio(Rational(16, 9))
    }

    // Remote actions: previous, play/pause, next
    val prevIntent = PendingIntent.getBroadcast(
        context,
        101,
        Intent(ACTION_PIP_PREVIOUS).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val playPauseIntent = PendingIntent.getBroadcast(
        context,
        102,
        Intent(ACTION_PIP_PLAY_PAUSE).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val nextIntent = PendingIntent.getBroadcast(
        context,
        103,
        Intent(ACTION_PIP_NEXT).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val prevAction = RemoteAction(
        Icon.createWithResource(context, R.drawable.skip_previous),
        context.getString(R.string.previous),
        context.getString(R.string.previous),
        prevIntent,
    )

    val playPauseIcon = if (isPlaying) R.drawable.pause else R.drawable.play
    val playPauseText = context.getString(if (isPlaying) R.string.pause else R.string.play)
    val playPauseAction = RemoteAction(
        Icon.createWithResource(context, playPauseIcon),
        playPauseText,
        playPauseText,
        playPauseIntent,
    )

    val nextAction = RemoteAction(
        Icon.createWithResource(context, R.drawable.skip_next),
        context.getString(R.string.next),
        context.getString(R.string.next),
        nextIntent,
    )

    builder.setActions(listOf(prevAction, playPauseAction, nextAction))

    return builder.build()
}

fun enterPictureInPictureMode(
    activity: Activity,
    aspect: Float,
    isPlaying: Boolean,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported(activity)) {
        try {
            val params = buildPipParams(activity, aspect, isPlaying)
            activity.enterPictureInPictureMode(params)
        } catch (_: Exception) {}
    }
}

