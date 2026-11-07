/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.settings.discord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.discord.DiscordProfile
import moe.rgsekai.sekaitune.discord.NowPlaying
import moe.rgsekai.sekaitune.discord.PresenceActivity
import moe.rgsekai.sekaitune.discord.PresenceImages
import moe.rgsekai.sekaitune.discord.PresencePicture
import moe.rgsekai.sekaitune.discord.PresenceSettings

object DiscordColors {
    val Profile = Color(0xFF1E1F22)
    val Banner = Color(0xFF2B2D31)
    val Card = Color(0xFF2B2D31)
    val Primary = Color.White
    val Secondary = Color.White.copy(alpha = 0.65f)
    val Cover = Color(0xFF111214)
    val Field = Color(0xFF1E1F22)
    val Button = Color(0xFF383A40)
    val ToggleCard = Color(0xFF2B2D31)
}

private val BannerHeight = 84.dp
private val AvatarSize = 64.dp
private val AvatarRing = 4.dp

@Composable
internal fun DiscordProfileCard(
    profile: DiscordProfile?,
    track: NowPlaying,
    settings: PresenceSettings,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DiscordColors.Profile),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BannerHeight + AvatarSize / 2),
        ) {
            Banner(profile)
            Avatar(
                url = profile?.avatarUrl,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 14.dp),
        ) {
            Text(
                text = profile?.displayName ?: "Sekai Tune User",
                color = DiscordColors.Primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = profile?.username ?: "sekaitune",
                color = DiscordColors.Secondary,
                fontSize = 12.sp,
            )
            profile?.pronouns?.let {
                Text(text = it, color = DiscordColors.Secondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
            profile?.bio?.let {
                Text(text = it, color = DiscordColors.Primary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }

            Spacer(Modifier.height(12.dp))
            DiscordActivityPreview(track = track, settings = settings)
        }
    }
}

@Composable
private fun Banner(profile: DiscordProfile?) {
    val color = profile?.bannerColor?.let { Color(0xFF000000 or it.toLong()) } ?: DiscordColors.Banner
    Box(
        Modifier
            .fillMaxWidth()
            .height(BannerHeight)
            .background(color),
    ) {
        profile?.bannerUrl?.let { url ->
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Avatar(url: String?, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(DiscordColors.Profile)
            .padding(AvatarRing)
            .clip(CircleShape)
            .background(Color(0xFF383A40)),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = "Discord avatar", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Image(
                painter = painterResource(R.drawable.discord),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        }
    }
}

@Composable
internal fun DiscordActivityPreview(
    track: NowPlaying,
    settings: PresenceSettings,
    modifier: Modifier = Modifier,
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val activity = remember(track, settings, now) { PresenceActivity.build(track, settings, now) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DiscordColors.Card)
            .padding(12.dp),
    ) {
        Text(
            text = "${activity.kind.label.uppercase()} TO",
            color = DiscordColors.Secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Pictures(large = activity.largeImage, small = activity.smallImage)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = activity.name,
                    color = DiscordColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                activity.details?.let {
                    Text(text = it, color = DiscordColors.Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                activity.state?.let {
                    Text(text = it, color = DiscordColors.Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Times(activity, now)
            }
        }
        if (activity.buttons.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            activity.buttons.forEach { button ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DiscordColors.Button)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = button.label,
                        color = DiscordColors.Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun Times(activity: PresenceActivity, now: Long) {
    val start = activity.startMs ?: return
    val end = activity.endMs
    if (end == null) {
        Text(
            "${clock(now - start)} elapsed",
            color = DiscordColors.Secondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }
    val total = (end - start).coerceAtLeast(1)
    val elapsed = (now - start).coerceIn(0, total)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(clock(elapsed), color = DiscordColors.Secondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = (elapsed.toFloat() / total).coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(Color.White),
            )
        }
        Text(clock(total), color = DiscordColors.Secondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val m = s / 60
    val remS = s % 60
    return "%d:%02d".format(m, remS)
}

@Composable
private fun Pictures(large: PresencePicture?, small: PresencePicture?) {
    Box(Modifier.size(66.dp)) {
        Box(
            Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DiscordColors.Cover),
        ) {
            large?.let { PictureView(it) }
        }
        if (large != null && small != null) {
            Box(
                Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(DiscordColors.Card)
                    .padding(2.dp)
                    .clip(CircleShape),
            ) {
                PictureView(small)
            }
        }
    }
}

@Composable
private fun PictureView(picture: PresencePicture) {
    val fill = Modifier.fillMaxSize()
    when (picture) {
        is PresencePicture.Web -> AsyncImage(model = picture.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = fill)
        PresencePicture.Logo -> Box(fill.background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.launcher_monochrome),
                contentDescription = null,
                modifier = fill.padding(8.dp),
            )
        }
        is PresencePicture.Status -> StatusBadge(picture, fill)
        is PresencePicture.Spinning -> SpinningPicture(picture.sourceUrl, picture.style.secondsPerTurn, fill)
    }
}

@Composable
private fun StatusBadge(status: PresencePicture.Status, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        drawCircle(Color(0xFF1E1F22))
        val stroke = s * 0.085f
        val inset = s * 0.16f + stroke / 2
        val arcSize = Size(s - inset * 2, s - inset * 2)
        drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        val sweep = 360f * status.step / PresenceImages.StatusSteps
        if (sweep > 0f) {
            drawArc(Color.White, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val glyph = s * 0.30f
        val cx = s / 2
        val cy = s / 2
        if (status.playing) {
            val path = Path().apply {
                moveTo(cx - glyph * 0.35f, cy - glyph * 0.5f)
                lineTo(cx + glyph * 0.55f, cy)
                lineTo(cx - glyph * 0.35f, cy + glyph * 0.5f)
                close()
            }
            drawPath(path, Color.White)
        } else {
            val barW = glyph * 0.28f
            val barH = glyph * 0.9f
            val space = glyph * 0.22f
            drawRect(Color.White, Offset(cx - space / 2 - barW, cy - barH / 2), Size(barW, barH))
            drawRect(Color.White, Offset(cx + space / 2, cy - barH / 2), Size(barW, barH))
        }
    }
}

@Composable
private fun SpinningPicture(url: String, secondsPerTurn: Int, modifier: Modifier) {
    val turn = rememberInfiniteTransition(label = "spin")
    val degrees by turn.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(secondsPerTurn * 1_000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .graphicsLayer { rotationZ = degrees }
            .clip(CircleShape),
    )
}

@Composable
internal fun DiscordFloatingPreview(
    visible: Boolean,
    track: NowPlaying,
    settings: PresenceSettings,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 3 },
        modifier = modifier,
    ) {
        DiscordActivityPreview(
            track = track,
            settings = settings,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

