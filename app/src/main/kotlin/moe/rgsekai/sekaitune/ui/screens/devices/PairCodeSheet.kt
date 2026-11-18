package moe.rgsekai.sekaitune.ui.screens.devices

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.link.LinkPeer
import moe.rgsekai.sekaitune.link.LinkWire
import moe.rgsekai.sekaitune.ui.component.GlassModalBottomSheet
import kotlinx.coroutines.launch

private val BoxColor = Color(0xFF2A2A2E)

@Composable
fun PairCodeSheet(
    link: DevicesLink,
    peer: LinkPeer?,
    onDismissRequest: () -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = null,
    ) {
        PairCodeContent(link = link, tv = peer, onClose = onDismissRequest)
    }
}

@Composable
fun PairCodeContent(link: DevicesLink, tv: LinkPeer?, onClose: () -> Unit) {
    val attempt = remember { PairAttempt(link.host) }
    val state = attempt.state
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val target = tv ?: address.trim().takeIf { it.isNotEmpty() }?.let {
        LinkPeer(id = "", name = it, host = it.substringBefore(':'), port = it.substringAfter(':', "").toIntOrNull() ?: LinkWire.PairPort)
    }
    val codeFocus = remember { FocusRequester() }
    val addressFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        runCatching { (if (tv == null) addressFocus else codeFocus).requestFocus() }
        keyboard?.show()
    }
    LaunchedEffect(state) {
        if (state == PairUi.WrongCode) code = ""
        if (state is PairUi.Done) keyboard?.hide()
    }

    AnimatedContent(
        targetState = state is PairUi.Done,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
        label = "tvPairSheet",
    ) { done ->
        if (done) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF5BD68A).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.check), contentDescription = null, tint = Color(0xFF5BD68A), modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.devices_paired_with, (state as? PairUi.Done)?.name.orEmpty()),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = Color.White,
                )
                Spacer(Modifier.height(20.dp))
                SheetButton(stringResource(R.string.tv_done), Modifier.fillMaxWidth(), bright = true, onClick = onClose)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    if (tv != null) stringResource(R.string.tv_pair_sheet_title, tv.name) else stringResource(R.string.tv_pair_sheet_by_address),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Text(
                    if (tv != null) stringResource(R.string.devices_pair_detail, tv.name) else stringResource(R.string.tv_pair_sheet_address_detail),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Muted,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 18.dp),
                )
                if (tv == null) {
                    BasicTextField(
                        value = address,
                        onValueChange = { address = it.filter { char -> char.isDigit() || char == '.' || char == ':' }.take(21) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(addressFocus)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BoxColor)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        decorationBox = { inner ->
                            if (address.isEmpty()) Text("192.168.1.50:47653", color = Muted)
                            inner()
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                }

                // 6 boxes for code
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (i in 0 until 6) {
                            val digit = code.getOrNull(i)?.toString().orEmpty()
                            val focused = i == code.length.coerceAtMost(5)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.85f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BoxColor)
                                    .border(
                                        width = if (focused) 2.dp else 1.dp,
                                        color = if (focused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    digit,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    BasicTextField(
                        value = code,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() }.take(6)
                            code = clean
                            if (clean.length == 6 && target != null) {
                                scope.launch { attempt.pair(listOf(target), clean) }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(codeFocus)
                            .alpha(0f),
                    )
                }

                val note = attempt.state.note
                if (note != null) {
                    Text(
                        stringResource(note),
                        fontSize = 12.sp,
                        color = if (attempt.state.isProblem) Color(0xFFFF8A80) else Muted,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                SheetButton(stringResource(R.string.tv_cancel), Modifier.fillMaxWidth(), onClick = onClose)
            }
        }
    }
}

