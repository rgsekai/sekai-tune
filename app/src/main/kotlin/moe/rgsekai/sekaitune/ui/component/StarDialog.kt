/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rgsekai.sekaitune.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarDialog(
    onDismissRequest: () -> Unit,
    onSupport: () -> Unit,
    onLater: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.isEnabledFor(GlassComponent.POPUP_MENU) || glassConfig.isEnabledFor(GlassComponent.DIALOG)
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    GlassModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.support_development_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            FilledTonalButton(
                onClick = onSupport,
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
                colors =
                    if (isGlass) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isLight) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                border = if (isGlass) BorderStroke(1.dp, if (isLight) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f)) else null,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.coffee),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(R.string.support_development_title))
            }

            TextButton(
                onClick = onLater,
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(R.string.later))
            }
        }
    }
}




