/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.settings

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import moe.rgsekai.sekaitune.BuildConfig
import moe.rgsekai.sekaitune.R

@Immutable
data class SettingsSearchEntry(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val category: String,
    @param:DrawableRes val iconRes: Int,
    val route: String,
    val keywords: List<String> = emptyList(),
)

object SettingsSearchRepository {
    fun buildSearchIndex(context: Context): List<SettingsSearchEntry> = buildList {
        val topCategory = context.getString(R.string.settings)
        val playerContentCategory = context.getString(R.string.settings_section_player_content)
        val appearanceCategory = context.getString(R.string.appearance)
        val playbackCategory = context.getString(R.string.settings_playback_title)
        val lyricsCategory = context.getString(R.string.lyrics)
        val behaviorCategory = context.getString(R.string.settings_behavior_title)
        val storageCategory = context.getString(R.string.storage)
        val internetCategory = context.getString(R.string.internet)
        val accountCategory = context.getString(R.string.account)
        val aiCategory = context.getString(R.string.ai_integration)
        val backupCategory = context.getString(R.string.backup_restore)
        val contentCategory = context.getString(R.string.content)
        val poTokenCategory = context.getString(R.string.po_token_generation)
        val devCategory = context.getString(R.string.settings_developer_options_title)
        val aboutCategory = context.getString(R.string.about)

        // ==========================================
        // 1. TOP-LEVEL CATEGORIES
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "cat_account",
                title = accountCategory,
                subtitle = context.getString(R.string.settings_account_subtitle),
                category = topCategory,
                iconRes = R.drawable.account,
                route = "settings/account",
                keywords = listOf("account", "google", "profile", "sync", "user", "login", "auth", "sign in", "channels"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_stats",
                title = context.getString(R.string.settings_stats_title),
                subtitle = context.getString(R.string.settings_stats_subtitle),
                category = topCategory,
                iconRes = R.drawable.stats,
                route = "stats",
                keywords = listOf("stats", "statistics", "listening", "top songs", "top artists", "history", "charts", "recap"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_appearance",
                title = appearanceCategory,
                subtitle = context.getString(R.string.settings_appearance_subtitle),
                category = topCategory,
                iconRes = R.drawable.palette,
                route = "settings/appearance",
                keywords = listOf("appearance", "theme", "look", "dark mode", "colors", "style", "ui", "display", "font", "oled", "palette"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_playback",
                title = playbackCategory,
                subtitle = context.getString(R.string.settings_playback_subtitle),
                category = topCategory,
                iconRes = R.drawable.music_note,
                route = "settings/player",
                keywords = listOf("playback", "audio", "sound", "player", "music", "equalizer", "eq", "quality", "crossfade", "bitrate"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_behavior",
                title = behaviorCategory,
                subtitle = context.getString(R.string.settings_behavior_subtitle),
                category = topCategory,
                iconRes = R.drawable.swipe,
                route = "settings/privacy",
                keywords = listOf("behavior", "privacy", "history", "pause", "clear history", "incognito", "analytics"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_lyrics",
                title = lyricsCategory,
                subtitle = context.getString(R.string.settings_lyrics_subtitle),
                category = topCategory,
                iconRes = R.drawable.lyrics,
                route = "settings/lyrics",
                keywords = listOf("lyrics", "subtitles", "karaoke", "provider", "translation", "romaji", "lrc", "lrclib", "kugou"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_ai_integration",
                title = aiCategory,
                subtitle = context.getString(R.string.ai_integration_desc),
                category = topCategory,
                iconRes = R.drawable.ai,
                route = "settings/ai_integration",
                keywords = listOf("ai integration", "gemini", "chatgpt", "openai", "claude", "openrouter", "artificial intelligence", "api key", "ai provider"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_backup_restore",
                title = backupCategory,
                subtitle = context.getString(R.string.settings_backup_restore_subtitle),
                category = topCategory,
                iconRes = R.drawable.backup,
                route = "settings/backup_restore",
                keywords = listOf("backup & restore", "backup", "restore", "export", "import", "playlists", "save", "database", "spotify"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_content",
                title = contentCategory,
                subtitle = context.getString(R.string.settings_content_subtitle),
                category = playerContentCategory,
                iconRes = R.drawable.language,
                route = "settings/content",
                keywords = listOf("content", "language", "country", "region", "explicit", "filter", "location", "quick picks"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_internet",
                title = internetCategory,
                subtitle = context.getString(R.string.settings_internet_subtitle),
                category = playerContentCategory,
                iconRes = R.drawable.wifi_proxy,
                route = "settings/internet",
                keywords = listOf("internet", "network", "wifi", "proxy", "http", "socks5", "offline", "data saver"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_po_token",
                title = poTokenCategory,
                subtitle = context.getString(R.string.settings_po_token_subtitle),
                category = playerContentCategory,
                iconRes = R.drawable.token,
                route = "settings/po_token",
                keywords = listOf("potoken", "po token generation", "proof of origin", "visitor data", "bot check", "youtube token"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_storage",
                title = storageCategory,
                subtitle = context.getString(R.string.settings_storage_subtitle),
                category = playerContentCategory,
                iconRes = R.drawable.storage,
                route = "settings/storage",
                keywords = listOf("storage", "cache", "clear cache", "disk", "download location", "sd card", "space", "smart trimmer"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_developer_options",
                title = devCategory,
                subtitle = context.getString(R.string.settings_developer_options_subtitle),
                category = playerContentCategory,
                iconRes = R.drawable.experiment,
                route = "settings/misc",
                keywords = listOf("developer options", "debug", "experimental", "advanced", "logcat", "nerd stats"),
            ),
        )
        if (BuildConfig.UPDATER_AVAILABLE) {
            add(
                SettingsSearchEntry(
                    id = "cat_updates",
                    title = context.getString(R.string.updates),
                    subtitle = context.getString(R.string.settings_updates_subtitle),
                    category = playerContentCategory,
                    iconRes = R.drawable.update,
                    route = "settings/update",
                    keywords = listOf("updates", "check update", "new version", "changelog", "release", "download"),
                ),
            )
        }
        add(
            SettingsSearchEntry(
                id = "cat_support",
                title = context.getString(R.string.support),
                subtitle = context.getString(R.string.settings_support_subtitle),
                category = playerContentCategory,
                iconRes = R.drawable.attach_money,
                route = "settings/support",
                keywords = listOf("support", "donate", "contribution", "support developer", "coffee", "sponsor"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "cat_about",
                title = aboutCategory,
                subtitle = context.getString(R.string.settings_about_subtitle),
                category = playerContentCategory,
                iconRes = R.drawable.info,
                route = "settings/about",
                keywords = listOf("about", "info", "version", "licenses", "github", "credits", "developers", "open source"),
            ),
        )

        // ==========================================
        // 2. AI INTEGRATION SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_ai_provider",
                title = "AI provider",
                subtitle = "Choose the AI service (None, Gemini, ChatGPT, Claude, OpenRouter, Custom)",
                category = aiCategory,
                iconRes = R.drawable.ai,
                route = "settings/ai_integration",
                keywords = listOf("ai provider", "ai service", "gemini", "chatgpt", "openai", "claude", "anthropic", "openrouter", "custom ai", "llm"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_ai_api_key",
                title = "AI API key",
                subtitle = "Set authentication API key for AI features and translation",
                category = aiCategory,
                iconRes = R.drawable.token,
                route = "settings/ai_integration",
                keywords = listOf("ai api key", "api key", "gemini token", "openai key", "claude key", "secret token", "api token"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_ai_test_api",
                title = "Test AI API",
                subtitle = "Validate and test AI API key connection",
                category = aiCategory,
                iconRes = R.drawable.sync,
                route = "settings/ai_integration",
                keywords = listOf("test ai api", "test api", "validate api", "check connection", "ping ai", "test token"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_ai_cloud_sync",
                title = "Google Account & Cloud Sync",
                subtitle = "Sign in with Google to sync app settings across devices",
                category = aiCategory,
                iconRes = R.drawable.account,
                route = "settings/ai_integration",
                keywords = listOf("google sign in", "google account", "cloud sync", "sync settings", "firebase sync", "sign out"),
            ),
        )

        // ==========================================
        // 3. ACCOUNT SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_music_together",
                title = context.getString(R.string.music_together),
                subtitle = "Listen together with friends in real-time (LAN or Online room)",
                category = accountCategory,
                iconRes = R.drawable.fire,
                route = "settings/music_together",
                keywords = listOf("music together", "listen together", "party", "sync playback", "room code", "host session", "join room", "lan", "online"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_hidden_playlists",
                title = context.getString(R.string.hidden_playlists),
                subtitle = context.getString(R.string.hidden_playlists_description),
                category = accountCategory,
                iconRes = R.drawable.visibility_off,
                route = "settings/hidden_playlists",
                keywords = listOf("hidden playlists", "private playlists", "hide playlist", "unhide"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_ytm_sync",
                title = context.getString(R.string.yt_sync),
                subtitle = "Synchronize your liked songs and playlists with YouTube Music",
                category = accountCategory,
                iconRes = R.drawable.cached,
                route = "settings/account",
                keywords = listOf("yt sync", "youtube music sync", "sync library", "sync liked songs", "sync playlists"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_more_content",
                title = context.getString(R.string.more_content),
                subtitle = context.getString(R.string.use_login_for_browse_desc),
                category = accountCategory,
                iconRes = R.drawable.account,
                route = "settings/account",
                keywords = listOf("more content", "browse with account", "personalized recommendations", "home feed"),
            ),
        )

        // ==========================================
        // 4. APPEARANCE SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_dark_theme",
                title = context.getString(R.string.dark_theme),
                subtitle = "Turn dark mode on, off, or follow system theme",
                category = appearanceCategory,
                iconRes = R.drawable.dark_mode,
                route = "settings/appearance",
                keywords = listOf("dark theme", "dark mode", "light mode", "system theme", "night mode", "display"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_pure_black",
                title = context.getString(R.string.pure_black),
                subtitle = "Pitch black background for OLED screens to save battery",
                category = appearanceCategory,
                iconRes = R.drawable.contrast,
                route = "settings/appearance",
                keywords = listOf("pure black", "oled", "amoled", "pitch black", "true black", "battery saver"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_dynamic_theme",
                title = "Dynamic theme",
                subtitle = "Generate theme colors dynamically from current song album art",
                category = appearanceCategory,
                iconRes = R.drawable.palette,
                route = "settings/appearance",
                keywords = listOf("dynamic theme", "material you", "album art color", "monet", "adaptive theme"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_palette_picker",
                title = context.getString(R.string.color_palette),
                subtitle = context.getString(R.string.customize_theme_colors),
                category = appearanceCategory,
                iconRes = R.drawable.palette,
                route = "settings/appearance/palette_picker",
                keywords = listOf("color palette", "custom colors", "accent color", "seed color", "theme color"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_theme_creator",
                title = context.getString(R.string.theme_creator_title),
                subtitle = "Build and edit your custom color palette and scheme",
                category = appearanceCategory,
                iconRes = R.drawable.format_paint,
                route = "settings/appearance/theme_creator",
                keywords = listOf("theme creator", "create theme", "custom palette", "build theme", "edit scheme"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_app_icon",
                title = "App icon",
                subtitle = "Choose the launcher icon style for Sekai Tune",
                category = appearanceCategory,
                iconRes = R.drawable.small_icon,
                route = "settings/appearance/icon",
                keywords = listOf("app icon", "change icon", "launcher icon", "icon pack", "custom icon", "logo"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_lyrics_animations",
                title = context.getString(R.string.lyrics_animation_style),
                subtitle = "Customize active line animations, glow effects, and transitions",
                category = appearanceCategory,
                iconRes = R.drawable.animation,
                route = "settings/appearance/lyrics_animations",
                keywords = listOf("lyrics animation style", "lyrics glow", "active line animation", "karaoke bounce", "smooth scroll"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_aod_customized",
                title = context.getString(R.string.aod_customize_title),
                subtitle = context.getString(R.string.aod_customize_subtitle),
                category = appearanceCategory,
                iconRes = R.drawable.desktop_windows,
                route = "settings/appearance/aod_customized",
                keywords = listOf("always on display", "aod", "lockscreen", "clock style", "standby mode", "ambient"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_custom_font",
                title = "Custom font",
                subtitle = "Select typeface (Poppins, Outfit, Inter, etc.) or import custom TTF",
                category = appearanceCategory,
                iconRes = R.drawable.text_fields,
                route = "settings/appearance",
                keywords = listOf("custom font", "typography", "font style", "import ttf", "typeface", "poppins", "inter", "outfit", "roboto", "rubik"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_nav_bar_tabs",
                title = "Navigation bar tabs",
                subtitle = "Choose default open tab and visible bottom navigation tabs",
                category = appearanceCategory,
                iconRes = R.drawable.nav_bar,
                route = "settings/appearance",
                keywords = listOf("navigation bar tabs", "bottom bar", "tabs", "hide tabs", "default tab", "home", "explore", "library"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_player_design_style",
                title = "Player design style",
                subtitle = "Choose layout and look of the full-screen player (V1, V2, V3, V4)",
                category = appearanceCategory,
                iconRes = R.drawable.music_note,
                route = "settings/appearance",
                keywords = listOf("player design style", "player layout", "v4 player", "v3 player", "now playing style"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_player_bg_style",
                title = "Player background style",
                subtitle = "Customize player background with blurred album art or gradients",
                category = appearanceCategory,
                iconRes = R.drawable.palette,
                route = "settings/appearance",
                keywords = listOf("player background style", "blurred art", "gradient background", "album cover blur"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_playback_slider_style",
                title = "Playback slider style",
                subtitle = "Select progress bar seeker appearance (Standard, Wavy, Modern)",
                category = appearanceCategory,
                iconRes = R.drawable.waves,
                route = "settings/appearance",
                keywords = listOf("playback slider style", "progress bar", "seek bar", "wavy slider", "slider style"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_spotify_canvas",
                title = "SekaiTune Canvas",
                subtitle = "Enable looping video artwork canvas for supported tracks",
                category = appearanceCategory,
                iconRes = R.drawable.slow_motion_video,
                route = "settings/canvas",
                keywords = listOf("canvas", "spotify canvas", "video loop", "animated artwork", "moving cover art"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_force_high_refresh_rate",
                title = "Force high refresh rate",
                subtitle = "Run the application smoothly at 90Hz / 120Hz display refresh rate",
                category = appearanceCategory,
                iconRes = R.drawable.bolt,
                route = "settings/appearance",
                keywords = listOf("force high refresh rate", "120hz", "90hz", "smooth display", "refresh rate", "fps"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_disable_blur",
                title = "Disable blur effects",
                subtitle = "Disable backdrop blur to maximize performance on lower-end devices",
                category = appearanceCategory,
                iconRes = R.drawable.contrast,
                route = "settings/appearance",
                keywords = listOf("disable blur", "remove blur", "blur radius", "performance", "lag fix", "speed up"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_swipe_to_skip",
                title = "Swipe thumbnail to skip",
                subtitle = "Swipe player artwork left or right to skip tracks",
                category = appearanceCategory,
                iconRes = R.drawable.swipe,
                route = "settings/appearance",
                keywords = listOf("swipe thumbnail to skip", "swipe gesture", "swipe to change song", "swipe sensitivity"),
            ),
        )

        // ==========================================
        // 5. PLAYBACK SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_audio_format",
                title = "Audio format",
                subtitle = "Select playback audio encoding (MP3, AAC, OPUS, FLAC)",
                category = playbackCategory,
                iconRes = R.drawable.ic_music,
                route = "settings/player",
                keywords = listOf("audio format", "format", "mp3", "aac", "opus", "flac", "audio codec", "bitrate"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_audio_quality",
                title = "Audio quality",
                subtitle = "Choose streaming audio quality level (Auto, High, Medium, Low)",
                category = playbackCategory,
                iconRes = R.drawable.music_note,
                route = "settings/player",
                keywords = listOf("audio quality", "sound quality", "high quality", "bitrate", "stream quality", "320kbps", "256kbps"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_audio_offload",
                title = "Audio offload",
                subtitle = "Use device hardware DSP decoding for energy efficiency and battery saving",
                category = playbackCategory,
                iconRes = R.drawable.bolt,
                route = "settings/player",
                keywords = listOf("audio offload", "hardware decoding", "dsp offload", "battery saver", "hardware acceleration"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_equalizer",
                title = context.getString(R.string.equalizer),
                subtitle = "Adjust audio frequencies, bass boost, and sound presets",
                category = playbackCategory,
                iconRes = R.drawable.equalizer,
                route = "settings/player",
                keywords = listOf("equalizer", "eq", "bass boost", "treble", "sound effect", "audio fx", "presets", "frequency bands"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_pitch_speed",
                title = "Pitch and playback speed",
                subtitle = "Modify playback tempo and audio pitch",
                category = playbackCategory,
                iconRes = R.drawable.speed,
                route = "settings/player",
                keywords = listOf("pitch and playback speed", "playback speed", "tempo", "pitch", "faster", "slower", "0.5x", "1.5x", "2x"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_crossfade",
                title = context.getString(R.string.audio_crossfade_title),
                subtitle = context.getString(R.string.audio_crossfade_description),
                category = playbackCategory,
                iconRes = R.drawable.waves,
                route = "settings/player",
                keywords = listOf("crossfade", "audio crossfade", "gapless", "crossfade duration", "smooth transition", "fade between songs"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_skip_silence",
                title = context.getString(R.string.skip_silence),
                subtitle = "Automatically skip silent portions at beginning and end of audio tracks",
                category = playbackCategory,
                iconRes = R.drawable.fast_forward,
                route = "settings/player",
                keywords = listOf("skip silence", "remove silence", "trim silence", "skip silent parts"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_audio_normalization",
                title = "Audio normalization",
                subtitle = "Level and balance volume across all songs (ReplayGain / loudness)",
                category = playbackCategory,
                iconRes = R.drawable.equalizer,
                route = "settings/player",
                keywords = listOf("audio normalization", "replaygain", "volume normalization", "loudness", "equal volume"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_sleep_timer",
                title = context.getString(R.string.sleep_timer),
                subtitle = "Set default timer to stop playback automatically",
                category = playbackCategory,
                iconRes = R.drawable.bedtime,
                route = "settings/player",
                keywords = listOf("sleep timer", "auto stop", "timer", "turn off music", "bedtime"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_persistent_queue",
                title = "Persistent queue",
                subtitle = "Preserve and restore playback queue and song position after app restarts",
                category = playbackCategory,
                iconRes = R.drawable.playlist_play,
                route = "settings/player",
                keywords = listOf("persistent queue", "save queue", "resume queue", "restore playback queue"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_permanent_shuffle",
                title = "Permanent shuffle",
                subtitle = "Keep shuffle mode always enabled across playlists",
                category = playbackCategory,
                iconRes = R.drawable.music_note,
                route = "settings/player",
                keywords = listOf("permanent shuffle", "always shuffle", "random order", "shuffle mode"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_chiper_settings",
                title = context.getString(R.string.mori_cipher_settings_title),
                subtitle = context.getString(R.string.mori_cipher_settings_description),
                category = playbackCategory,
                iconRes = R.drawable.lock,
                route = "settings/player/chiper",
                keywords = listOf("mori cipher", "cipher", "chiper", "audio engine", "stream decrypt", "deobfuscator"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_auto_bluetooth",
                title = "Auto-play on Bluetooth",
                subtitle = "Resume playing music when connected to Bluetooth devices or headphones",
                category = playbackCategory,
                iconRes = R.drawable.music_note,
                route = "settings/player",
                keywords = listOf("auto start on bluetooth", "bluetooth autoplay", "headphones connect", "car audio"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_external_downloader",
                title = "External downloader",
                subtitle = "Use external app (e.g. Seal, YTDLnis) to download audio files",
                category = playbackCategory,
                iconRes = R.drawable.storage,
                route = "settings/player",
                keywords = listOf("external downloader", "seal", "ytdlnis", "download manager", "save songs"),
            ),
        )

        // ==========================================
        // 6. LYRICS SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_lyrics_providers",
                title = context.getString(R.string.providers),
                subtitle = "Manage enabled lyrics providers (LrcLib, Kugou, SimpMusic, Paxsenix, BetterLyrics)",
                category = lyricsCategory,
                iconRes = R.drawable.lyrics,
                route = "settings/lyrics",
                keywords = listOf("lyrics providers", "lrclib", "kugou", "simpmusic", "youlyplus", "betterlyrics", "unison", "paxsenix", "apple music lyrics", "spotify lyrics", "netease", "musixmatch", "youtube lyrics"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_lyrics_text_size",
                title = context.getString(R.string.lyrics_text_size),
                subtitle = "Change font size, line spacing, and blur for synchronized lyrics",
                category = lyricsCategory,
                iconRes = R.drawable.text_fields,
                route = "settings/lyrics",
                keywords = listOf("lyrics text size", "font size", "lyrics size", "large lyrics", "line spacing", "lyrics blur"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_lyrics_mode",
                title = "Lyrics mode",
                subtitle = "Switch between Enhanced (word-by-word synced), Classic, and Karaoke modes",
                category = lyricsCategory,
                iconRes = R.drawable.lyrics,
                route = "settings/lyrics",
                keywords = listOf("lyrics mode", "karaoke mode", "enhanced lyrics", "word by word", "syllable sync", "classic lyrics"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_romanize_lyrics",
                title = "Romanize lyrics (Romaji, Pinyin, Hangul)",
                subtitle = "Convert Japanese, Korean, and Chinese lyrics into readable romanized pronunciation",
                category = lyricsCategory,
                iconRes = R.drawable.language,
                route = "settings/lyrics",
                keywords = listOf("romanize lyrics", "romaji", "pinyin", "furigana", "hangul pronunciation", "japanese lyrics", "korean lyrics", "chinese lyrics"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_clear_lyrics_cache",
                title = "Clear lyrics cache",
                subtitle = "Delete all stored local lyrics cache to fetch fresh lyrics",
                category = lyricsCategory,
                iconRes = R.drawable.cached,
                route = "settings/lyrics",
                keywords = listOf("clear lyrics cache", "delete lyrics cache", "refresh lyrics", "clean lyrics"),
            ),
        )

        // ==========================================
        // 7. BEHAVIOR & PRIVACY SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_pause_listen_history",
                title = context.getString(R.string.pause_listen_history),
                subtitle = "Stop recording newly played songs to listening history",
                category = behaviorCategory,
                iconRes = R.drawable.history,
                route = "settings/privacy",
                keywords = listOf("pause listen history", "pause history", "incognito", "private listening", "do not track"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_pause_search_history",
                title = context.getString(R.string.pause_search_history),
                subtitle = "Stop recording search terms in history",
                category = behaviorCategory,
                iconRes = R.drawable.search_off,
                route = "settings/privacy",
                keywords = listOf("pause search history", "pause search", "search history", "do not save searches"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_clear_listen_history",
                title = context.getString(R.string.clear_listen_history),
                subtitle = "Delete all previously recorded playback history",
                category = behaviorCategory,
                iconRes = R.drawable.delete_history,
                route = "settings/privacy",
                keywords = listOf("clear listen history", "clear playback history", "delete history", "erase history"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_clear_search_history",
                title = context.getString(R.string.clear_search_history),
                subtitle = "Delete all saved search history terms",
                category = behaviorCategory,
                iconRes = R.drawable.clear_all,
                route = "settings/privacy",
                keywords = listOf("clear search history", "delete searches", "erase search query", "remove search history"),
            ),
        )

        // ==========================================
        // 8. CONTENT SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_content_language",
                title = context.getString(R.string.content_language),
                subtitle = "Choose preferred language for music metadata and search results",
                category = contentCategory,
                iconRes = R.drawable.language,
                route = "settings/content",
                keywords = listOf("content language", "song language", "translate title", "metadata language"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_content_country",
                title = context.getString(R.string.content_country),
                subtitle = "Select location region for top charts and trending playlists",
                category = contentCategory,
                iconRes = R.drawable.location_on,
                route = "settings/content",
                keywords = listOf("content country", "country", "region", "charts location", "trending music region"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_hide_explicit",
                title = context.getString(R.string.hide_explicit),
                subtitle = "Filter out songs with explicit lyrics and themes",
                category = contentCategory,
                iconRes = R.drawable.explicit,
                route = "settings/content",
                keywords = listOf("hide explicit", "explicit filter", "parental control", "clean music", "clean songs"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_hide_video",
                title = context.getString(R.string.hide_video),
                subtitle = "Prefer audio streams and hide video results",
                category = contentCategory,
                iconRes = R.drawable.slow_motion_video,
                route = "settings/content",
                keywords = listOf("hide video", "audio only", "disable videos", "save data"),
            ),
        )

        // ==========================================
        // 9. STORAGE SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_clear_audio_cache",
                title = context.getString(R.string.clear_song_cache),
                subtitle = "Free up storage space by clearing cached song files",
                category = storageCategory,
                iconRes = R.drawable.cached,
                route = "settings/storage",
                keywords = listOf("clear song cache", "clear audio cache", "delete cached songs", "free up storage", "audio cache"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_clear_image_cache",
                title = context.getString(R.string.clear_image_cache),
                subtitle = "Clear cached album art, artist photos, and thumbnails",
                category = storageCategory,
                iconRes = R.drawable.image,
                route = "settings/storage",
                keywords = listOf("clear image cache", "album art cache", "thumbnails", "image storage"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_max_cache_size",
                title = "Max cache size limit",
                subtitle = "Set maximum storage threshold for songs, artwork, and canvas",
                category = storageCategory,
                iconRes = R.drawable.storage,
                route = "settings/storage",
                keywords = listOf("max cache size", "cache limit", "disk space limit", "storage quota"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_smart_trimmer",
                title = "Smart trimmer",
                subtitle = "Automatically clean up least recently played song cache",
                category = storageCategory,
                iconRes = R.drawable.storage,
                route = "settings/storage",
                keywords = listOf("smart trimmer", "auto trim", "auto clean cache", "cleanup storage"),
            ),
        )

        // ==========================================
        // 10. INTERNET & PROXY SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_proxy_configuration",
                title = context.getString(R.string.proxy),
                subtitle = "Set up HTTP or SOCKS5 proxy to route network traffic",
                category = internetCategory,
                iconRes = R.drawable.wifi_proxy,
                route = "settings/internet",
                keywords = listOf("proxy", "proxy configuration", "http proxy", "socks5", "bypass geo restriction", "port", "host"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_test_proxy",
                title = "Test proxy connection",
                subtitle = "Check if configured HTTP/SOCKS5 proxy server is reachable",
                category = internetCategory,
                iconRes = R.drawable.wifi_proxy,
                route = "settings/internet",
                keywords = listOf("test proxy", "test connection", "verify proxy", "ping proxy"),
            ),
        )

        // ==========================================
        // 11. BACKUP & RESTORE SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_create_backup",
                title = "Create backup",
                subtitle = "Export your local playlists, playback history, and preferences to file",
                category = backupCategory,
                iconRes = R.drawable.backup,
                route = "settings/backup_restore",
                keywords = listOf("create backup", "export backup", "save playlists", "export database", "backup file"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_restore_backup",
                title = "Restore backup",
                subtitle = "Restore your saved library and playlists from a backup file",
                category = backupCategory,
                iconRes = R.drawable.backup,
                route = "settings/backup_restore",
                keywords = listOf("restore backup", "import backup", "load playlists", "restore settings"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_spotify_sync",
                title = "Spotify sync & import",
                subtitle = "Connect your Spotify account to import and synchronize playlists",
                category = backupCategory,
                iconRes = R.drawable.playlist_play,
                route = "settings/backup_restore",
                keywords = listOf("spotify sync", "spotify import", "import spotify playlists", "transfer playlists from spotify"),
            ),
        )

        // ==========================================
        // 12. DEVELOPER & DEBUG SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_logcat_viewer",
                title = "Logcat viewer",
                subtitle = "View real-time application and network logs",
                category = devCategory,
                iconRes = R.drawable.manage_search,
                route = "settings/logcat",
                keywords = listOf("logcat viewer", "logs", "error logs", "crash log", "debug logs", "logcat"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_nerd_stats",
                title = "Stats for nerds",
                subtitle = "Display technical playback metrics, buffer health, and audio bitrate",
                category = devCategory,
                iconRes = R.drawable.stats,
                route = "settings/misc",
                keywords = listOf("stats for nerds", "nerd stats", "bitrate display", "buffer metrics", "technical stats"),
            ),
        )

        // ==========================================
        // 13. ABOUT & CHANGELOG SUB-SETTINGS
        // ==========================================
        add(
            SettingsSearchEntry(
                id = "sub_changelog",
                title = context.getString(R.string.changelog),
                subtitle = "View latest release notes and new features",
                category = aboutCategory,
                iconRes = R.drawable.history,
                route = "settings/changelog",
                keywords = listOf("changelog", "what's new", "release notes", "version history", "features", "updates"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_licenses",
                title = context.getString(R.string.about_license),
                subtitle = context.getString(R.string.about_license_unknown),
                category = aboutCategory,
                iconRes = R.drawable.info,
                route = "settings/about",
                keywords = listOf("open source licenses", "dependencies", "gpl-3.0", "third party libraries", "licenses"),
            ),
        )
        add(
            SettingsSearchEntry(
                id = "sub_github",
                title = "GitHub repository",
                subtitle = "View Sekai Tune source code, report bugs, and contribute",
                category = aboutCategory,
                iconRes = R.drawable.info,
                route = "settings/about",
                keywords = listOf("github", "source code", "open source", "report bug", "issue", "repository"),
            ),
        )
    }

    /**
     * Powerful multi-token, character substring, and fuzzy matching algorithm.
     * Matches every letter typed across titles, categories, subtitles, and keywords.
     */
    fun search(query: String, entries: List<SettingsSearchEntry>): List<SettingsSearchEntry> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return emptyList()

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        return entries.mapNotNull { entry ->
            val titleLower = entry.title.lowercase()
            val subtitleLower = entry.subtitle?.lowercase().orEmpty()
            val categoryLower = entry.category.lowercase()
            val keywordsCombined = entry.keywords.joinToString(" ").lowercase()
            val combinedText = "$titleLower $subtitleLower $categoryLower $keywordsCombined"

            // 1. Check if all individual search tokens match somewhere in the entry
            val matchesAllTokens = tokens.all { token ->
                titleLower.contains(token) ||
                    subtitleLower.contains(token) ||
                    categoryLower.contains(token) ||
                    keywordsCombined.contains(token) ||
                    isSubsequence(token, titleLower) ||
                    isSubsequence(token, keywordsCombined)
            }

            // 2. Check continuous substring match of whole query
            val matchesContiguous = combinedText.contains(trimmed) || isSubsequence(trimmed, titleLower) || isSubsequence(trimmed, keywordsCombined)

            if (!matchesAllTokens && !matchesContiguous) return@mapNotNull null

            // Calculate fine-grained relevance ranking score
            var score = 0

            // Exact or prefix title match
            if (titleLower == trimmed) {
                score += 1000
            } else if (titleLower.startsWith(trimmed)) {
                score += 600
            } else if (titleLower.contains(trimmed)) {
                score += 400
            }

            // Keyword matches
            for (kw in entry.keywords) {
                val kwLower = kw.lowercase()
                if (kwLower == trimmed) {
                    score += 500
                    break
                } else if (kwLower.startsWith(trimmed)) {
                    score += 350
                    break
                } else if (kwLower.contains(trimmed)) {
                    score += 200
                    break
                }
            }

            // Category match
            if (categoryLower.startsWith(trimmed)) {
                score += 250
            } else if (categoryLower.contains(trimmed)) {
                score += 150
            }

            // Subtitle match
            if (subtitleLower.contains(trimmed)) {
                score += 100
            }

            // Token based score
            val tokenTitleMatches = tokens.count { titleLower.contains(it) }
            score += tokenTitleMatches * 80

            val tokenKwMatches = tokens.count { keywordsCombined.contains(it) }
            score += tokenKwMatches * 40

            // Subsequence match in title
            if (isSubsequence(trimmed, titleLower)) {
                score += 50
            }

            Pair(entry, score)
        }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Checks if [pattern] characters appear in order within [text] (fuzzy sequence match).
     */
    private fun isSubsequence(pattern: String, text: String): Boolean {
        if (pattern.isEmpty()) return true
        if (text.length < pattern.length) return false
        var pIdx = 0
        for (i in text.indices) {
            if (text[i] == pattern[pIdx]) {
                pIdx++
                if (pIdx == pattern.length) return true
            }
        }
        return false
    }
}
