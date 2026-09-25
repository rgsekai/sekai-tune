/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import moe.rgsekai.sekaitune.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = "home",
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.search,
        iconIdActive = R.drawable.search,
        route = "search",
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.library_outlined,
        iconIdActive = R.drawable.library_filled,
        route = "library",
    )

    object NewRelease : Screens(
        titleId = R.string.new_releases,
        iconIdInactive = R.drawable.new_release,
        iconIdActive = R.drawable.new_release,
        route = "new_release",
    )

    object MoodAndGenres : Screens(
        titleId = R.string.mood_and_genres,
        iconIdInactive = R.drawable.style,
        iconIdActive = R.drawable.style,
        route = "mood_and_genres",
    )

    object Onboarding : Screens(
        titleId = R.string.onboarding_welcome_title,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = "onboarding",
    )

    companion object {
        val MainScreens = listOf(Home, Search, NewRelease, Library)
        val TvMainScreens = listOf(Home, Search, Library)
    }
}




