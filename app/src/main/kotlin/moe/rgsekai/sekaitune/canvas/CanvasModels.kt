/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import androidx.compose.runtime.Immutable

enum class ProceduralCanvasStyle {
    KAWARP,
    SQUARE_TUNNEL,
    ;

    companion object {
        fun fromPreference(value: String?): ProceduralCanvasStyle {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: KAWARP
        }
    }
}

@Immutable
data class CanvasConfiguration(
    val enabled: Boolean = false,
    val source: CanvasSource = CanvasSource.ALL,
    val wifiOnly: Boolean = false,
    val proceduralFallback: Boolean = true,
    val proceduralStyle: ProceduralCanvasStyle = ProceduralCanvasStyle.KAWARP,
    val audioReactive: Boolean = false,
    val cacheLimitMb: Int = 256,
    val lowDataMode: Boolean = false,
)

@Immutable
data class CanvasConnectivity(
    val online: Boolean = false,
    val wifi: Boolean = false,
    val metered: Boolean = true,
)

@Immutable
data class CanvasPolicy(
    val configuration: CanvasConfiguration = CanvasConfiguration(),
    val connectivity: CanvasConnectivity = CanvasConnectivity(),
    val ready: Boolean = false,
    val configurationError: Boolean = false,
) {
    val networkAllowed: Boolean
        get() = ready && configuration.enabled && connectivity.online &&
            (!configuration.wifiOnly || connectivity.wifi) &&
            (!configuration.lowDataMode || !connectivity.metered)
}

enum class CanvasHealth {
    NOT_CHECKED,
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
    NOT_SELECTED,
    NOT_CONNECTED,
    DISABLED,
    OFFLINE,
    WIFI_REQUIRED,
    LOW_DATA_MODE,
}

@Immutable
data class CanvasHealthStatus(
    val betterLyrics: CanvasHealth = CanvasHealth.NOT_CHECKED,
    val appleMusic: CanvasHealth = CanvasHealth.NOT_CHECKED,
    val tidal: CanvasHealth = CanvasHealth.NOT_CHECKED,
    val spotify: CanvasHealth = CanvasHealth.NOT_CHECKED,
) {
    val checking: Boolean
        get() = betterLyrics == CanvasHealth.CHECKING || appleMusic == CanvasHealth.CHECKING ||
            tidal == CanvasHealth.CHECKING || spotify == CanvasHealth.CHECKING
}
