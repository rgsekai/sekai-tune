package moe.rgsekai.sekaitune.desktop

import moe.rgsekai.sekaitune.shared.playback.karaoke.PcmStream

/**
 * Karaoke's own decode of [stream], through the same FFmpeg path
 * and output format as the deck playing it — so its frames are the deck's
 * frames. See [moe.rgsekai.sekaitune.shared.playback.karaoke.KaraokeStems].
 *
 * A second read of the stream: the deck's decoder is owned by its read-ahead
 * thread, and this one runs at its own pace, ahead of the playhead.
 */
internal class DesktopKaraokeStream private constructor(
    private val decoder: DesktopAudioDecoder,
) : PcmStream {

    override val sampleRate: Int = decoder.outputFormat.sampleRate
    override val durationFrames: Long = decoder.durationUs?.let { it * sampleRate / 1_000_000 } ?: -1L
    private val channels = decoder.outputFormat.channels.coerceAtLeast(1)

    override fun next(): Array<FloatArray>? {
        val block = decoder.readSamples() ?: return null
        val frames = decoder.sampleCount / channels
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        for (frame in 0 until frames) {
            left[frame] = block[frame * channels]
            right[frame] = block[frame * channels + if (channels > 1) 1 else 0]
        }
        return arrayOf(left, right)
    }

    /** FFmpeg's seek drops what lands before the target, so the next chunk starts on it. */
    override fun seek(frame: Long): Long =
        if (decoder.seek(frame * 1_000_000 / sampleRate)) frame else -1L

    override fun close() = decoder.close()

    companion object {
        fun open(stream: DesktopStream): DesktopKaraokeStream? {
            val decoder = DesktopAudioDecoder()
            return decoder.open(
                url = stream.url,
                headers = stream.headers,
                requested = DesktopPcmFormat(44_100, 2, bytesPerSample = 4, isFloat = true),
                windowed = stream.windowedReads,
                transport = stream.transport,
            ).map { DesktopKaraokeStream(decoder) }
                .onFailure { decoder.close() }
                .getOrNull()
        }
    }
}
