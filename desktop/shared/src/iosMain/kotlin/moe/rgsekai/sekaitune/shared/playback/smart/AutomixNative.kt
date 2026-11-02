package moe.rgsekai.sekaitune.shared.playback.smart

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar

/**
 * What Automix's analysis needs from the platform, implemented in Swift (iosApp/iosApp/AutomixNative.swift):
 * a decode of the track, the native DSP analyzer (native/analyzer, the C++ Android runs through JNI) and the
 * Beat This! model on ONNX Runtime. Only ever called from the analyzer's worker thread.
 */
@OptIn(ExperimentalForeignApi::class)
interface AutomixNative {
    /** The whole track as mono float at [DECODE_RATE] Hz, or null when it can't be opened. */
    fun decode(url: String, headers: Map<String, String>, mimeType: String, contentLength: Long, chunkBytes: Long): AutomixPcm?

    /** Beat This! over [count] mono [DECODE_RATE] Hz samples at [pcm]: mel front end, then the model in chunks. */
    fun beatLogits(modelPath: String, threads: Int, pcm: CPointer<FloatVar>, count: Int): AutomixLogits?

    /** Where the bundled beat model lives on disk. */
    fun modelPath(): String

    /** Where the bundled open-unmix vocal model lives on disk. */
    fun vocalModelPath(): String

    /**
     * open-unmix over [seconds] of the track from [startSec], decoded as 44.1 kHz stereo: the STFT front end, then the
     * model, handing back only the vocal band's bins ([firstBin] through [lastBin]) of the mix and of the model's vocal
     * estimate, in the `[channel][bin][VocalModel.FIXED_FRAMES]` layout [VocalModel.reduceToBandCurve] reads. Null when
     * the region can't be decoded or the model is missing.
     */
    fun vocalBands(
        modelPath: String, threads: Int, url: String, headers: Map<String, String>, mimeType: String,
        contentLength: Long, chunkBytes: Long, startSec: Double, seconds: Double, firstBin: Int, lastBin: Int,
    ): AutomixVocal?

    companion object { const val DECODE_RATE = 22_050.0 }
}

@OptIn(ExperimentalForeignApi::class)
interface AutomixPcm {
    val frames: Int
    fun copy(into: CPointer<FloatVar>, from: Int, count: Int)

    /** The analyzer's whole-track DSP features as JSON (see [TrackFeatureSet]), or null when it declined. */
    fun features(durationSeconds: Double): String?
    fun close()
}

@OptIn(ExperimentalForeignApi::class)
interface AutomixLogits {
    val frames: Int
    fun copy(beat: CPointer<FloatVar>, downbeat: CPointer<FloatVar>)
}

/** One region's vocal-band tensors; [frames] of the model's fixed width carry audio. */
@OptIn(ExperimentalForeignApi::class)
interface AutomixVocal {
    val frames: Int
    val count: Int
    fun copy(mix: CPointer<FloatVar>, target: CPointer<FloatVar>)
}

object IosAutomix {
    var native: AutomixNative? = null
}
