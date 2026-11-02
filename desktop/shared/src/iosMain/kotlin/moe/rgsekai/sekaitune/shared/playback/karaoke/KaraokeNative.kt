package moe.rgsekai.sekaitune.shared.playback.karaoke

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

/**
 * What neural karaoke needs from Swift (iosApp/iosApp/KaraokeNative.swift): ONNX Runtime and a
 * decode of the track's stream. Installed once at startup; until then (or on any failure) the
 * separator can't be built and karaoke stays on the classic centre-cancel.
 */
@OptIn(ExperimentalForeignApi::class)
interface KaraokeNative {
    /** The ONNX model at [path], or null when it won't load. */
    fun openModel(path: String, threads: Int): KaraokeModelRun?

    /** A forward decode of [url] to 44.1 kHz interleaved stereo float, or null when it can't be opened. */
    fun openPcm(url: String, headers: Map<String, String>, mimeType: String, contentLength: Long, chunkBytes: Long): KaraokePcmSource?
}

@OptIn(ExperimentalForeignApi::class)
interface KaraokeModelRun {
    /** One inference: [input] and [output] are both the model's [1, 4, dimF, 256] floats. False on failure. */
    fun run(input: CPointer<FloatVar>, output: CPointer<FloatVar>): Boolean
    fun close()
}

@OptIn(ExperimentalForeignApi::class)
interface KaraokePcmSource {
    val durationFrames: Long

    /** Up to [maxFrames] interleaved stereo frames into [into]; 0 at the end, -1 on failure. */
    fun read(into: CPointer<FloatVar>, maxFrames: Int): Int

    /** Moves to [frame], returning where the next read starts; -1 where it can't. */
    fun seek(frame: Long): Long
    fun close()
}

object IosKaraoke {
    var native: KaraokeNative? = null
}

/** [KaraokeStems]' view of a [KaraokePcmSource]. */
@OptIn(ExperimentalForeignApi::class)
class IosPcmStream(private val source: KaraokePcmSource) : PcmStream {
    override val sampleRate = 44_100
    override val durationFrames = source.durationFrames
    private val block = FloatArray(BLOCK * 2)

    override fun next(): Array<FloatArray>? {
        val frames = block.usePinned { source.read(it.addressOf(0), BLOCK) }
        check(frames >= 0) { "karaoke decode failed" }
        if (frames == 0) return null
        return arrayOf(FloatArray(frames) { block[it * 2] }, FloatArray(frames) { block[it * 2 + 1] })
    }

    override fun seek(frame: Long) = source.seek(frame)

    override fun close() = source.close()

    private companion object {
        const val BLOCK = 16_384
    }
}
