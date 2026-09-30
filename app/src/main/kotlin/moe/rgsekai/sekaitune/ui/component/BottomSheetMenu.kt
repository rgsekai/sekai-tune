/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)

    fun show(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        this.content = content
    }

    fun dismiss() {
        isVisible = false
    }
}

@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = MaterialTheme.colorScheme.surface,
    contentWindowInsets: WindowInsets = WindowInsets.navigationBars,
) {
    val focusManager = LocalFocusManager.current
    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.isEnabledFor(GlassComponent.POPUP_MENU)
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    if (state.isVisible) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
        ) {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val expandedBound = (maxHeight - statusBarTop - 24.dp).coerceAtLeast(300.dp)
            val collapsedBound = (maxHeight * 0.48f).coerceIn(340.dp, expandedBound)
            val dismissedBound = 0.dp

            val sheetState =
                rememberBottomSheetState(
                    dismissedBound = dismissedBound,
                    expandedBound = expandedBound,
                    collapsedBound = collapsedBound,
                    initialAnchor = COLLAPSED_ANCHOR,
                )

            val onDismiss = {
                state.dismiss()
            }

            BackHandler {
                if (sheetState.isExpandedOrExpanding) {
                    sheetState.collapseSoft()
                } else {
                    sheetState.dismiss()
                    onDismiss()
                }
            }

            val scrimHeight = (maxHeight - sheetState.value).coerceAtLeast(0.dp)
            if (scrimHeight > 0.dp) {
                Spacer(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(scrimHeight)
                            .align(Alignment.TopCenter)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    sheetState.dismiss()
                                    onDismiss()
                                }
                            }
                            .background(if (isGlass) Color.Transparent else Color.Black.copy(alpha = 0.45f)),
                )
            }

            val sheetHeight = sheetState.value.coerceAtLeast(0.dp)
            if (sheetHeight > 0.dp) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(sheetHeight)
                            .bottomSheetDraggable(sheetState, onDismiss = onDismiss)
                            .clip(sheetShape)
                            .then(
                                if (isGlass) {
                                    Modifier.liquidGlass(glassConfig, shape = sheetShape)
                                } else {
                                    Modifier.background(background)
                                }
                            ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 12.dp)
                                .size(width = 40.dp, height = 4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(2.dp),
                                ),
                    )

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp)
                                .windowInsetsPadding(contentWindowInsets)
                                .nestedScroll(sheetState.preUpPostDownNestedScrollConnection),
                    ) {
                        state.content(this)
                    }
                }
            }
        }
    }

    LaunchedEffect(state.isVisible) {
        if (state.isVisible) {
            focusManager.clearFocus()
        }
    }
}
