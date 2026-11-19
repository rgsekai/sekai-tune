package moe.rgsekai.sekaitune.ui.screens.audioeffects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.screens.settings.SettingsDimensions
import moe.rgsekai.sekaitune.viewmodels.AudioEffectsViewModel

@Composable
fun AudioEffectsScreen(
    navController: NavController,
    viewModel: AudioEffectsViewModel = hiltViewModel(),
) {
    ListenToAudio()
    val scrollState = rememberScrollState()
    val playerInsets = LocalPlayerAwareWindowInsets.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                        .clickable(onClick = navController::navigateUp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.back_button_desc),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .windowInsetsPadding(playerInsets.only(WindowInsetsSides.Horizontal))
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = SettingsDimensions.ScreenBottomPadding + playerInsets.asPaddingValues().calculateBottomPadding(),
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 1. Reverb
                ReverbCard(viewModel)

                // 2 & 3. Loudness & Even out songs
                LoudnessCard(viewModel)

                // 4. Spatial audio
                SpatialCard(viewModel)

                // 5. Mono audio
                MonoCard(viewModel)

                // 6. Dolby Atmos & surround
                AtmosCard(viewModel)

                // 7. Playback speed
                SpeedCard(viewModel)

                // 8. Exponential volume
                VolumeCard(viewModel)
            }
        }
    }
}

