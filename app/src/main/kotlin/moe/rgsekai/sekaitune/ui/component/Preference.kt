/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rgsekai.sekaitune.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.composed
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntSize
import moe.rgsekai.sekaitune.ui.utils.LocalIsTv
import moe.rgsekai.sekaitune.ui.utils.safeRequestFocus
import moe.rgsekai.sekaitune.ui.utils.tvFocusOutline
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.HISTORY_DURATION_DEFAULT
import moe.rgsekai.sekaitune.constants.HISTORY_DURATION_RANGE
import kotlin.math.roundToInt

val LocalPreferenceInGroup = compositionLocalOf { false }

enum class PreferenceGroupPosition { Single, First, Middle, Last }

val LocalPreferenceGroupPosition = compositionLocalOf<PreferenceGroupPosition?> { null }

val LocalTvSliderAdjustment = compositionLocalOf<((Boolean) -> Unit)?> { null }
val LocalTvSettingsCategoryFocusRequester = compositionLocalOf<FocusRequester?> { null }
val LocalTvSettingsRightPaneFocusRequester = compositionLocalOf<FocusRequester?> { null }
val LocalTvSettingsRightPaneFirstItemClaimed = compositionLocalOf { mutableStateOf(false) }
val LocalTvSettingsCurrentFocusedRequester = compositionLocalOf<MutableState<FocusRequester?>?> { null }
val LocalTvSettingsInSubScreen = compositionLocalOf { false }

class TvRightPaneFocusTracker {
    private val items = mutableMapOf<FocusRequester, Float>()
    var topItemFocusRequester by mutableStateOf<FocusRequester?>(null)
        private set
    var bottomItemFocusRequester by mutableStateOf<FocusRequester?>(null)
        private set

    fun register(requester: FocusRequester, y: Float) {
        items[requester] = y
        updateBounds()
    }

    fun unregister(requester: FocusRequester) {
        items.remove(requester)
        updateBounds()
    }

    private fun updateBounds() {
        if (items.isEmpty()) {
            topItemFocusRequester = null
            bottomItemFocusRequester = null
            return
        }
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var topReq: FocusRequester? = null
        var bottomReq: FocusRequester? = null
        for ((req, y) in items) {
            if (y < minY) {
                minY = y
                topReq = req
            }
            if (y > maxY) {
                maxY = y
                bottomReq = req
            }
        }
        topItemFocusRequester = topReq
        bottomItemFocusRequester = bottomReq
    }
}

val LocalTvRightPaneFocusTracker = compositionLocalOf<TvRightPaneFocusTracker?> { null }

