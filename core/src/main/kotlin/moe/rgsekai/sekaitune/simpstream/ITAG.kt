package moe.rgsekai.sekaitune.simpstream

object ITAG {
    /** AAC 48 kbps, adaptive audio */
    const val AUDIO_AAC_LOW: Int = 139

    /** AAC 128 kbps, adaptive audio */
    const val AUDIO_AAC_MEDIUM: Int = 140

    /** AAC 256 kbps, adaptive audio - the AAC twin of [AUDIO_OPUS_HIGH]. */
    const val AUDIO_AAC_HIGH: Int = 141

    /** Opus, adaptive audio - what the "Low" audio-quality setting selects. */
    const val AUDIO_OPUS_LOW: Int = 250

    /** Opus, adaptive audio - what the "Medium" / "High" audio-quality setting selects. */
    const val AUDIO_OPUS_MEDIUM: Int = 251

    /** Opus 256 kbps, adaptive audio - YouTube serves it to Premium accounts only. */
    const val AUDIO_OPUS_HIGH: Int = 774

    /** H.264 360p, adaptive video - what the "360p" video-quality setting selects. */
    const val VIDEO_360P: Int = 134

    /** H.264 720p, adaptive video - what the "720p" video-quality setting selects. */
    const val VIDEO_720P: Int = 136

    /** H.264 1080p, adaptive video - what the "1080p" video-quality setting selects. */
    const val VIDEO_1080P: Int = 137

    /** H.264 360p and AAC muxed into ONE progressive stream, for the single-URL (muxed) path. */
    const val MUXED_360P: Int = 18

    val AUDIO: List<Int> = listOf(AUDIO_AAC_LOW, AUDIO_AAC_MEDIUM, AUDIO_AAC_HIGH, AUDIO_OPUS_LOW, AUDIO_OPUS_MEDIUM, AUDIO_OPUS_HIGH)
    val VIDEO: List<Int> = listOf(VIDEO_360P, VIDEO_720P, VIDEO_1080P, MUXED_360P)

    fun highQualityTwinOf(itag: Int): Int? =
        when (itag) {
            AUDIO_OPUS_HIGH -> AUDIO_AAC_HIGH
            AUDIO_AAC_HIGH -> AUDIO_OPUS_HIGH
            else -> null
        }
}
