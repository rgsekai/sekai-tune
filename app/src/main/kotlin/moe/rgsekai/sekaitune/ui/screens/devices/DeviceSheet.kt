package moe.rgsekai.sekaitune.ui.screens.devices

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.link.phone.PairedDevice
import moe.rgsekai.sekaitune.ui.component.DefaultDialog
import moe.rgsekai.sekaitune.ui.component.GlassModalBottomSheet
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun DeviceSheet(
    link: DevicesLink,
    deviceId: String,
    onDismissRequest: () -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = null,
    ) {
        DeviceSheetContent(link, deviceId, onClose = onDismissRequest)
        Spacer(Modifier.height(16.dp))
        SheetButton(stringResource(R.string.devices_ok), Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            onDismissRequest()
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun DeviceSheetContent(link: DevicesLink, deviceId: String, onClose: () -> Unit) {
    val devices by link.host.devices.collectAsState()
    val live by link.host.live.collectAsState()
    val target by link.remote.target.collectAsState()
    val device = devices.firstOrNull { it.id == deviceId }
    if (device == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val around = remember(live) { link.host.liveDevice(deviceId) }
    var unlinking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        DevicePicture(device.kind, Modifier.width(140.dp).height(96.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            device.name,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = (-0.02).em,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${device.kind.label()} · ${statusOf(device, around, target?.id == device.id, System.currentTimeMillis())}",
            fontSize = 13.sp,
            color = Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))

        Card {
            ToggleRow(
                icon = Icons.Rounded.SettingsRemote,
                title = stringResource(R.string.devices_remote_control),
                detail = stringResource(if (device.kind.isTv) R.string.devices_remote_control_tv_detail else R.string.devices_remote_control_phone_detail),
                checked = device.remoteControl,
            ) { on -> link.host.update(deviceId) { it.copy(remoteControl = on, autoControl = it.autoControl && on) } }
            ToggleRow(
                icon = Icons.Rounded.Bolt,
                title = stringResource(R.string.devices_auto_control),
                detail = stringResource(R.string.devices_auto_control_detail),
                checked = device.autoControl && device.remoteControl,
                enabled = device.remoteControl,
            ) { on -> link.host.update(deviceId) { it.copy(autoControl = on) } }
            if (device.kind.isTv) {
                ToggleRow(
                    icon = Icons.Rounded.Wifi,
                    title = stringResource(R.string.devices_stream_local),
                    detail = stringResource(R.string.devices_stream_local_detail),
                    checked = device.streamLocal,
                ) { on -> link.host.update(deviceId) { it.copy(streamLocal = on) } }
                ToggleRow(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.devices_override_library),
                    detail = stringResource(R.string.devices_override_library_detail),
                    checked = device.mirrorLibrary,
                ) { on -> link.host.update(deviceId) { it.copy(mirrorLibrary = on) } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            if (device.kind.isTv && device.mirrorLibrary) {
                ActionRow(stringResource(R.string.devices_sync_now), enabled = around != null) {
                    val tv = around ?: return@ActionRow
                    scope.launch {
                        val asked = link.host.tell(tv, "/control", JSONObject().put("action", "sync"))
                        if (asked != null) {
                            Toast.makeText(context, context.getString(R.string.devices_sync_asked, device.name), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.tv_menu_unreachable, device.name), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            ActionRow(stringResource(R.string.tv_unlink), danger = true) { unlinking = true }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (unlinking) {
        DefaultDialog(
            onDismiss = { unlinking = false },
            content = {
                Text(stringResource(R.string.devices_unlink_question, device.name), color = Color.White)
            },
            buttons = {
                TextButton(onClick = { unlinking = false }) {
                    Text(stringResource(R.string.tv_cancel))
                }
                TextButton(onClick = {
                    unlinking = false
                    link.host.unlink(deviceId)
                    onClose()
                }) {
                    Text(stringResource(R.string.tv_unlink), color = Color(0xFFFF8A80))
                }
            },
        )
    }
}
