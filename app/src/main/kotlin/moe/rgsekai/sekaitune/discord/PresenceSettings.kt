/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

import androidx.annotation.DrawableRes
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.DiscordActivityNameKey
import moe.rgsekai.sekaitune.constants.DiscordActivityTypeKey
import moe.rgsekai.sekaitune.constants.DiscordButton1TargetKey
import moe.rgsekai.sekaitune.constants.DiscordButton1TextKey
import moe.rgsekai.sekaitune.constants.DiscordButton2TargetKey
import moe.rgsekai.sekaitune.constants.DiscordButton2TextKey
import moe.rgsekai.sekaitune.constants.DiscordClearOnPauseKey
import moe.rgsekai.sekaitune.constants.DiscordDetailsKey
import moe.rgsekai.sekaitune.constants.DiscordLargeImageKey
import moe.rgsekai.sekaitune.constants.DiscordLargeTextKey
import moe.rgsekai.sekaitune.constants.DiscordRefreshSecondsKey
import moe.rgsekai.sekaitune.constants.DiscordSmallImageKey
import moe.rgsekai.sekaitune.constants.DiscordSmallTextKey
import moe.rgsekai.sekaitune.constants.DiscordSpinFpsKey
import moe.rgsekai.sekaitune.constants.DiscordSpinSecondsKey
import moe.rgsekai.sekaitune.constants.DiscordStateKey
import moe.rgsekai.sekaitune.constants.EnableDiscordRPCKey

enum class PresenceKind(val prefValue: String, val label: String, val discordType: Int) {
    LISTENING("listening", "Listening", 2),
    PLAYING("playing", "Playing", 0),
}

enum class PresenceImage(val label: String, val forLargeImage: Boolean) {
    OFF("Turn it off", false),
    ARTWORK("Music artwork", true),
    ARTIST("Artist artwork", true),
    PLAYBACK_STATUS("Playback status", false),
    APP_LOGO("Sekai Tune logo", true),
    SPINNING_ARTIST("Rotating artist artwork", false),
    SPINNING_ARTWORK("Rotating music artwork", false),
    ;

    val isRotating: Boolean get() = this == SPINNING_ARTIST || this == SPINNING_ARTWORK

    companion object {
        val ForLarge = entries.filter { it.forLargeImage }
        val ForSmall = entries
    }
}

enum class PresenceButtonTarget(val label: String, @DrawableRes val icon: Int) {
    YOUTUBE_MUSIC("YouTube Music", R.drawable.provider_youtube_music),
    SPOTIFY("Spotify", R.drawable.provider_spotify),
    APPLE_MUSIC("Apple Music", R.drawable.provider_apple_music),
    TIDAL("Tidal", R.drawable.provider_tidal),
    SEKAI_TUNE("Sekai Tune on GitHub", R.drawable.github),
}

data class PresenceButton(val text: String, val target: PresenceButtonTarget)

data class PresenceSettings(
    val enabled: Boolean = true,
    val kind: PresenceKind = PresenceKind.LISTENING,
    val refreshSeconds: Int = DefaultRefreshSeconds,
    val name: String = "{artist} on {app}",
    val details: String = "{name}",
    val state: String = "{artist}",
    val largeText: String = "",
    val smallText: String = "{quality}",
    val largeImage: PresenceImage = PresenceImage.ARTWORK,
    val smallImage: PresenceImage = PresenceImage.PLAYBACK_STATUS,
    val button1: PresenceButton = PresenceButton("See on YouTube Music", PresenceButtonTarget.YOUTUBE_MUSIC),
    val button2: PresenceButton = PresenceButton("Open Sekai Tune", PresenceButtonTarget.SEKAI_TUNE),
    val spinSeconds: Int = DefaultSpinSeconds,
    val spinFps: Int = DefaultSpinFps,
    val clearOnPause: Boolean = false,
) {
    val spin: SpinStyle get() = SpinStyle(spinSeconds, spinFps)

    fun writeTo(prefs: MutablePreferences) {
        prefs[EnableDiscordRPCKey] = enabled
        prefs[DiscordActivityTypeKey] = kind.prefValue
        prefs[DiscordRefreshSecondsKey] = refreshSeconds
        prefs[DiscordActivityNameKey] = name
        prefs[DiscordDetailsKey] = details
        prefs[DiscordStateKey] = state
        prefs[DiscordLargeTextKey] = largeText
        prefs[DiscordSmallTextKey] = smallText
        prefs[DiscordLargeImageKey] = largeImage.name
        prefs[DiscordSmallImageKey] = smallImage.name
        prefs[DiscordButton1TextKey] = button1.text
        prefs[DiscordButton1TargetKey] = button1.target.name
        prefs[DiscordButton2TextKey] = button2.text
        prefs[DiscordButton2TargetKey] = button2.target.name
        prefs[DiscordSpinSecondsKey] = spinSeconds
        prefs[DiscordSpinFpsKey] = spinFps
        prefs[DiscordClearOnPauseKey] = clearOnPause
    }

    companion object {
        const val MinRefreshSeconds = 1
        const val MaxRefreshSeconds = 10
        const val DefaultRefreshSeconds = 3

        const val MinSpinSeconds = 2
        const val MaxSpinSeconds = 20
        const val DefaultSpinSeconds = 8

        val SpinFpsSteps = listOf(10, 12, 14, 17, 20, 25, 33, 50)
        const val DefaultSpinFps = 25

        private val Defaults = PresenceSettings()

        fun from(prefs: Preferences) = PresenceSettings(
            enabled = prefs[EnableDiscordRPCKey] ?: Defaults.enabled,
            kind = PresenceKind.entries.find { it.prefValue == prefs[DiscordActivityTypeKey] } ?: Defaults.kind,
            refreshSeconds = (prefs[DiscordRefreshSecondsKey] ?: Defaults.refreshSeconds)
                .coerceIn(MinRefreshSeconds, MaxRefreshSeconds),
            name = prefs[DiscordActivityNameKey] ?: Defaults.name,
            details = prefs[DiscordDetailsKey] ?: Defaults.details,
            state = prefs[DiscordStateKey] ?: Defaults.state,
            largeText = prefs[DiscordLargeTextKey] ?: Defaults.largeText,
            smallText = prefs[DiscordSmallTextKey] ?: Defaults.smallText,
            largeImage = enumOf(prefs[DiscordLargeImageKey], Defaults.largeImage).takeIf { it.forLargeImage }
                ?: Defaults.largeImage,
            smallImage = enumOf(prefs[DiscordSmallImageKey], Defaults.smallImage),
            button1 = PresenceButton(
                text = prefs[DiscordButton1TextKey] ?: Defaults.button1.text,
                target = enumOf(prefs[DiscordButton1TargetKey], Defaults.button1.target),
            ),
            button2 = PresenceButton(
                text = prefs[DiscordButton2TextKey] ?: Defaults.button2.text,
                target = enumOf(prefs[DiscordButton2TargetKey], Defaults.button2.target),
            ),
            spinSeconds = (prefs[DiscordSpinSecondsKey] ?: Defaults.spinSeconds).coerceIn(MinSpinSeconds, MaxSpinSeconds),
            spinFps = prefs[DiscordSpinFpsKey]?.takeIf { it in SpinFpsSteps } ?: Defaults.spinFps,
            clearOnPause = prefs[DiscordClearOnPauseKey] ?: Defaults.clearOnPause,
        )

        private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
            name?.let { stored -> enumValues<T>().find { it.name == stored } } ?: fallback
    }
}

