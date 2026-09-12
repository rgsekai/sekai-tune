/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.models

import androidx.annotation.StringRes
import moe.rgsekai.sekaitune.R

enum class QueueFilter(
    @StringRes val titleRes: Int,
) {
    ALL(R.string.filter_all),
    DISCOVER(R.string.filter_discover),
    FAMILIAR(R.string.filter_familiar),
    POPULAR(R.string.filter_popular),
    DEEP_CUTS(R.string.filter_deep_cuts),
}
