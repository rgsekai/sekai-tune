package moe.rgsekai.sekaitune.desktop

import moe.rgsekai.sekaitune.desktop.DesktopPersistence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DesktopNowPlayingStyle {
    MODERN,
    IMMERSIVE,
}

object DesktopPlayerStyleSettings {
    private const val KEY_STYLE = "player_design_style"
    private val persistence by lazy { DesktopPersistence() }

    private val _style = MutableStateFlow(
        runCatching {
            val raw = persistence.string(KEY_STYLE, DesktopNowPlayingStyle.MODERN.name)
            DesktopNowPlayingStyle.valueOf(raw)
        }.getOrDefault(DesktopNowPlayingStyle.MODERN)
    )
    val style: StateFlow<DesktopNowPlayingStyle> = _style.asStateFlow()

    fun setStyle(value: DesktopNowPlayingStyle) {
        _style.value = value
        persistence.saveString(KEY_STYLE, value.name)
    }
}