/**
 * Modifier for TV Settings right-pane interactive items (action rows, switch rows, action buttons)
 * that integrates with [LocalTvRightPaneFocusTracker], [LocalTvSettingsCategoryFocusRequester],
 * [LocalTvSettingsRightPaneFocusRequester], and [tvFocusOutline].
 * Zero overhead / no-op on non-TV devices.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvSettingsItemFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    interactionSource: MutableInteractionSource? = null,
    customFocusRequester: FocusRequester? = null,
    onClick: (() -> Unit)? = null,
    isFirstItem: Boolean? = null,
    interceptDirectionLeft: Boolean = true,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    scaleOnFocus: Float = 1.0f,
): Modifier = composed {
    val isTv = LocalIsTv.current
    if (!isTv) return@composed this

    val leftCategoryFocusRequester = LocalTvSettingsCategoryFocusRequester.current
    val rightPaneFocusRequester = LocalTvSettingsRightPaneFocusRequester.current
    val firstItemClaimed = LocalTvSettingsRightPaneFirstItemClaimed.current
    val currentFocusedRequester = LocalTvSettingsCurrentFocusedRequester.current
    val focusTracker = LocalTvRightPaneFocusTracker.current
    val focusManager = LocalFocusManager.current

    val shouldAttachFirstItemRequester = remember(isTv, rightPaneFocusRequester, isFirstItem) {
        if (!isTv || rightPaneFocusRequester == null) {
            false
        } else if (isFirstItem == true) {
            firstItemClaimed.value = true
            true
        } else if (isFirstItem == false) {
            false
        } else if (!firstItemClaimed.value) {
            firstItemClaimed.value = true
            true
        } else {
            false
        }
    }

    DisposableEffect(shouldAttachFirstItemRequester) {
        onDispose {
            if (shouldAttachFirstItemRequester) {
                firstItemClaimed.value = false
            }
        }
    }

    val itemFocusRequester = customFocusRequester ?: remember { FocusRequester() }

    DisposableEffect(focusTracker, itemFocusRequester) {
        onDispose {
            focusTracker?.unregister(itemFocusRequester)
        }
    }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    var isFocused by remember { mutableStateOf(false) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val extraPaddingPx = remember(density) { with(density) { 36.dp.toPx() } }

    this
        .focusRequester(itemFocusRequester)
        .then(
            if (shouldAttachFirstItemRequester && rightPaneFocusRequester != null) {
                Modifier.focusRequester(rightPaneFocusRequester)
            } else {
                Modifier
            }
        )
        .onSizeChanged { itemSize = it }
        .onFocusChanged {
            isFocused = it.isFocused || it.hasFocus
            if (it.isFocused || it.hasFocus) {
                currentFocusedRequester?.value = itemFocusRequester
                coroutineScope.launch {
                    runCatching {
                        if (itemSize.height > 0) {
                            val rect = Rect(
                                left = 0f,
                                top = -extraPaddingPx,
                                right = itemSize.width.toFloat(),
                                bottom = itemSize.height.toFloat() + extraPaddingPx,
                            )
                            bringIntoViewRequester.bringIntoView(rect)
                        } else {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                }
            }
        }
        .onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (keyEvent.key) {
                Key.DirectionLeft -> {
                    if (onMoveLeft != null) {
                        onMoveLeft()
                        true
                    } else if (interceptDirectionLeft) {
                        if (leftCategoryFocusRequester != null) {
                            leftCategoryFocusRequester.safeRequestFocus()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                Key.DirectionRight -> {
                    if (onMoveRight != null) {
                        onMoveRight()
                        true
                    } else {
                        false
                    }
                }
                Key.DirectionUp -> {
                    if (onMoveUp != null) {
                        onMoveUp()
                        true
                    } else if (focusTracker != null && focusTracker.topItemFocusRequester == itemFocusRequester) {
                        true
                    } else if (shouldAttachFirstItemRequester) {
                        true
                    } else {
                        focusManager.moveFocus(FocusDirection.Up)
                        true
                    }
                }
                Key.DirectionDown -> {
                    if (onMoveDown != null) {
                        onMoveDown()
                        true
                    } else if (focusTracker != null && focusTracker.bottomItemFocusRequester == itemFocusRequester) {
                        true
                    } else {
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    }
                }
                Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.NumPadEnter -> {
                    if (onClick != null) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
        .zIndex(if (isFocused) 1f else 0f)
        .bringIntoViewRequester(bringIntoViewRequester)
        .onGloballyPositioned { coordinates ->
            focusTracker?.register(itemFocusRequester, coordinates.positionInRoot().y)
        }
        .tvFocusOutline(
            shape = shape,
            scaleOnFocus = scaleOnFocus,
            interactionSource = interactionSource,
        )
}

private val PreferenceGroupLargeCorner = 28.dp
private val PreferenceGroupSmallCorner = 6.dp
private val PreferenceGroupHorizontalPadding = 26.dp
private val PreferenceEntryMinHeight = 88.dp
private val PreferenceEntryHorizontalPadding = 22.dp
private val PreferenceEntryVerticalPadding = 18.dp

@Composable
private fun rememberPreferenceIconShape(): Shape = MaterialShapes.Ghostish.toShape()

private fun segmentedPreferenceItemShape(
    index: Int,
    count: Int,
): Shape {
    val large = PreferenceGroupLargeCorner
    val small = PreferenceGroupSmallCorner
    return when {
        count <= 1 -> {
            RoundedCornerShape(large)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = large,
                topEnd = large,
                bottomEnd = small,
                bottomStart = small,
            )
        }

        index == count - 1 -> {
            RoundedCornerShape(
                topStart = small,
                topEnd = small,
                bottomEnd = large,
                bottomStart = large,
            )
        }

        else -> {
            RoundedCornerShape(small)
        }
    }
}

private fun preferenceItemShapeForPosition(position: PreferenceGroupPosition?): Shape =
    when (position) {
        null,
        PreferenceGroupPosition.Single,
        -> segmentedPreferenceItemShape(index = 0, count = 1)

        PreferenceGroupPosition.First -> segmentedPreferenceItemShape(index = 0, count = 2)

        PreferenceGroupPosition.Middle -> segmentedPreferenceItemShape(index = 1, count = 3)

        PreferenceGroupPosition.Last -> segmentedPreferenceItemShape(index = 1, count = 2)
    }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PreferenceEntry(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    content: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    shape: Shape? = null,
) {
    val inGroup = LocalPreferenceInGroup.current
    val groupPosition = LocalPreferenceGroupPosition.current
    val preferenceIconShape = rememberPreferenceIconShape()
    val preferenceItemShape =
        remember(groupPosition) {
            preferenceItemShapeForPosition(groupPosition)
        }
    val resolvedShape = shape ?: preferenceItemShape
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "prefScale",
    )

    val isTv = LocalIsTv.current
    val leftCategoryFocusRequester = LocalTvSettingsCategoryFocusRequester.current
    val rightPaneFocusRequester = LocalTvSettingsRightPaneFocusRequester.current
    val firstItemClaimed = LocalTvSettingsRightPaneFirstItemClaimed.current
    val currentFocusedRequester = LocalTvSettingsCurrentFocusedRequester.current
    val focusTracker = LocalTvRightPaneFocusTracker.current
    val focusManager = LocalFocusManager.current
    val sliderAdjustment = LocalTvSliderAdjustment.current

    val shouldAttachFirstItemRequester = remember(isTv, rightPaneFocusRequester) {
        if (isTv && rightPaneFocusRequester != null && !firstItemClaimed.value) {
            firstItemClaimed.value = true
            true
        } else {
            false
        }
    }

    DisposableEffect(shouldAttachFirstItemRequester) {
        onDispose {
            if (shouldAttachFirstItemRequester) {
                firstItemClaimed.value = false
            }
        }
    }

    val itemFocusRequester = remember { FocusRequester() }

    DisposableEffect(focusTracker, itemFocusRequester) {
        onDispose {
            focusTracker?.unregister(itemFocusRequester)
        }
    }

    var isRowFocused by remember { mutableStateOf(false) }
    val isInteractionFocused by interactionSource.collectIsFocusedAsState()
    val isItemFocused = isTv && (isRowFocused || isInteractionFocused)

    val activeBorderColor = MaterialTheme.colorScheme.primary
    val focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isItemFocused) activeBorderColor else Color.Transparent,
        animationSpec = tween(150),
        label = "prefBorderColor",
    )
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isItemFocused) focusedContainerColor else unfocusedContainerColor,
        animationSpec = tween(150),
        label = "prefContainerColor",
    )
    val animatedTvScale by animateFloatAsState(
        targetValue = if (isItemFocused) 1.02f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "prefTvScale",
    )

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    var prefItemSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val extraPaddingPx = remember(density) { with(density) { 36.dp.toPx() } }

    LaunchedEffect(isItemFocused) {
        if (isItemFocused) {
            runCatching {
                if (prefItemSize.height > 0) {
                    val rect = Rect(
                        left = 0f,
                        top = -extraPaddingPx,
                        right = prefItemSize.width.toFloat(),
                        bottom = prefItemSize.height.toFloat() + extraPaddingPx,
                    )
                    bringIntoViewRequester.bringIntoView(rect)
                } else {
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }
    }

    val tvModifier = if (isTv) {
        Modifier
            .focusRequester(itemFocusRequester)
            .then(
                if (shouldAttachFirstItemRequester && rightPaneFocusRequester != null) {
                    Modifier.focusRequester(rightPaneFocusRequester)
                } else {
                    Modifier
                }
            )
            .onSizeChanged { prefItemSize = it }
            .onFocusChanged {
                isRowFocused = it.isFocused || it.hasFocus
                if (it.isFocused || it.hasFocus) {
                    currentFocusedRequester?.value = itemFocusRequester
                    coroutineScope.launch {
                        runCatching {
                            if (prefItemSize.height > 0) {
                                val rect = Rect(
                                    left = 0f,
                                    top = -extraPaddingPx,
                                    right = prefItemSize.width.toFloat(),
                                    bottom = prefItemSize.height.toFloat() + extraPaddingPx,
                                )
                                bringIntoViewRequester.bringIntoView(rect)
                            } else {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    }
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        if (sliderAdjustment != null) {
                            sliderAdjustment(false)
                            true
                        } else if (leftCategoryFocusRequester != null) {
                            leftCategoryFocusRequester.safeRequestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionRight -> {
                        if (sliderAdjustment != null) {
                            sliderAdjustment(true)
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp -> {
                        if (focusTracker != null && focusTracker.topItemFocusRequester == itemFocusRequester) {
                            true
                        } else if (shouldAttachFirstItemRequester) {
                            true
                        } else {
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
                    }
                    Key.DirectionDown -> {
                        if (focusTracker != null && focusTracker.bottomItemFocusRequester == itemFocusRequester) {
                            true
                        } else {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                    }
                    Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.NumPadEnter -> {
                        if (isEnabled && onClick != null) {
                            onClick()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
    } else {
        Modifier
    }

    val rowContent: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PreferenceEntryMinHeight)
                    .then(tvModifier)
                    .then(
                        if (onLongClick != null) {
                            Modifier.combinedClickable(
                                interactionSource = interactionSource,
                                indication = if (isTv) null else LocalIndication.current,
                                enabled = isEnabled && (onClick != null || onLongClick != null),
                                onClick = onClick ?: {},
                                onLongClick = onLongClick,
                            )
                        } else if (onClick != null) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = if (isTv) null else LocalIndication.current,
                                enabled = isEnabled,
                                onClick = onClick,
                            )
                        } else if (isTv && sliderAdjustment != null) {
                            Modifier.focusable(enabled = isEnabled, interactionSource = interactionSource)
                        } else if (!isTv && isEnabled && (onClick != null || onLongClick != null)) {
                            Modifier.focusable()
                        } else {
                            Modifier
                        }
                    ).alpha(if (isEnabled) 1f else 0.5f)
                    .padding(
                        horizontal = PreferenceEntryHorizontalPadding,
                        vertical = PreferenceEntryVerticalPadding,
                    ),
        ) {
            if (icon != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterVertically)
                            .size(44.dp)
                            .clip(preferenceIconShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                        icon()
                    }
                }
                Spacer(Modifier.width(16.dp))
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) {
                    title()
                }
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content?.invoke()
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(16.dp))
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    trailingContent()
                }
            }
        }
    }

    Card(
        shape = resolvedShape,
        colors =
            CardDefaults.cardColors(
                containerColor = if (isTv) animatedContainerColor else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (isTv && (isItemFocused || animatedBorderColor != Color.Transparent)) {
            BorderStroke(2.5.dp, animatedBorderColor)
        } else {
            null
        },
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (inGroup) 0.dp else 16.dp,
                    vertical = if (inGroup) 0.dp else 3.dp,
                )
                .then(
                    if (isTv) {
                        Modifier
                            .zIndex(if (isItemFocused) 1f else 0f)
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .onGloballyPositioned { coordinates ->
                                focusTracker?.register(itemFocusRequester, coordinates.positionInRoot().y)
                            }
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    val currentScale = if (isTv) animatedTvScale else scale
                    scaleX = currentScale
                    scaleY = currentScale
                },
    ) {
        rowContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    val isTv = LocalIsTv.current
    val preferenceContent = @Composable {
        PreferenceEntry(
            modifier = modifier,
            title = title,
            description = description,
            icon = icon,
            isEnabled = isEnabled,
            content = {
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().then(
                        if (isTv) Modifier.focusProperties { canFocus = false } else Modifier
                    ),
                ) {
                    values.forEachIndexed { index, value ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = values.size),
                            onClick = { onValueSelected(value) },
                            selected = value == selectedValue,
                            enabled = isEnabled,
                            colors =
                                SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                        ) {
                            Text(
                                text = valueText(value),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            },
        )
    }

    if (isTv) {
        CompositionLocalProvider(
            LocalTvSliderAdjustment provides { increase ->
                val currentIndex = values.indexOf(selectedValue)
                if (currentIndex != -1) {
                    val nextIndex =
                        if (increase) {
                            (currentIndex + 1).coerceAtMost(values.size - 1)
                        } else {
                            (currentIndex - 1).coerceAtLeast(0)
                        }
                    if (nextIndex != currentIndex) {
                        onValueSelected(values[nextIndex])
                    }
                }
            },
        ) {
            preferenceContent()
        }
    } else {
        preferenceContent()
    }
}

@Composable
inline fun <reified T : Enum<T>> EnumSegmentedPreference(
    modifier: Modifier = Modifier,
    noinline title: @Composable () -> Unit,
    description: String? = null,
    noinline icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    noinline valueText: @Composable (T) -> String,
    noinline onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    val values = remember { enumValues<T>().toList() }

    SegmentedPreference(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        selectedValue = selectedValue,
        values = values,
        valueText = valueText,
        onValueSelected = onValueSelected,
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ListPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    valueDescription: (@Composable (T) -> String)? = null,
    isValueEnabled: (T) -> Boolean = { true },
    onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    var showBottomSheet by remember {
        mutableStateOf(false)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    if (showBottomSheet) {
        PreferenceSelectionBottomSheet(
            title = title,
            values = values,
            selectedValue = selectedValue,
            valueText = valueText,
            valueDescription = valueDescription,
            isValueEnabled = isValueEnabled,
            sheetState = sheetState,
            onDismiss = { showBottomSheet = false },
            onValueSelected = { value ->
                onValueSelected(value)
                coroutineScope
                    .launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        showBottomSheet = false
                    }
            },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        content = {
            Spacer(Modifier.height(10.dp))
            PreferenceValueChip(valueText(selectedValue))
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        },
        onClick = { showBottomSheet = true },
        isEnabled = isEnabled,
    )
}

@Composable
inline fun <reified T : Enum<T>> EnumListPreference(
    modifier: Modifier = Modifier,
    noinline title: @Composable () -> Unit,
    description: String? = null,
    noinline icon: (@Composable () -> Unit)?,
    selectedValue: T,
    values: List<T> = remember { enumValues<T>().toList() },
    noinline valueText: @Composable (T) -> String,
    noinline valueDescription: (@Composable (T) -> String)? = null,
    noinline onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    ListPreference(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        selectedValue = selectedValue,
        values = values,
        valueText = valueText,
        valueDescription = valueDescription,
        onValueSelected = onValueSelected,
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PreferenceSelectionBottomSheet(
    title: @Composable () -> Unit,
    values: List<T>,
    selectedValue: T,
    valueText: @Composable (T) -> String,
    valueDescription: (@Composable (T) -> String)? = null,
    isValueEnabled: (T) -> Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onValueSelected: (T) -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 48.dp,
                height = 5.dp,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .padding(bottom = 12.dp),
        ) {
            ProvideTextStyle(
                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, bottom = 22.dp),
                ) {
                    title()
                }
            }

            val isTv = LocalIsTv.current
            val selectedOptionFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (isTv) {
                    selectedOptionFocusRequester.safeRequestFocus()
                }
            }

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(
                    items = values,
                    key = { index, value -> preferenceOptionKey(index, value) },
                    contentType = { _, _ -> "preference_option" },
                ) { _, value ->
                    PreferenceSelectionOption(
                        text = valueText(value),
                        description = valueDescription?.invoke(value),
                        selected = value == selectedValue,
                        enabled = isValueEnabled(value),
                        onClick = { onValueSelected(value) },
                        modifier = if (isTv && value == selectedValue) Modifier.focusRequester(selectedOptionFocusRequester) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceSelectionOption(
    text: String,
    description: String? = null,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.isEnabledFor(GlassComponent.POPUP_MENU)
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val shape = MaterialTheme.shapes.extraLarge

    val containerColor =
        when {
            selected && isGlass -> adjustColorVibrancy(MaterialTheme.colorScheme.primary, glassConfig.vibrancy).copy(alpha = 0.82f)
            selected -> MaterialTheme.colorScheme.primary
            isGlass -> Color.Transparent
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val descriptionColor =
        if (selected) contentColor.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant

    val rimColor =
        when {
            selected && isGlass -> Color.White.copy(alpha = 0.35f)
            isGlass -> if (isLight) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.14f)
            else -> null
        }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = containerColor,
        border = if (isGlass && rimColor != null) BorderStroke(1.dp, rimColor) else null,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = if (description == null) 72.dp else 96.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .then(
                    if (isGlass) {
                        Modifier.liquidGlass(
                            config = glassConfig,
                            shape = shape,
                            applyEdgeEffects = true,
                        )
                    } else {
                        Modifier
                    },
                ).then(
                    if (LocalIsTv.current) {
                        Modifier.tvFocusOutline(shape = shape)
                    } else {
                        Modifier
                    },
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = descriptionColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (selected) {
                Spacer(Modifier.width(16.dp))
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun PreferenceValueChip(text: String) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun preferenceOptionKey(
    index: Int,
    value: Any?,
): String {
    val valueKey =
        when (value) {
            is Enum<*> -> value.name
            null -> "null"
            else -> value.hashCode().toString()
        }
    return "${value?.javaClass?.name.orEmpty()}:$valueKey:$index"
}

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean = true,
) {
    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = isEnabled,
                modifier = if (LocalIsTv.current) Modifier.focusProperties { canFocus = false } else Modifier,
                thumbContent = {
                    AnimatedContent(
                        targetState = checked,
                        transitionSpec = {
                            fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                        },
                        label = "switchThumbIcon",
                    ) { isChecked ->
                        Icon(
                            painter =
                                painterResource(
                                    id = if (isChecked) R.drawable.check else R.drawable.close,
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                },
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedIconColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        },
        onClick = { onCheckedChange(!checked) },
        isEnabled = isEnabled,
    )
}

@Composable
fun EditTextPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    if (showDialog) {
        TextFieldDialog(
            title = title,
            initialTextFieldValue =
                TextFieldValue(
                    text = value,
                    selection = TextRange(value.length),
                ),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            isInputValid = isInputValid,
            onDone = onValueChange,
            onDismiss = { showDialog = false },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = value,
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
fun NumberEditTextPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    isInputValid: (String) -> Boolean = { it.toIntOrNull() != null },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    if (showDialog) {
        TextFieldDialog(
            title = title,
            initialTextFieldValue =
                TextFieldValue(
                    text = value.toString(),
                    selection = TextRange(value.toString().length),
                ),
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            isInputValid = isInputValid,
            onDone = { it.toIntOrNull()?.let(onValueChange) },
            onDismiss = { showDialog = false },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = value.toString(),
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember(value) {
        mutableFloatStateOf(value.toFloat())
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.history_duration),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                showDialog = false
                onValueChange.invoke(sliderValue.roundToInt())
            },
            onCancel = {
                sliderValue = value.toFloat()
                showDialog = false
            },
            onReset = {
                sliderValue = HISTORY_DURATION_DEFAULT.toFloat()
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.seconds,
                                sliderValue.roundToInt(),
                                sliderValue.roundToInt(),
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.history_duration_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    Spacer(Modifier.height(16.dp))

                    val sliderState =
                        rememberSliderState(
                            value = sliderValue,
                            valueRange = HISTORY_DURATION_RANGE,
                            onValueChangeFinished = {},
                        )
                    sliderState.onValueChange = { sliderValue = it }
                    sliderState.value = sliderValue

                    Slider(
                        state = sliderState,
                        modifier = Modifier.fillMaxWidth(),
                        track = {
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                trackCornerSize = 12.dp,
                            )
                        },
                    )
                }
            },
        )
    }

    val isTv = LocalIsTv.current
    if (isTv) {
        val minVal = HISTORY_DURATION_RANGE.start.toInt()
        val maxVal = HISTORY_DURATION_RANGE.endInclusive.toInt()
        CompositionLocalProvider(
            LocalTvSliderAdjustment provides { increase ->
                val next = if (increase) (value + 1).coerceAtMost(maxVal) else (value - 1).coerceAtLeast(minVal)
                if (next != value) {
                    onValueChange(next)
                }
            },
        ) {
            PreferenceEntry(
                modifier = modifier,
                title = title,
                description = stringResource(R.string.history_duration_seconds, value),
                icon = icon,
                onClick = { showDialog = true },
                isEnabled = isEnabled,
            )
        }
    } else {
        PreferenceEntry(
            modifier = modifier,
            title = title,
            description = stringResource(R.string.history_duration_seconds, value),
            icon = icon,
            onClick = { showDialog = true },
            isEnabled = isEnabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossfadeSliderPreference(
    modifier: Modifier = Modifier,
    valueSeconds: Float,
    onValueChange: (Float) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember {
        mutableFloatStateOf(valueSeconds.coerceIn(0f, 10f))
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.audio_crossfade_dialog_title),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                val rounded =
                    ((sliderValue * 2f).roundToInt().toFloat() / 2f)
                        .coerceIn(0f, 10f)
                sliderValue = rounded
                showDialog = false
                onValueChange.invoke(rounded)
            },
            onCancel = {
                sliderValue = valueSeconds.coerceIn(0f, 10f)
                showDialog = false
            },
            onReset = {
                sliderValue = 5f
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val rounded =
                        ((sliderValue * 2f).roundToInt().toFloat() / 2f)
                            .coerceIn(0f, 10f)
                    val isWhole =
                        (rounded - rounded.roundToInt().toFloat()).let { delta ->
                            kotlin.math.abs(delta) < 0.001f
                        }
                    val displayValue =
                        if (isWhole) rounded.roundToInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", rounded)
                    Text(
                        text =
                            if (rounded <= 0f) {
                                stringResource(R.string.dark_theme_off)
                            } else {
                                stringResource(R.string.audio_crossfade_seconds, displayValue)
                            },
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.audio_crossfade_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    Spacer(Modifier.height(16.dp))

                    val crossfadeSliderState =
                        rememberSliderState(
                            value = sliderValue,
                            steps = 19,
                            valueRange = 0f..10f,
                            onValueChangeFinished = {},
                        )
                    crossfadeSliderState.onValueChange = { sliderValue = it.coerceIn(0f, 10f) }
                    crossfadeSliderState.value = sliderValue

                    Slider(
                        state = crossfadeSliderState,
                        modifier = Modifier.fillMaxWidth(),
                        track = {
                            SliderDefaults.Track(
                                sliderState = crossfadeSliderState,
                                trackCornerSize = 12.dp,
                            )
                        },
                    )
                }
            },
        )
    }

    val rounded =
        ((valueSeconds * 2f).roundToInt().toFloat() / 2f)
            .coerceIn(0f, 10f)
    val isWhole =
        (rounded - rounded.roundToInt().toFloat()).let { delta ->
            kotlin.math.abs(delta) < 0.001f
        }
    val displayValue =
        if (isWhole) rounded.roundToInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", rounded)
    val descriptionText =
        if (rounded <= 0f) {
            stringResource(R.string.dark_theme_off)
        } else {
            stringResource(R.string.audio_crossfade_seconds, displayValue)
        }

    val isTv = LocalIsTv.current
    if (isTv) {
        CompositionLocalProvider(
            LocalTvSliderAdjustment provides { increase ->
                val step = 0.5f
                val next = if (increase) (valueSeconds + step).coerceAtMost(10f) else (valueSeconds - step).coerceAtLeast(0f)
                val rounded = ((next * 2f).roundToInt().toFloat() / 2f).coerceIn(0f, 10f)
                if (kotlin.math.abs(rounded - valueSeconds) > 0.01f) {
                    onValueChange(rounded)
                }
            },
        ) {
            PreferenceEntry(
                modifier = modifier,
                title = { Text(stringResource(R.string.audio_crossfade_title)) },
                description = descriptionText,
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                onClick = { if (isEnabled) showDialog = true },
                isEnabled = isEnabled,
            )
        }
    } else {
        PreferenceEntry(
            modifier = modifier,
            title = { Text(stringResource(R.string.audio_crossfade_title)) },
            description = descriptionText,
            icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
            onClick = { if (isEnabled) showDialog = true },
            isEnabled = isEnabled,
        )
    }
}

@Composable
fun NumberPickerPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int = 0,
    maxValue: Int = 10,
    valueText: (Int) -> String = { it.toString() },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember {
        mutableFloatStateOf(value.toFloat())
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    title()
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                val rounded = sliderValue.roundToInt().coerceIn(minValue, maxValue)
                sliderValue = rounded.toFloat()
                showDialog = false
                onValueChange.invoke(rounded)
            },
            onCancel = {
                sliderValue = value.toFloat()
                showDialog = false
            },
            onReset = {
                sliderValue = minValue.toFloat()
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val rounded = sliderValue.roundToInt().coerceIn(minValue, maxValue)
                    Text(
                        text = valueText(rounded),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(16.dp))

                    val pickerSliderState =
                        rememberSliderState(
                            value = sliderValue,
                            steps = maxValue - minValue - 1,
                            valueRange = minValue.toFloat()..maxValue.toFloat(),
                            onValueChangeFinished = {},
                        )
                    pickerSliderState.onValueChange = {
                        sliderValue = it.coerceIn(minValue.toFloat(), maxValue.toFloat())
                    }
                    pickerSliderState.value = sliderValue

                    Slider(
                        state = pickerSliderState,
                        modifier = Modifier.fillMaxWidth(),
                        track = {
                            SliderDefaults.Track(
                                sliderState = pickerSliderState,
                                trackCornerSize = 12.dp,
                            )
                        },
                    )
                }
            },
        )
    }

    val isTv = LocalIsTv.current
    if (isTv) {
        CompositionLocalProvider(
            LocalTvSliderAdjustment provides { increase ->
                val next = if (increase) (value + 1).coerceAtMost(maxValue) else (value - 1).coerceAtLeast(minValue)
                if (next != value) {
                    onValueChange(next)
                }
            },
        ) {
            PreferenceEntry(
                modifier = modifier,
                title = title,
                description = valueText(value),
                icon = icon,
                onClick = { if (isEnabled) showDialog = true },
                isEnabled = isEnabled,
            )
        }
    } else {
        PreferenceEntry(
            modifier = modifier,
            title = title,
            description = valueText(value),
            icon = icon,
            onClick = { if (isEnabled) showDialog = true },
            isEnabled = isEnabled,
        )
    }
}

class PreferenceGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(
        visible: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        if (visible) {
            items.add(content)
        }
    }
}

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: PreferenceGroupScope.() -> Unit,
) {
    val scope = PreferenceGroupScope().apply(content)
    val itemCount = scope.items.size

    if (itemCount == 0) return

    Column(modifier = modifier) {
        if (title != null) {
            PreferenceGroupTitle(
                title = title,
                modifier = Modifier.padding(horizontal = PreferenceGroupHorizontalPadding),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PreferenceGroupHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            scope.items.forEachIndexed { index, itemContent ->
                val position =
                    when {
                        itemCount == 1 -> PreferenceGroupPosition.Single
                        index == 0 -> PreferenceGroupPosition.First
                        index == itemCount - 1 -> PreferenceGroupPosition.Last
                        else -> PreferenceGroupPosition.Middle
                    }
                CompositionLocalProvider(
                    LocalPreferenceInGroup provides true,
                    LocalPreferenceGroupPosition provides position,
                ) {
                    itemContent()
                }
            }
        }
    }
}

@Composable
fun PreferenceGroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 60.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
}

@Composable
fun PreferenceGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}




