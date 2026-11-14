package moe.rgsekai.sekaitune.ui.player.styles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.PlayerDesignStyleKey
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CardScale = 0.72f
private const val CardCenterY = 0.465f

private fun rowLeaving(row: Int, rowPosition: Float): Float = (1f - (rowPosition - row)).coerceIn(0f, 1f)

private class CardGeometry(val w: Float, val h: Float, gapPx: Float) {
    val scale = CardScale
    val cardW = w * scale
    val cardH = h * scale
    val stride = cardW + gapPx
    val strideY = cardH + gapPx
    val centerY = CardCenterY * h

    fun centerX(page: Int, position: Float) = w / 2f + (page - position) * stride
    fun centerY(row: Int, rowPosition: Float) = centerY + (row - rowPosition) * strideY
    fun visible(page: Int, position: Float) = abs(centerX(page, position) - w / 2f) < w / 2f + cardW / 2f
    fun visibleY(row: Int, rowPosition: Float) = abs(centerY(row, rowPosition) - h / 2f) < h / 2f + cardH / 2f
}

/**
 * Player Styles selector screen replicating Mochy's PlayerSelector exact layout, geometry and interaction model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStylesScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val (savedStyleId, onSavedStyleChange) = rememberPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerStyle.DEFAULT.id,
    )

    val columns = remember { PlayerStyle.selectorColumns }
    val initialStyle = remember(savedStyleId) { PlayerStyle.fromPreference(savedStyleId) }

    var initialCol = 0
    var initialRow = 0
    columns.forEachIndexed { colIdx, list ->
        val rowIdx = list.indexOf(initialStyle)
        if (rowIdx >= 0) {
            initialCol = colIdx
            initialRow = rowIdx
        }
    }

    val colAnim = remember { Animatable(initialCol.toFloat()) }
    val rowAnimMap = remember {
        val map = mutableMapOf<Int, Animatable<Float, *>>()
        columns.indices.forEach {
            map[it] = Animatable(if (it == initialCol) initialRow.toFloat() else 0f)
        }
        map
    }
    val coroutineScope = rememberCoroutineScope()

    val colPos = colAnim.value
    val columnCount = columns.size
    val lastColumn = columnCount - 1
    val focusedCol = colPos.roundToInt().coerceIn(0, lastColumn)
    val focusedRow = (rowAnimMap[focusedCol]?.value ?: 0f).roundToInt().coerceIn(0, columns[focusedCol].lastIndex)
    val focusedStyle = columns[focusedCol][focusedRow]

    val previewData = rememberPlayerPreviewData()
    var showConfigureSheet by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val gapPx = with(density) { 18.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val geom = remember(w, h, gapPx) { CardGeometry(w, h, gapPx) }
        val restingBottomPx = geom.centerY + geom.cardH / 2f
        val cardsTopPx = with(density) { (statusTop + 50.dp).toPx() }
        val cardsBottomPx = restingBottomPx + with(density) { 18.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(columnCount, w, h, cardsTopPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPointerInputChange(down)
                        val col = colAnim.value.roundToInt().coerceIn(0, lastColumn)
                        val rowAnim = rowAnimMap[col] ?: return@awaitEachGesture
                        val startPage = colAnim.value
                        val startRow = rowAnim.value
                        val lastRow = columns[col].lastIndex
                        var dx = 0f
                        var dy = 0f
                        var mode = 0 // 0 undecided, 1 across the columns, 2 up and down
                        var released = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = true
                                break
                            }

                            val delta = change.positionChangeIgnoreConsumed()
                            dx += delta.x
                            dy += delta.y
                            tracker.addPointerInputChange(change)

                            if (mode == 0 && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                                mode = if (abs(dx) >= abs(dy)) 1 else 2
                            }

                            if (mode == 1) {
                                change.consume()
                                val raw = startPage - dx / geom.stride
                                coroutineScope.launch {
                                    colAnim.snapTo(
                                        when {
                                            raw < 0f -> raw * 0.3f
                                            raw > lastColumn -> lastColumn + (raw - lastColumn) * 0.3f
                                            else -> raw
                                        }
                                    )
                                }
                            } else if (mode == 2) {
                                change.consume()
                                val raw = startRow - dy / geom.strideY
                                coroutineScope.launch {
                                    rowAnim.snapTo(
                                        when {
                                            raw < 0f -> 0f
                                            raw > lastRow -> lastRow + (raw - lastRow) * 0.3f
                                            else -> raw
                                        }
                                    )
                                }
                            }
                        }

                        val velocity = tracker.calculateVelocity()
                        when (mode) {
                            0 -> if (released) {
                                val tap = down.position
                                if (tap.y >= cardsTopPx && tap.y <= cardsBottomPx) {
                                    val tappedCol = (colAnim.value + (tap.x - w / 2f) / geom.stride).roundToInt()
                                    if (tappedCol in 0..lastColumn) {
                                        val rowPosition = rowAnimMap[tappedCol]?.value ?: 0f
                                        val tappedRow = (rowPosition + (tap.y - geom.centerY) / geom.strideY).roundToInt()
                                        if (tappedRow in 0..columns[tappedCol].lastIndex) {
                                            val onCard = abs(tap.x - geom.centerX(tappedCol, colAnim.value)) < geom.cardW / 2f &&
                                                abs(tap.y - geom.centerY(tappedRow, rowPosition)) < geom.cardH / 2f
                                            if (onCard && rowLeaving(tappedRow, rowPosition) >= 0.5f) {
                                                when {
                                                    tappedCol != focusedCol -> coroutineScope.launch {
                                                        colAnim.animateTo(tappedCol.toFloat(), spring(dampingRatio = 0.88f, stiffness = 210f))
                                                    }
                                                    tappedRow != rowPosition.roundToInt() -> coroutineScope.launch {
                                                        rowAnimMap[tappedCol]?.animateTo(tappedRow.toFloat(), spring(dampingRatio = 0.88f, stiffness = 210f))
                                                    }
                                                    else -> {
                                                        onSavedStyleChange(columns[tappedCol][tappedRow].id)
                                                        navController.popBackStack()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                val flick = (-velocity.x / geom.stride).coerceIn(-3f, 3f)
                                val target = (colAnim.value + flick * 0.3f).roundToInt().coerceIn(0, lastColumn)
                                coroutineScope.launch {
                                    colAnim.animateTo(target.toFloat(), spring(dampingRatio = 0.88f, stiffness = 210f))
                                }
                            }
                            2 -> {
                                val rowVelocity = (-velocity.y / geom.strideY).coerceIn(-4f, 4f)
                                val pos = rowAnim.value
                                val target = when {
                                    rowVelocity > 0.35f -> kotlin.math.max(kotlin.math.ceil(pos), (pos + rowVelocity * 0.25f).roundToInt().toFloat())
                                    rowVelocity < -0.35f -> kotlin.math.min(kotlin.math.floor(pos), (pos + rowVelocity * 0.25f).roundToInt().toFloat())
                                    else -> pos.roundToInt().toFloat()
                                }.roundToInt().coerceIn(0, lastRow)
                                coroutineScope.launch {
                                    rowAnim.animateTo(target.toFloat(), spring(dampingRatio = 0.88f, stiffness = 210f))
                                }
                            }
                        }
                    }
                },
        ) {
            // Render every style as a card in its column and row
            columns.forEachIndexed { colIdx, rowList ->
                rowList.forEachIndexed { rowIdx, style ->
                    key(style) {
                        val rowPosition = rowAnimMap[colIdx]?.value ?: 0f
                        val leaving = rowLeaving(rowIdx, rowPosition)
                        val isVisible = geom.visible(colIdx, colPos) && geom.visibleY(rowIdx, rowPosition) && leaving > 0f

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = if (!isVisible) 0f else leaving
                                    scaleX = geom.scale
                                    scaleY = geom.scale
                                    translationX = geom.centerX(colIdx, colPos) - w / 2f
                                    translationY = geom.centerY(rowIdx, rowPosition) - h / 2f
                                    shape = RoundedCornerShape(34.dp / geom.scale)
                                    clip = true
                                }
                                .background(Color.Black),
                        ) {
                            PlayerStylePreviewCard(
                                style = style,
                                data = previewData,
                            )
                        }
                    }
                }
            }

            // A hairline round every card, and the focused column's variant dots
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 1.dp.toPx()
                val radius = 34.dp.toPx()
                val position = colAnim.value

                columns.forEachIndexed { colIdx, rowList ->
                    rowList.forEachIndexed { rowIdx, _ ->
                        val rowPosition = rowAnimMap[colIdx]?.value ?: 0f
                        if (!geom.visible(colIdx, position) || !geom.visibleY(rowIdx, rowPosition)) return@forEachIndexed
                        val cx = geom.centerX(colIdx, position)
                        val cy = geom.centerY(rowIdx, rowPosition)
                        val leaving = rowLeaving(rowIdx, rowPosition)
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.16f * leaving),
                            topLeft = Offset(cx - geom.cardW / 2f + stroke / 2f, cy - geom.cardH / 2f + stroke / 2f),
                            size = Size(geom.cardW - stroke, geom.cardH - stroke),
                            cornerRadius = CornerRadius(radius),
                            style = Stroke(stroke),
                        )
                    }
                }

                // Vertical dots beside the focused column when it has multiple variants
                val count = columns[focusedCol].size
                if (count > 1) {
                    val settled = (1f - abs(position - focusedCol) * 2f).coerceIn(0f, 1f)
                    val dot = 8.dp.toPx()
                    val step = 17.dp.toPx()
                    val x = (w - geom.cardW) / 4f
                    val top = geom.centerY - (count - 1) * step / 2f
                    val rowPosition = (rowAnimMap[focusedCol]?.value ?: 0f).coerceIn(0f, (count - 1).toFloat())
                    repeat(count) { i ->
                        val near = (1f - abs(rowPosition - i)).coerceIn(0f, 1f)
                        drawCircle(
                            color = Color.White.copy(alpha = (0.32f + 0.6f * near) * settled),
                            radius = dot / 2f,
                            center = Offset(x, top + i * step),
                        )
                    }
                }
            }

            // Scrim: the variant peeking in below fades into the dark under the buttons
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val from = restingBottomPx + 2.dp.toPx()
                        val scrim = Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.9f),
                            startY = from,
                            endY = from + 64.dp.toPx(),
                        )
                        onDrawBehind {
                            drawRect(scrim, topLeft = Offset(0f, from))
                        }
                    },
            )

            // Header: back button + "Styles" title + animated subtitle
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = statusTop + 10.dp, start = 22.dp, end = 22.dp)
                    .height(40.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { navController.popBackStack() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.back_button_desc),
                        tint = Color.White,
                        modifier = Modifier.size(15.dp).offset(x = 1.dp),
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.player_styles_title),
                        color = Color.White,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.05).em,
                        ),
                    )
                    AnimatedContent(
                        targetState = stringResource(focusedStyle.displayNameRes),
                        transitionSpec = {
                            (slideInVertically(tween(320)) { it } + fadeIn(tween(240))) togetherWith
                                (slideOutVertically(tween(280)) { -it } + fadeOut(tween(180)))
                        },
                        label = "selectorStyleName",
                    ) { name ->
                        Text(
                            text = name,
                            color = Color.White.copy(alpha = 0.6f),
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.05).em,
                            ),
                        )
                    }
                }
            }

            // Under the cards: page dots and buttons placed at restingBottomPx + 18.dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            p.placeWithLayer(0, (restingBottomPx + 18.dp.toPx()).roundToInt())
                        }
                    },
            ) {
                // Horizontal column dots
                Canvas(modifier = Modifier.size(width = (columnCount * 11).dp, height = 5.dp)) {
                    val dot = 5.dp.toPx()
                    val step = 11.dp.toPx()
                    val position = colAnim.value.coerceIn(0f, lastColumn.toFloat())
                    val start = (size.width - (columnCount - 1) * step - dot) / 2f
                    repeat(columnCount) { i ->
                        val near = (1f - abs(position - i)).coerceIn(0f, 1f)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.32f + 0.68f * near),
                            radius = dot / 2f,
                            center = Offset(start + i * step + dot / 2f, size.height / 2f),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                val editable = focusedStyle.hasOptions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CardActionButton(
                        label = "Edit",
                        enabled = editable,
                        dimmed = !editable,
                        onClick = { showConfigureSheet = true },
                        modifier = Modifier.weight(1f),
                    )
                    CardActionButton(
                        label = "Set",
                        enabled = true,
                        dimmed = false,
                        onClick = {
                            onSavedStyleChange(focusedStyle.id)
                            navController.popBackStack()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showConfigureSheet && focusedStyle.hasOptions) {
        PlayerStyleConfigureSheet(
            style = focusedStyle,
            onDismissRequest = { showConfigureSheet = false },
        )
    }
}

@Composable
private fun CardActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (dimmed) 0.06f else 0.15f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (dimmed) 0.35f else 1f),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.05).em,
            ),
        )
    }
}