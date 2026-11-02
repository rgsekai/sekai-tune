package moe.rgsekai.sekaitune.desktopui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable

// A desktop window has no status bar to tint.
@Composable
actual fun StatusBarIcons(dark: Boolean) = Unit

// Nor one to hide.
@Composable
actual fun HideStatusBar(hidden: Boolean) = Unit

@Composable
actual fun stableStatusBarInsets(): WindowInsets = WindowInsets.statusBars
