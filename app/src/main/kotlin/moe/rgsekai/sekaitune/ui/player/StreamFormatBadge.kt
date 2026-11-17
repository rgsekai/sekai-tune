package moe.rgsekai.sekaitune.ui.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.ui.theme.AppFontFamily

/**
 * "OPUS", or "OPUS 144 kbps": an outlined tag in flagship proportions,
 * every one of them a fraction of [height], so it reads the same at any size and font scale.
 */
@Composable
fun StreamFormatBadge(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 19.dp,
    color: Color = Color.White.copy(alpha = 0.55f),
) {
    val fontSize = with(LocalDensity.current) { (height * 0.52f).toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .border(height * 0.0625f, color, RoundedCornerShape(height * 0.31f))
            .padding(horizontal = height * 0.32f),
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                color = color,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = fontSize,
                letterSpacing = 0.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}
