package moe.rgsekai.sekaitune.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.link.DeviceKind
import moe.rgsekai.sekaitune.link.phone.PhoneLinkHost
import moe.rgsekai.sekaitune.link.phone.PhonePairing
import moe.rgsekai.sekaitune.link.remote.RemoteControl
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DevicesEntryPoint {
    fun phoneLinkHost(): PhoneLinkHost

    fun remoteControl(): RemoteControl

    fun phonePairing(): PhonePairing
}

@Immutable
class DevicesLink(val host: PhoneLinkHost, val remote: RemoteControl, val pairing: PhonePairing)

@Composable
fun rememberDevicesLink(): DevicesLink {
    val context = LocalContext.current.applicationContext
    return remember {
        val entry = EntryPointAccessors.fromApplication(context, DevicesEntryPoint::class.java)
        DevicesLink(entry.phoneLinkHost(), entry.remoteControl(), entry.phonePairing())
    }
}

internal val Muted = Color.White.copy(alpha = 0.6f)
internal val Faint = Color.White.copy(alpha = 0.1f)
internal val LiveGreen = Color(0xFF5BD68A)
internal val CardShape = RoundedCornerShape(22.dp)
internal val MenuRowSurface = Color(0xFF161618)

internal val DeviceKind.icon: ImageVector
    get() = when (this) {
        DeviceKind.Phone -> Icons.Rounded.Smartphone
        DeviceKind.Tablet -> Icons.Rounded.TabletAndroid
        DeviceKind.Tv -> Icons.Rounded.Tv
        DeviceKind.Car -> Icons.Rounded.DirectionsCar
    }

@Composable
internal fun DeviceKind.label(): String = stringResource(
    when (this) {
        DeviceKind.Phone -> R.string.devices_kind_phone
        DeviceKind.Tablet -> R.string.devices_kind_tablet
        DeviceKind.Tv -> R.string.devices_kind_tv
        DeviceKind.Car -> R.string.devices_kind_car
    },
)

@Composable
internal fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.03).em,
        color = Color.White,
        modifier = modifier.padding(start = 2.dp, top = 4.dp),
    )
}

@Composable
internal fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = Muted,
        modifier = modifier.padding(start = 2.dp, bottom = 8.dp),
    )
}

@Composable
internal fun PillButton(text: String, modifier: Modifier = Modifier, bright: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = when {
            bright -> Color.Black
            enabled -> Color.White
            else -> Color.White.copy(alpha = 0.35f)
        },
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(if (bright) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
internal fun SheetButton(text: String, modifier: Modifier = Modifier, bright: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(if (bright) Color.White else Color.White.copy(alpha = 0.26f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = if (bright) Color.Black else Color.White)
    }
}

@Composable
internal fun ToggleRow(
    icon: ImageVector?,
    title: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Faint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            )
            if (detail != null) {
                Text(detail, fontSize = 12.sp, lineHeight = 16.sp, color = Muted.copy(alpha = if (enabled) 0.6f else 0.3f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
            ),
        )
    }
}

@Composable
internal fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MenuRowSurface)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) { content() }
}

@Composable
internal fun ActionRow(text: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = when {
            !enabled -> Color.White.copy(alpha = 0.35f)
            danger -> Color(0xFFFF8A80)
            else -> Color.White
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

