/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.onboarding

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.common.collect.ImmutableList
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.onboarding.OnboardingEvent
import moe.rgsekai.sekaitune.onboarding.OnboardingScreenState
import moe.rgsekai.sekaitune.onboarding.OnboardingSocialLinkUiModel
import moe.rgsekai.sekaitune.onboarding.OnboardingUiState
import moe.rgsekai.sekaitune.onboarding.OnboardingViewModel

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

private const val GITHUB_REPO_URL = "https://github.com/rgsekai/sekai-tune"

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
    onNavigateToSupport: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.OpenUri -> {
                    runCatching { uriHandler.openUri(event.url) }
                }
                OnboardingEvent.NavigateToSupport -> {
                    onNavigateToSupport()
                }
                OnboardingEvent.NavigateToLogin -> {
                    onNavigateToLogin()
                }
                OnboardingEvent.NavigateToHome -> {
                    onNavigateToHome()
                }
            }
        }
    }

    OnboardingScreen(
        state = state,
        onStartListening = viewModel::complete,
        onGiveStar = { viewModel.onOpenUri(GITHUB_REPO_URL) },
        onSupport = viewModel::onSupportClick,
        onSocialClick = { link -> viewModel.onOpenUri(link.url) },
        onLoginWithGoogle = viewModel::onLoginClick,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingScreenState,
    onStartListening: () -> Unit,
    onGiveStar: () -> Unit,
    onSupport: () -> Unit,
    onSocialClick: (OnboardingSocialLinkUiModel) -> Unit,
    onLoginWithGoogle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF07040D),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Theme - Subtle Liquid Purple from bottom fading to pitch black at top
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Vertical gradient: top is pitch black/deep dark, bottom is a gentle subtle dark purple
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF020104), // Pure deep dark top
                            Color(0xFF050308),
                            Color(0xFF0A0512),
                            Color(0xFF10071C),
                            Color(0xFF160A26), // Subtle, refined dark purple bottom
                        ),
                    ),
                )

                // Soft subtle ambient purple aura rising from the bottom
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4C217D).copy(alpha = 0.18f),
                            Color(0xFF2B104A).copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.95f),
                        radius = size.width * 0.85f,
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.95f),
                    radius = size.width * 0.85f,
                )
            }

            when (state) {
                OnboardingScreenState.Loading -> {
                    LoadingContent(contentPadding = padding)
                }

                OnboardingScreenState.Empty -> {
                    MessageContent(
                        title = stringResource(R.string.onboarding_empty_title),
                        subtitle = stringResource(R.string.onboarding_empty_subtitle),
                        actionLabel = stringResource(R.string.onboarding_start_listening),
                        onAction = onStartListening,
                        contentPadding = padding,
                    )
                }

                is OnboardingScreenState.Error -> {
                    MessageContent(
                        title = stringResource(state.messageResId),
                        subtitle = stringResource(R.string.onboarding_empty_subtitle),
                        actionLabel = stringResource(R.string.onboarding_start_listening),
                        onAction = onStartListening,
                        contentPadding = padding,
                    )
                }

                is OnboardingScreenState.Success -> {
                    WelcomeScreenContent(
                        uiState = state.uiState,
                        onStartListening = onStartListening,
                        onGiveStar = onGiveStar,
                        onSupport = onSupport,
                        onSocialClick = onSocialClick,
                        onLoginWithGoogle = onLoginWithGoogle,
                        contentPadding = padding,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreenContent(
    uiState: OnboardingUiState,
    onStartListening: () -> Unit,
    onGiveStar: () -> Unit,
    onSupport: () -> Unit,
    onSocialClick: (OnboardingSocialLinkUiModel) -> Unit,
    onLoginWithGoogle: () -> Unit,
    contentPadding: PaddingValues,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
                    .padding(top = 44.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Solid App Logo
            AppLogo(
                modifier = Modifier.padding(bottom = 22.dp),
            )

            // 2. Headline
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp,
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Badge Row (Edition + Version)
            MetadataBadgeRow(uiState = uiState)

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Subtext Tagline (Below edition & version badges)
            Text(
                text = stringResource(R.string.onboarding_welcome_tagline),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = Color(0xFFB8B3C8),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(26.dp))

            // 5. Card Container (Rounded Glass Surface)
            ActionCardContainer(
                socialLinks = uiState.socialLinks,
                onGiveStar = onGiveStar,
                onSupport = onSupport,
                onSocialClick = onSocialClick,
                onStartListening = onStartListening,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Below the card: "Login with Google to Sync" (Liquid Glass pill)
            LoginWithGoogleButton(
                onClick = onLoginWithGoogle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(76.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Clean, crisp solid transparent Sekai Tune logo
        Icon(
            painter = painterResource(R.drawable.sekai_tune_logo_white_transparent),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(72.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataBadgeRow(uiState: OnboardingUiState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BadgePill(text = stringResource(uiState.variantLabelResId))
        BadgePill(
            text =
                stringResource(
                    R.string.onboarding_version_label,
                    uiState.versionName,
                ),
        )
    }
}

@Composable
private fun BadgePill(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0x35281745),
        contentColor = Color(0xFFD6D1E8),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.20f),
                    Color(0xFF8C64D8).copy(alpha = 0.16f),
                ),
            ),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionCardContainer(
    socialLinks: ImmutableList<OnboardingSocialLinkUiModel>,
    onGiveStar: () -> Unit,
    onSupport: () -> Unit,
    onSocialClick: (OnboardingSocialLinkUiModel) -> Unit,
    onStartListening: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Liquid Glass Container matching exact screenshot proportions
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color(0x351F103A),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.26f),
                    Color(0xFFA27BFF).copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.06f),
                ),
            ),
        ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 5a. Give a Star on GitHub (Solid white pill button)
            LightPillButton(
                icon = {
                    Text(
                        text = "⭐",
                        fontSize = 16.sp,
                    )
                },
                text = stringResource(R.string.onboarding_give_star_github),
                onClick = onGiveStar,
            )

            // 5b. Support the development (Solid white pill button)
            LightPillButton(
                icon = {
                    Text(
                        text = "💖",
                        fontSize = 16.sp,
                    )
                },
                text = stringResource(R.string.onboarding_support_development),
                onClick = onSupport,
            )

            // 5c. Connect with us card inner glass section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0x40160B2A),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color(0xFF8652DD).copy(alpha = 0.14f),
                        ),
                    ),
                ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_connect_with_us),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        ),
                        color = Color(0xFFB5ADC8),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        socialLinks.forEach { link ->
                            SocialIconButton(
                                link = link,
                                onClick = { onSocialClick(link) },
                            )
                        }
                    }
                }
            }

            // 5d. Start Listening Now (Primary Solid White Pill CTA)
            Button(
                onClick = onStartListening,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0F081D),
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = Color(0xFF0F081D),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_start_listening),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color(0xFF0F081D),
                )
            }
        }
    }
}

@Composable
private fun LightPillButton(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF0F081D),
        ),
        contentPadding = PaddingValues(vertical = 13.dp, horizontal = 16.dp),
    ) {
        icon()
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
            color = Color(0xFF0F081D),
        )
    }
}

@Composable
private fun SocialIconButton(
    link: OnboardingSocialLinkUiModel,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .size(44.dp)
                .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0x55351E5C),
        contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.35f),
                    Color(0xFF9E71F0).copy(alpha = 0.18f),
                ),
            ),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(link.iconResId),
                contentDescription = link.contentDescription,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified,
            )
        }
    }
}

@Composable
private fun LoginWithGoogleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color(0x351F103A),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.24f),
                    Color(0xFF8652DD).copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.05f),
                ),
            ),
        ),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Official Google 4-color icon
            Icon(
                painter = painterResource(R.drawable.ic_google_logo_color),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.Unspecified,
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.onboarding_login_with_google),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun LoadingContent(contentPadding: PaddingValues) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun MessageContent(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    contentPadding: PaddingValues,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9E9EA7),
            )
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}
