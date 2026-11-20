package moe.rgsekai.sekaitune.floating

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.FloatingLyricsKey
import moe.rgsekai.sekaitune.utils.rememberPreference

@Composable
fun rememberFloatingLyricsSwitch(): Pair<Boolean, (Boolean) -> Unit> {
    val context = LocalContext.current
    val (wanted, setWanted) = rememberPreference(FloatingLyricsKey, defaultValue = false)
    var allowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var asking by remember { mutableStateOf(false) }
    val currentSetWanted by rememberUpdatedState(setWanted)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            allowed = Settings.canDrawOverlays(context)
            if (asking && !allowed) currentSetWanted(false)
            asking = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val onChange: (Boolean) -> Unit = { on ->
        setWanted(on)
        if (on && !Settings.canDrawOverlays(context)) {
            asking = askForOverlay(context)
        }
    }
    return (wanted && (allowed || asking)) to onChange
}

private fun askForOverlay(context: Context): Boolean {
    Toast.makeText(context, R.string.floating_lyrics_permission, Toast.LENGTH_LONG).show()
    val ask = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(ask) }.recoverCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}

