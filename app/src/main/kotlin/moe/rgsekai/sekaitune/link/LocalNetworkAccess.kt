// LocalNetworkAccess.kt

package moe.rgsekai.sekaitune.link

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

// android 17 asks people before an app can talk to other devices on the wi-fi, older phones always let it
object LocalNetworkAccess {
    // spelled out, the constant only exists in the android 17 sdk
    const val Permission = "android.permission.ACCESS_LOCAL_NETWORK"

    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(context, Permission) == PackageManager.PERMISSION_GRANTED
}

// asks once while wanted, and on a yes runs onGranted so whatever failed to advertise can try again
@Composable
fun AskForLocalNetwork(wanted: Boolean = true, onGranted: () -> Unit = {}) {
    if (Build.VERSION.SDK_INT < 37) return
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) currentOnGranted()
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(wanted) {
        if (!wanted || asked || LocalNetworkAccess.granted(context)) return@LaunchedEffect
        asked = true
        launcher.launch(LocalNetworkAccess.Permission)
    }
}

