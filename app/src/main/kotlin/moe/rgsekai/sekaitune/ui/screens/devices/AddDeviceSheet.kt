package moe.rgsekai.sekaitune.ui.screens.devices

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.link.LinkIdentity
import moe.rgsekai.sekaitune.link.LinkPeer
import moe.rgsekai.sekaitune.link.LinkWire
import moe.rgsekai.sekaitune.link.PairingCode
import moe.rgsekai.sekaitune.link.discoverPeers
import moe.rgsekai.sekaitune.ui.component.GlassModalBottomSheet
import kotlinx.coroutines.launch

@Composable
fun AddDeviceSheet(
    link: DevicesLink,
    onPairCode: (LinkPeer?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = null,
    ) {
        AddDeviceContent(link, onPairCode = onPairCode)
        Spacer(Modifier.height(16.dp))
        SheetButton(stringResource(R.string.tv_done), Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            onDismissRequest()
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun AddDeviceContent(
    link: DevicesLink,
    onPairCode: (LinkPeer?) -> Unit,
) {
    val context = LocalContext.current
    val code by link.pairing.code.collectAsState()
    val devices by link.host.devices.collectAsState()
    val nearby by remember { discoverPeers(context, LinkWire.PairServiceType) }.collectAsState(initial = emptyList())
    val myId = remember { LinkIdentity.id(context) }
    val detected = nearby.filter { peer -> peer.id != myId && devices.none { it.id == peer.id } }
    val attempt = remember { PairAttempt(link.host) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        link.pairing.open()
        onDispose { link.pairing.close() }
    }
    LaunchedEffect(Unit) {
        link.pairing.paired.collect {
            Toast.makeText(context, context.getString(R.string.devices_paired_with, it.name), Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(attempt.state) {
        (attempt.state as? PairUi.Done)?.let {
            Toast.makeText(context, context.getString(R.string.devices_paired_with, it.name), Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        SheetTitle(stringResource(R.string.devices_add_title))
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(CardShape)
                .background(MenuRowSurface),
        ) {
            LinkPicture(Modifier.fillMaxSize(), scale = 1.55f)
        }

        Spacer(Modifier.height(18.dp))
        GroupLabel(stringResource(R.string.devices_pairing_code))
        PairingCodeBlock(code)

        Spacer(Modifier.height(20.dp))
        GroupLabel(stringResource(R.string.devices_detected))
        if (detected.isEmpty()) {
            LookingRow()
        } else {
            detected.forEach { peer ->
                DetectedRow(peer) { onPairCode(peer) }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        GroupLabel(stringResource(R.string.devices_or))
        EnterCodeField(enabled = attempt.state != PairUi.Working) { typed ->
            scope.launch { attempt.pair(detected, typed) }
        }
        val note = attempt.state.note
        if (note != null) {
            Text(
                stringResource(note),
                fontSize = 12.sp,
                color = if (attempt.state.isProblem) Color(0xFFFF8A80) else Muted,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
        Text(
            stringResource(R.string.tv_pair_sheet_by_address),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Muted,
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(CircleShape)
                .clickable { onPairCode(null) }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PairingCodeBlock(code: PairingCode?) {
    val digits = code?.code ?: "——————"
    Text(
        digits.take(3) + "  " + digits.drop(3),
        fontFamily = FontFamily.Monospace,
        fontSize = 40.sp,
        letterSpacing = 10.sp,
        color = Color.White,
        maxLines = 1,
    )
    Spacer(Modifier.height(8.dp))
    val bars = remember(code?.code) { barsFor(digits + Build.MODEL.orEmpty()) }
    Canvas(Modifier.width(220.dp).height(20.dp)) {
        val unit = size.width / bars.sum()
        var x = 0f
        bars.forEachIndexed { index, width ->
            if (index % 2 == 0) drawRect(Color.White.copy(alpha = 0.85f), Offset(x, 0f), Size(width * unit, size.height))
            x += width * unit
        }
    }
    Spacer(Modifier.height(4.dp))
    val caption = listOfNotNull(
        digits.take(3) + " " + digits.drop(3),
        "ANDROID ${Build.VERSION.RELEASE}",
        code?.deviceName?.uppercase(),
        code?.address,
    ).joinToString("  ·  ")
    Text(caption, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun barsFor(seed: String): List<Int> = buildList {
    seed.forEach { char ->
        val bits = char.code
        add(1 + (bits and 1) + (bits shr 3 and 1))
        add(1 + (bits shr 1 and 1))
        add(1 + (bits shr 2 and 1))
    }
}

@Composable
private fun LookingRow() {
    val phase by rememberInfiniteTransition(label = "devicesLooking").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "devicesLookingPhase",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MenuRowSurface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.devices_looking), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Text(stringResource(R.string.devices_looking_detail), fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(6.dp)
                        .graphicsLayer { alpha = if (phase.toInt() == index) 0.9f else 0.25f }
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
private fun DetectedRow(peer: LinkPeer, onConnect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MenuRowSurface)
            .clickable(onClick = onConnect)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(peer.kind.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(peer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(peer.kind.label(), fontSize = 12.sp, color = Muted)
        }
        PillButton(stringResource(R.string.devices_connect), onClick = onConnect)
    }
}

@Composable
private fun EnterCodeField(enabled: Boolean, onCode: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    BasicTextField(
        value = code,
        onValueChange = { typed ->
            code = typed.filter(Char::isDigit).take(6)
            if (code.length == 6) onCode(code)
        },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = 4.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MenuRowSurface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        decorationBox = { inner ->
            if (code.isEmpty()) {
                Text(stringResource(R.string.devices_enter_code), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White.copy(alpha = 0.8f))
            }
            inner()
        },
    )
}

