package moe.rgsekai.sekaitune.ui.screens.devices

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.link.remote.RemoteSetting
import moe.rgsekai.sekaitune.link.remote.RemoteTarget
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// the steered device's settings
@Composable
internal fun RemoteSettingsSection(link: DevicesLink, target: RemoteTarget) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember(target.id) { mutableStateOf<List<RemoteSetting>?>(null) }
    var tries by remember(target.id) { mutableIntStateOf(0) }
    var failed by remember(target.id) { mutableStateOf(false) }
    LaunchedEffect(target.id, tries) {
        failed = false
        val got = link.remote.settings()
        settings = got
        failed = got == null
    }

    fun change(setting: RemoteSetting, value: Any) {
        settings = settings?.map { if (it.key == setting.key) it.copy(value = value) else it }
        scope.launch {
            val fresh = link.remote.changeSetting(setting.key, value)
            if (fresh != null) {
                settings = fresh
            } else {
                Toast.makeText(context, context.getString(R.string.remote_setting_failed, target.name), Toast.LENGTH_SHORT).show()
                tries++
            }
        }
    }

    Spacer(Modifier.height(22.dp))
    GroupLabel(stringResource(R.string.remote_settings_on, target.name).uppercase())
    val shown = settings
    when {
        shown == null && failed -> Text(
            stringResource(R.string.remote_settings_failed),
            fontSize = 13.sp,
            color = Muted,
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
        )
        shown == null -> Text(
            stringResource(R.string.remote_settings_loading),
            fontSize = 13.sp,
            color = Muted,
            modifier = Modifier.padding(start = 2.dp),
        )
        else -> shown.groupBy { it.section }.forEach { (section, items) ->
            Text(
                section,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 8.dp),
            )
            Card {
                items.forEach { setting -> SettingRow(setting) { value -> change(setting, value) } }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
    if (failed && shown == null) {
        PillButton(stringResource(R.string.remote_settings_retry), modifier = Modifier.padding(start = 2.dp)) { tries++ }
    }
}

@Composable
private fun SettingRow(setting: RemoteSetting, onChange: (Any) -> Unit) {
    when (setting.type) {
        RemoteSetting.Type.Switch -> ToggleRow(
            icon = null,
            title = setting.title,
            detail = null,
            checked = setting.value == true,
            onChange = onChange,
        )
        RemoteSetting.Type.Choice -> ChoiceRow(setting, onChange)
        RemoteSetting.Type.Seconds -> SecondsRow(setting, onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(setting: RemoteSetting, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(setting.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            setting.options.forEach { (value, label) ->
                PillButton(label, bright = setting.value == value) { if (setting.value != value) onPick(value) }
            }
        }
    }
}

@Composable
private fun SecondsRow(setting: RemoteSetting, onPick: (Float) -> Unit) {
    val stored = (setting.value as? Number)?.toFloat() ?: setting.min
    var dragged by remember(setting.key, stored) { mutableFloatStateOf(stored) }
    val steps = ((setting.max - setting.min) / setting.step).roundToInt() - 1
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(setting.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.remote_seconds, dragged.roundToInt()), fontSize = 13.sp, color = Muted)
        }
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { if (dragged != stored) onPick(dragged) },
            valueRange = setting.min..setting.max,
            steps = steps.coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

