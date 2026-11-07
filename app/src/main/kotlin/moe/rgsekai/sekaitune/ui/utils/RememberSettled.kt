/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * [value] once it has stayed the same for [delayMs]. The first value comes straight through; a new
 * one reads as null until it has settled, so whatever the old value started stops at once.
 */
@Composable
fun <T : Any> rememberSettled(value: T, delayMs: Long): T? {
    var settled by remember { mutableStateOf<T?>(value) }
    LaunchedEffect(value) {
        if (settled == value) return@LaunchedEffect
        settled = null
        delay(delayMs)
        settled = value
    }
    return settled?.takeIf { it == value }
}

