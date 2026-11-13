package moe.rgsekai.sekaitune.ui.player.styles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import androidx.compose.foundation.layout.navigationBarsPadding
import moe.rgsekai.sekaitune.constants.PlayerDesignStyleKey
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.abs

/**
 * Full-screen Styles Carousel picker screen opened from Settings > Appearance.
 *
 * Rules:
 * - Always opens without media locks or toasts.
 * - Solid surfaces from app theme; zero glass/blur effects.
 * - HorizontalPager with beyondViewportPageCount = 1 (only 3 pages max in composition).
 * - Smooth spring settle, 38.dp card corners, ~0.72 card scale.
 * - Page dots below cards.
 * - Header with back button, "Styles" title, and animated subtitle showing active style name.
 * - Bottom action bar: Set button (Configure added in Phase 3).
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

    val styles = remember { PlayerStyle.entries }
    val initialIndex = remember(savedStyleId) {
        val style = PlayerStyle.fromPreference(savedStyleId)
        styles.indexOf(style).coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { styles.size },
    )

    val previewData = rememberPlayerPreviewData()
    val focusedStyle = styles[pagerState.currentPage]
    val coroutineScope = rememberCoroutineScope()
    var showConfigureSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Header: Back Button + Title + Animated Subtitle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 6.dp)
                    .height(42.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)),
                ) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(
                        text = stringResource(R.string.player_styles_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    AnimatedContent(
                        targetState = stringResource(focusedStyle.displayNameRes),
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "styleSubtitleTransition",
                    ) { styleName ->
                        Text(
                            text = styleName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Carousel Pager: card scale 0.72f, 34.dp corners, 18.dp gap like reference
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val horizontalPadding = (maxWidth * 0.14f).coerceAtLeast(32.dp)

                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    pageSpacing = 18.dp,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val absOffset = abs(pageOffset).coerceIn(0f, 1f)
                    val cardScale = 0.72f + (0.28f * (1f - absOffset))

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                                alpha = 0.45f + (0.55f * (1f - absOffset))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.98f)
                                .shadow(8.dp, RoundedCornerShape(34.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    RoundedCornerShape(34.dp),
                                ),
                            shape = RoundedCornerShape(34.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            PlayerStylePreviewCard(
                                style = styles[page],
                                data = previewData,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Carousel Page Dots
            StylesPageDots(
                count = styles.size,
                currentPage = pagerState.currentPage,
                offsetFraction = pagerState.currentPageOffsetFraction,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )

            Spacer(Modifier.height(18.dp))

            // Bottom Action Bar: Adaptive Configure + Set Buttons (40.dp height, pill shape)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (focusedStyle.hasOptions) {
                    FilledTonalButton(
                        onClick = { showConfigureSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.player_styles_action_configure),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Button(
                    onClick = {
                        onSavedStyleChange(focusedStyle.id)
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .then(if (focusedStyle.hasOptions) Modifier.weight(1f) else Modifier.fillMaxWidth())
                        .height(44.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.player_styles_action_set),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (showConfigureSheet && focusedStyle.hasOptions) {
                PlayerStyleConfigureSheet(
                    style = focusedStyle,
                    onDismissRequest = { showConfigureSheet = false },
                )
            }
        }
    }
}

@Composable
private fun StylesPageDots(
    count: Int,
    currentPage: Int,
    offsetFraction: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val dotRadius = 4.dp.toPx()
        val spacing = 16.dp.toPx()
        val totalWidth = (count - 1) * spacing
        val startX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f

        for (i in 0 until count) {
            val cx = startX + i * spacing
            val dist = abs(currentPage + offsetFraction - i)
            val proximity = (1f - dist).coerceIn(0f, 1f)

            val color = if (proximity > 0.5f) {
                activeColor.copy(alpha = 0.35f + 0.65f * proximity)
            } else {
                inactiveColor
            }

            drawCircle(
                color = color,
                radius = dotRadius * (1f + 0.3f * proximity),
                center = Offset(cx, centerY),
            )
        }
    }
}

