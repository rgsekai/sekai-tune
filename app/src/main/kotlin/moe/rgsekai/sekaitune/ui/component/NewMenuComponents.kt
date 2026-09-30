/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NewActionButton(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.isEnabledFor(GlassComponent.POPUP_MENU)
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    val defaultContainerColor = when {
        backgroundColor.isSpecified -> backgroundColor
        isGlass -> if (isLight) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val actionContentColor = if (contentColor.isSpecified) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
    val buttonShape = ButtonDefaults.squareShape

    val rimColor = if (isLight) {
        Color.White.copy(alpha = 0.35f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = buttonShape,
        color = if (isGlass && !backgroundColor.isSpecified) Color.Transparent else defaultContainerColor,
        border = if (isGlass && !backgroundColor.isSpecified) BorderStroke(1.dp, rimColor) else null,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .then(
                    if (isGlass) {
                        Modifier.liquidGlass(
                            config = glassConfig,
                            shape = buttonShape,
                            applyEdgeEffects = true,
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }

            Text(
                text = text,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 13.5.sp,
                    ),
                color = actionContentColor,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                softWrap = true,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun NewMenuItem(
    headlineContent: @Composable () -> Unit,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        ListItem(
            headlineContent = headlineContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            modifier = Modifier.padding(horizontal = 4.dp),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            tonalElevation = 0.dp,
        )
    }

    if (onClick == null) {
        Box(modifier = modifier.fillMaxWidth()) {
            content()
        }
    } else {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = Color.Transparent,
        ) {
            content()
        }
    }
}

@Composable
fun NewMenuSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
fun NewActionGrid(
    actions: List<NewAction>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    if (actions.isEmpty()) return

    val columnCount = columns.coerceAtLeast(1)
    val rows = actions.chunked(columnCount)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { action ->
                    NewActionButton(
                        icon = action.icon,
                        text = action.text,
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                        enabled = action.enabled,
                        backgroundColor = action.backgroundColor,
                        contentColor = action.contentColor,
                    )
                }

                repeat(columnCount - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

data class NewAction(
    val icon: @Composable () -> Unit,
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val backgroundColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
)

@Composable
fun NewMenuContent(
    headerContent: @Composable (() -> Unit)? = null,
    actionGrid: @Composable (() -> Unit)? = null,
    menuItems: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        headerContent?.invoke()
        actionGrid?.invoke()

        if (actionGrid != null && menuItems != null) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        menuItems?.invoke()
    }
}

@Composable
fun NewIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.isEnabledFor(GlassComponent.POPUP_MENU)
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    val defaultContainerColor = when {
        backgroundColor.isSpecified -> backgroundColor
        isGlass -> if (isLight) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val iconContentColor = if (contentColor.isSpecified) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = CircleShape
    val rimColor = if (isLight) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.14f)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (isGlass && !backgroundColor.isSpecified) Color.Transparent else defaultContainerColor,
        border = if (isGlass && !backgroundColor.isSpecified) BorderStroke(1.dp, rimColor) else null,
        modifier =
            modifier.then(
                if (isGlass) {
                    Modifier.liquidGlass(
                        config = glassConfig,
                        shape = shape,
                        applyEdgeEffects = true,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@Composable
fun NewMenuContainer(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
    ) {
        content()
    }
}

@Composable
fun MenuSurfaceSection(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.isEnabledFor(GlassComponent.POPUP_MENU)
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    val surfaceColor =
        if (isGlass) {
            if (isLight) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }

    val rimColor = if (isLight) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f)

    Surface(
        shape = shape,
        color = if (isGlass) Color.Transparent else surfaceColor,
        border = if (isGlass) BorderStroke(1.dp, rimColor) else null,
        modifier =
            modifier
                .fillMaxWidth()
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
                ),
    ) {
        Column(content = content)
    }
}




