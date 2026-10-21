package moe.rgsekai.sekaitune.playback.sources

/**
 * Quality ranking for audio stream candidates.
 * Lossless (FLAC, ALAC, WAV) strictly ranks above lossy.
 * Among lossless: higher bit depth, then higher sample rate, then higher bitrate.
 * Among lossy: higher bitrate.
 */
data class CandidateQualityRank(
    val isLossless: Boolean,
    val bitDepth: Int,
    val sampleRateHz: Int,
    val bitrateKbps: Int,
) : Comparable<CandidateQualityRank> {

    companion object {
        fun from(stream: SourceStream): CandidateQualityRank {
            val isLossless = stream.format in listOf(StreamFormat.FLAC, StreamFormat.ALAC, StreamFormat.WAV)
            val bitDepth = stream.bitDepth ?: if (isLossless) 16 else 0
            val sampleRate = stream.sampleRateHz ?: if (isLossless) 44100 else 0
            val bitrate = stream.bitrateKbps ?: if (isLossless) 1411 else 0
            return CandidateQualityRank(
                isLossless = isLossless,
                bitDepth = bitDepth,
                sampleRateHz = sampleRate,
                bitrateKbps = bitrate,
            )
        }

        fun from(
            format: StreamFormat,
            bitrateKbps: Int?,
            sampleRateHz: Int? = null,
            bitDepth: Int? = null,
        ): CandidateQualityRank {
            val isLossless = format in listOf(StreamFormat.FLAC, StreamFormat.ALAC, StreamFormat.WAV)
            val depth = bitDepth ?: if (isLossless) 16 else 0
            val rate = sampleRateHz ?: if (isLossless) 44100 else 0
            val kbps = bitrateKbps ?: if (isLossless) 1411 else 0
            return CandidateQualityRank(
                isLossless = isLossless,
                bitDepth = depth,
                sampleRateHz = rate,
                bitrateKbps = kbps,
            )
        }

        /**
         * Returns true if candidate is strictly better than the YouTube baseline.
         * Requirement: "lossless, 320 kbps or higher, only if it is better than the YouTube stream; otherwise stay on YouTube."
         */
        fun isStrictlyBetterThanYouTube(candidate: CandidateQualityRank, youtubeBitrateKbps: Int): Boolean {
            if (candidate.isLossless) return true
            return candidate.bitrateKbps >= 320 && candidate.bitrateKbps > youtubeBitrateKbps
        }

        /**
         * Computes the FINAL badge label and lossless flag from actual stream properties.
         * "Lossless" (FLAC/ALAC/WAV), "Hi-Res Lossless" (depth > 16 or rate > 48kHz),
         * otherwise codec and bitrate e.g. "MP3 320", "AAC 256", "Opus 150".
         */
        fun computeFinalQualityLabel(
            mimeType: String?,
            codecs: String? = null,
            bitrateBps: Int? = null,
            sampleRateHz: Int? = null,
            bitDepth: Int? = null,
            addonFormat: StreamFormat? = null,
        ): Pair<String, Boolean> {
            val rawMime = mimeType?.uppercase() ?: ""
            val rawCodecs = codecs?.uppercase() ?: ""

            val isLossless = rawMime.contains("FLAC") || rawCodecs.contains("FLAC") ||
                    rawMime.contains("ALAC") || rawCodecs.contains("ALAC") ||
                    rawMime.contains("WAV") || rawCodecs.contains("WAV") ||
                    addonFormat in listOf(StreamFormat.FLAC, StreamFormat.ALAC, StreamFormat.WAV)

            if (isLossless) {
                val depth = bitDepth ?: 16
                val rate = sampleRateHz ?: 44100
                val isHiRes = depth > 16 || rate > 48000
                val label = if (isHiRes) "Hi-Res Lossless" else "Lossless"
                return label to true
            }

            val codecName = when {
                rawMime.contains("OPUS") || rawCodecs.contains("OPUS") || rawMime.contains("WEBM") -> "Opus"
                rawMime.contains("AAC") || rawCodecs.contains("AAC") || rawCodecs.contains("MP4A") -> "AAC"
                rawMime.contains("MPEG") || rawMime.contains("MP3") || rawCodecs.contains("MP3") -> "MP3"
                rawMime.contains("VORBIS") || rawCodecs.contains("VORBIS") -> "Vorbis"
                rawCodecs.isNotBlank() -> rawCodecs.substringBefore(".").trim()
                else -> "AAC"
            }

            val kbps = if (bitrateBps != null && bitrateBps > 0) {
                bitrateBps / 1000
            } else {
                128
            }

            return "$codecName $kbps" to false
        }
    }

    override fun compareTo(other: CandidateQualityRank): Int {
        if (this.isLossless != other.isLossless) {
            return if (this.isLossless) 1 else -1
        }
        if (this.isLossless) {
            val depthCompare = this.bitDepth.compareTo(other.bitDepth)
            if (depthCompare != 0) return depthCompare
            val rateCompare = this.sampleRateHz.compareTo(other.sampleRateHz)
            if (rateCompare != 0) return rateCompare
            return this.bitrateKbps.compareTo(other.bitrateKbps)
        }
        return this.bitrateKbps.compareTo(other.bitrateKbps)
    }
}
