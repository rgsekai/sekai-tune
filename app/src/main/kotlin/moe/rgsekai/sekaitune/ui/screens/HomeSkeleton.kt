/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.ui.component.Bone

/**
 * Structural skeleton for the Home feed during initial load.
 * Matches exact LazyColumn padding, hero aspect ratio, section heights and card sizes
 * so transitioning to real data causes zero layout shifts.
 */
@Composable
fun HomeSkeleton(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxW = maxWidth
            val insetsPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
            val listContentPadding = PaddingValues(
                start = insetsPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = 0.dp,
                end = insetsPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                bottom = insetsPadding.calculateBottomPadding(),
            )

            LazyColumn(
                state = lazyListState,
                contentPadding = listContentPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            ) {
                // 1. Hero Carousel Placeholder
                item(key = "skeleton_hero") {
                    val heroHeight = (maxW * 0.72f).coerceIn(240.dp, 360.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Bone(
                            width = maxW - 32.dp,
                            height = heroHeight,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // 2. Section: Quick Picks / Continue Listening
                item(key = "skeleton_section_1") {
                    HomeSectionBones(titleWidth = 140.dp)
                }

                // 3. Section: Forgotten Favorites / Recommendations
                item(key = "skeleton_section_2") {
                    HomeSectionBones(titleWidth = 180.dp)
                }

                // 4. Section: Artists / Playlists
                item(key = "skeleton_section_3") {
                    HomeSectionBones(titleWidth = 120.dp)
                }
            }
        }
    }
}

@Composable
private fun HomeSectionBones(
    titleWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        // Section Title Bone
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bone(width = titleWidth, height = 22.dp, shape = RoundedCornerShape(6.dp))
        }

        Spacer(Modifier.height(10.dp))

        // Horizontal Row of Card Bones
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(3) {
                Column {
                    Bone(width = 140.dp, height = 140.dp, shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(8.dp))
                    Bone(width = 110.dp, height = 14.dp, shape = RoundedCornerShape(4.dp))
                    Spacer(Modifier.height(4.dp))
                    Bone(width = 75.dp, height = 12.dp, shape = RoundedCornerShape(4.dp))
                }
            }
        }
    }
}
