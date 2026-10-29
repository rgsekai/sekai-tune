/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.utils

import android.net.Uri
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}

fun NavController.navigateToArtist(artistId: String, name: String? = null, thumbnail: String? = null) {
    val encodedName = name?.takeIf { it.isNotBlank() }?.let { Uri.encode(it) }
    val encodedThumb = thumbnail?.takeIf { it.isNotBlank() }?.let { Uri.encode(it) }
    val route = buildString {
        append("artist/$artistId")
        if (encodedName != null || encodedThumb != null) {
            append("?")
            if (encodedName != null) append("name=$encodedName")
            if (encodedThumb != null) {
                if (encodedName != null) append("&")
                append("thumbnail=$encodedThumb")
            }
        }
    }
    navigate(route)
}
