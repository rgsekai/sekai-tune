/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

/**
 * Types of audio sources supported by the application.
 */
enum class SourceKind {
    /**
     * External HTTP REST addon server.
     */
    ADDON,

    /**
     * Built-in YouTube / YouTube Music baseline catalog. Always enabled and last in priority.
     */
    YOUTUBE,

    // Reserved for future module/script scrapers
}
