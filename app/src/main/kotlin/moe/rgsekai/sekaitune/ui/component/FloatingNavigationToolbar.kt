/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.screens.Screens
import java.util.Random
import kotlin.math.abs

/** Fine, faint grain softens banding without black/white speckles. Generated once and shared. */
private object FrostedGrain {
    val brush: ShaderBrush by lazy {
        val random = Random(41L)
        val pixels = IntArray(128 * 128) {
            val value = (128 + random.nextGaussian() * 18).toInt().coerceIn(96, 160)
            (12 shl 24) or (value shl 16) or (value shl 8) or value
        }
        val texture = Bitmap.createBitmap(pixels, 128, 128, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        ShaderBrush(shader)
    }
}

@Composable
private fun isLightPalette(pureBlack: Boolean): Boolean {
    if (pureBlack) return false
    return MaterialTheme.colorScheme.surface.luminance() > 0.5f
}

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    onShuffleClick: (() -> Unit)? = null,
    shuffleIconRes: Int? = null,
    shuffleContentDescription: String = "",
    onMusicRecognitionClick: (() -> Unit)? = null,
    musicRecognitionContentDescription: String = "",
    onMusicTogetherClick: (() -> Unit)? = null,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val isLight = isLightPalette(pureBlack)
    val glassConfig = LocalGlassEffectConfig.current

    // Always enable liquid glass blur for the floating navigation bar when supported on device
    val effectiveGlassConfig = remember(glassConfig) {
        glassConfig.copy(
            globalEnabled = true,
            navBarEnabled = true,
            blurRadius = if (glassConfig.blurRadius > 0f) glassConfig.blurRadius else 16f,
            vibrancy = if (glassConfig.vibrancy > 0f) glassConfig.vibrancy else 95f,
            lensHeight = 35f,
            lensAmount = 25f,
            surfaceOpacity = 20f,
        )
    }

    val barShape = RoundedCornerShape(percent = 50)
    val containerColor = when {
        pureBlack -> Color(0xFF000000).copy(alpha = 0.85f)
        isLight -> Color(0xFFF7F7F9).copy(alpha = 0.78f)
        else -> Color(0xFF101014).copy(alpha = 0.74f)
    }

    // Specular light sheen across top of the curved glass
    val glossBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = if (isLight) 0.18f else 0.15f),
        0.35f to Color.White.copy(alpha = if (isLight) 0.05f else 0.03f),
        0.65f to Color.Transparent,
    )

    // 3D glass border with specular highlight at the top edge
    val glassBorderBrush = Brush.verticalGradient(
        0.0f to (if (isLight) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.32f)),
        0.4f to (if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f)),
        1.0f to (if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.08f)),
    )

    val pillHighlightColor = if (isLight) Color.Black.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.15f)

    val mainItems = remember(items) { items.filterNot { it == Screens.Search } }
    val isSearchSelected = isSelected(Screens.Search)
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                val searchInteractionSource = remember { MutableInteractionSource() }
                val isSearchPressed by searchInteractionSource.collectIsPressedAsState()
                val searchPressScale by animateFloatAsState(
                    targetValue = if (isSearchPressed) 0.93f else 1f,
                    animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
                    label = "searchPressScale",
                )
                val searchGlyphScale by animateFloatAsState(
                    targetValue = if (isSearchSelected) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
                    label = "searchGlyphScale",
                )
                val searchContentColor = if (isSearchSelected) {
                    if (isLight) Color.Black else Color.White
                } else {
                    if (isLight) Color(0xFF6E6E73) else Color(0xFF8E8E93)
                }
                val searchButtonShape = CircleShape

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(searchPressScale)
                        .clip(searchButtonShape)
                        .then(
                            if (isGlassSupported()) {
                                Modifier.liquidGlass(
                                    config = effectiveGlassConfig,
                                    shape = searchButtonShape,
                                    applyEdgeEffects = true,
                                )
                            } else {
                                Modifier
                            }
                        )
                        .background(containerColor, searchButtonShape)
                        .drawBehind {
                            drawRect(glossBrush)
                            drawRect(FrostedGrain.brush)
                            if (isSearchSelected) {
                                drawRect(pillHighlightColor)
                            }
                        }
                        .border(0.5.dp, glassBorderBrush, searchButtonShape)
                        .combinedClickable(
                            interactionSource = searchInteractionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onItemClick(Screens.Search, isSearchSelected)
                            },
                            onDoubleClick = onSearchItemDoubleClick?.let { doubleClick ->
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    doubleClick()
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = stringResource(R.string.search),
                        tint = searchContentColor,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = searchGlyphScale
                                scaleY = searchGlyphScale
                            },
                    )
                }
            },
            modifier = Modifier.widthIn(max = 480.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = Color.Transparent,
            ),
            contentPadding = PaddingValues(0.dp),
            scrollBehavior = scrollBehavior,
            animationSpec = FloatingToolbarDefaults.animationSpec(),
        ) {
            Box(
                modifier = Modifier
                    .clip(barShape)
                    .then(
                        if (isGlassSupported()) {
                            Modifier.liquidGlass(
                                config = effectiveGlassConfig,
                                shape = barShape,
                                applyEdgeEffects = true,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .background(containerColor, barShape)
                    .drawBehind {
                        drawRect(glossBrush)
                        drawRect(FrostedGrain.brush)
                    }
                    .border(0.5.dp, glassBorderBrush, barShape)
                    .padding(vertical = 4.dp, horizontal = 5.dp),
            ) {
                ToolbarItemsContainer(
                    items = mainItems,
                    isLight = isLight,
                    pureBlack = pureBlack,
                    pillHighlightColor = pillHighlightColor,
                    isSelected = isSelected,
                    onItemClick = onItemClick,
                    onSearchItemDoubleClick = onSearchItemDoubleClick,
                )
            }
        }
    }
}

@Composable
private fun ToolbarItemsContainer(
    items: List<Screens>,
    isLight: Boolean,
    pureBlack: Boolean,
    pillHighlightColor: Color,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)?,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val currentSelectedScreen = items.firstOrNull { isSelected(it) }
    var optimisticScreen by remember { mutableStateOf<Screens?>(null) }

    LaunchedEffect(currentSelectedScreen) {
        optimisticScreen = null
    }

    val activeScreen = optimisticScreen ?: currentSelectedScreen
    val selectedIndex = items.indexOf(activeScreen)

    // TeleMusic's signature physics-driven spring indicator
    val indicator = animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 320f,
        ),
        label = "tabIndicator",
    )

    val tabWidth = 68.dp
    val gap = 4.dp
    val stride = with(density) { (tabWidth + gap).toPx() }
    val tabHeight = 52.dp

    Box(
        modifier = Modifier
            .wrapContentSize()
            .height(tabHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (selectedIndex >= 0) {
            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = (indicator.value * stride) * if (rtl) -1f else 1f
                        val lag = abs(selectedIndex - indicator.value).coerceIn(0f, 1f)
                        scaleX = 1f + lag * 0.16f
                        scaleY = 1f - lag * 0.08f
                    }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(pillHighlightColor),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items.forEach { screen ->
                val selected = screen == activeScreen
                val onClick = remember(screen, selected, onItemClick) {
                    {
                        optimisticScreen = screen
                        onItemClick(screen, selected)
                    }
                }
                val onDoubleClick = remember(screen, onSearchItemDoubleClick) {
                    if (screen == Screens.Search) onSearchItemDoubleClick else null
                }
                FloatingNavigationToolbarItem(
                    screen = screen,
                    selected = selected,
                    isLight = isLight,
                    pureBlack = pureBlack,
                    onClick = onClick,
                    onDoubleClick = onDoubleClick,
                    modifier = Modifier.width(tabWidth),
                )
            }
        }
    }
}

