/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.settings.discord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import moe.rgsekai.sekaitune.discord.PresenceButton
import moe.rgsekai.sekaitune.discord.PresenceButtonTarget
import moe.rgsekai.sekaitune.discord.PresenceImage
import moe.rgsekai.sekaitune.discord.PresenceSettings
import moe.rgsekai.sekaitune.discord.PresenceText
import moe.rgsekai.sekaitune.discord.SpinStyle
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(16.dp)
private const val UnfoldMs = 280

@Composable
internal fun DiscordSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
internal fun DiscordCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
internal fun DiscordCardTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun DiscordSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    DiscordCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                DiscordCardTitle(title)
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

@Composable
internal fun DiscordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp),
            ) {
                if (value.isEmpty()) {
                    Text(text = hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 14.sp)
                }
                inner()
            }
        },
    )
}

@Composable
internal fun DiscordTextCard(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "Hidden",
) {
    DiscordCard {
        DiscordCardTitle(title)
        Spacer(Modifier.height(8.dp))
        DiscordTextField(value = value, onValueChange = onValueChange, hint = hint)
    }
}

@Composable
internal fun <T> DiscordDropdown(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    optionIcon: ((T) -> Int)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val chevronTurn by animateFloatAsState(if (open) 180f else 0f, tween(UnfoldMs), label = "chevron")
    val bringIntoView = remember { BringIntoViewRequester() }

    LaunchedEffect(open) {
        if (open) {
            delay(UnfoldMs.toLong())
            bringIntoView.bringIntoView()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable { open = !open }
                .padding(horizontal = 14.dp),
        ) {
            optionIcon?.invoke(selected)?.let {
                Icon(painter = painterResource(it), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = label(selected),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.rotate(chevronTurn),
            )
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clickable {
                                onSelect(option)
                                open = false
                            }
                            .padding(horizontal = 14.dp),
                    ) {
                        optionIcon?.invoke(option)?.let {
                            Icon(painter = painterResource(it), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = label(option),
                            color = if (option == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun <T> DiscordSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
internal fun DiscordStepSlider(
    value: Int,
    values: List<Int>,
    label: (Int) -> String,
    onValueChange: (Int) -> Unit,
) {
    val latest by rememberUpdatedState(onValueChange)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val lastIndex = values.lastIndex.coerceAtLeast(1)
        fun valueAt(x: Float): Int {
            val fraction = (x / widthPx).coerceIn(0f, 1f)
            return values[(fraction * lastIndex).roundToInt().coerceIn(0, values.lastIndex)]
        }
        val fraction = values.indexOf(value).coerceAtLeast(0).toFloat() / lastIndex

        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(widthPx, values) { detectTapGestures { latest(valueAt(it.x)) } }
                .pointerInput(widthPx, values) { detectHorizontalDragGestures { change, _ -> latest(valueAt(change.position.x)) } }
                .drawBehind {
                    val y = size.height - 8.dp.toPx()
                    val thickness = 4.dp.toPx()
                    val radius = CornerRadius(thickness / 2)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.2f),
                        topLeft = Offset(0f, y - thickness / 2),
                        size = Size(size.width, thickness),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(0f, y - thickness / 2),
                        size = Size(size.width * fraction, thickness),
                        cornerRadius = radius,
                    )
                },
        ) {
            Text(
                text = label(value),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

@Composable
internal fun DiscordImageSourceCard(
    title: String,
    selected: PresenceImage,
    options: List<PresenceImage>,
    onSelect: (PresenceImage) -> Unit,
    spin: SpinStyle? = null,
    onSpinChange: ((SpinStyle) -> Unit)? = null,
) {
    DiscordCard {
        DiscordCardTitle(title)
        Spacer(Modifier.height(8.dp))
        DiscordDropdown(
            selected = selected,
            options = options,
            label = { it.label },
            onSelect = onSelect,
        )
        if (selected.isRotating && spin != null && onSpinChange != null) {
            Spacer(Modifier.height(10.dp))
            FieldLabel("Speed")
            DiscordStepSlider(
                value = spin.secondsPerTurn,
                values = (PresenceSettings.MinSpinSeconds..PresenceSettings.MaxSpinSeconds).toList(),
                label = { "${it}s per turn" },
                onValueChange = { onSpinChange(spin.copy(secondsPerTurn = it)) },
            )
            Spacer(Modifier.height(6.dp))
            FieldLabel("FPS")
            DiscordStepSlider(
                value = spin.fps,
                values = PresenceSettings.SpinFpsSteps,
                label = { "$it fps" },
                onValueChange = { onSpinChange(spin.copy(fps = it)) },
            )
        }
    }
}

@Composable
internal fun DiscordButtonCard(
    title: String,
    button: PresenceButton,
    onChange: (PresenceButton) -> Unit,
) {
    DiscordCard {
        DiscordCardTitle(title)
        FieldLabel("Label")
        DiscordTextField(
            value = button.text,
            onValueChange = { onChange(button.copy(text = it)) },
            hint = "No button",
        )
        FieldLabel("Target URL")
        DiscordDropdown(
            selected = button.target,
            options = PresenceButtonTarget.entries,
            label = { it.label },
            onSelect = { onChange(button.copy(target = it)) },
            optionIcon = { it.icon },
        )
    }
}

@Composable
internal fun DiscordPlaceholderCard() {
    DiscordCard {
        Text("Placeholders", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        PresenceText.Placeholders.forEach { (placeholder, meaning) ->
            Text(
                text = "$placeholder = $meaning",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
internal fun DiscordNoticeCard(text: String) {
    DiscordCard {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