@Composable
private fun FloatingNavigationToolbarItem(
    screen: Screens,
    selected: Boolean,
    isLight: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = 500f,
        ),
        label = "tabPress",
    )
    val glyphScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 320f,
        ),
        label = "tabGlyph",
    )

    val contentColor = if (selected) {
        if (isLight) Color.Black else Color.White
    } else {
        if (isLight) Color(0xFF6E6E73) else Color(0xFF8E8E93)
    }
    val haptic = LocalHapticFeedback.current

    // Display "New" instead of "New releases" as requested
    val labelText = if (screen == Screens.NewRelease) "New" else stringResource(screen.titleId)

    Column(
        modifier = modifier
            .scale(pressScale)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onDoubleClick = onDoubleClick?.let { doubleClick ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        doubleClick()
                    }
                },
            )
            .semantics { this.selected = selected }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
            contentDescription = labelText,
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = glyphScale
                    scaleY = glyphScale
                },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = labelText,
            color = contentColor,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FloatingToolbarOverflowAction(
    pureBlack: Boolean,
    onShuffleClick: (() -> Unit)?,
    shuffleIconRes: Int?,
    shuffleContentDescription: String,
    onMusicRecognitionClick: (() -> Unit)?,
    musicRecognitionContentDescription: String,
    onMusicTogetherClick: (() -> Unit)?,
) {
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Box {
        FloatingToolbarDefaults.VibrantFloatingActionButton(
            onClick = { fabMenuExpanded = !fabMenuExpanded },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription =
                    shuffleContentDescription.ifEmpty {
                        stringResource(R.string.more)
                    },
            )
        }

        GlassDropdownMenu(
            expanded = fabMenuExpanded,
            onDismissRequest = { fabMenuExpanded = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_together)) },
                onClick = {
                    fabMenuExpanded = false
                    onMusicTogetherClick?.invoke()
                },
                leadingIcon = {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = floatingToolbarMenuIconContainerColor(pureBlack = pureBlack),
                        contentColor = floatingToolbarMenuIconContentColor(pureBlack = pureBlack),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.multi_user),
                                contentDescription = null,
                            )
                        }
                    }
                },
                enabled = onMusicTogetherClick != null,
                colors =
                    MenuDefaults.itemColors(
                        textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = if (pureBlack) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTextColor =
                            if (pureBlack) {
                                Color.White.copy(
                                    alpha = 0.38f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        disabledLeadingIconColor =
                            if (pureBlack) {
                                Color.White.copy(
                                    alpha = 0.38f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                    ),
            )

            if (onShuffleClick != null && shuffleIconRes != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shuffle)) },
                    onClick = {
                        fabMenuExpanded = false
                        onShuffleClick()
                    },
                    leadingIcon = {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = floatingToolbarMenuIconContainerColor(pureBlack = pureBlack),
                            contentColor = floatingToolbarMenuIconContentColor(pureBlack = pureBlack),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(shuffleIconRes),
                                    contentDescription =
                                        shuffleContentDescription.ifEmpty {
                                            stringResource(R.string.shuffle)
                                        },
                                )
                            }
                        }
                    },
                    colors =
                        MenuDefaults.itemColors(
                            textColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface,
                            leadingIconColor =
                                if (pureBlack) {
                                    Color.White.copy(
                                        alpha = 0.82f,
                                    )
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        ),
                )
            }
        }
    }
}

@Composable
private fun floatingToolbarMenuIconContainerColor(pureBlack: Boolean): Color =
    if (pureBlack) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer

@Composable
private fun floatingToolbarMenuIconContentColor(pureBlack: Boolean): Color =
    if (pureBlack) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
