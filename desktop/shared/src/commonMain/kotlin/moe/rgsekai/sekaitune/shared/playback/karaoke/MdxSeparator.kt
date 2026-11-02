package moe.rgsekai.sekaitune.shared.playback.karaoke

/**
 * An MDX-Net vocal model and the STFT it was trained on, from UVR's own model
 * table (MIT).
 */
enum class MdxModel(
    val nFft: Int,
    val dimF: Int,
    /** UVR's per-model gain on the vocal before it is subtracted from the mix. */
    val compensate: Float,
) {
    /**
     * UVR-MDX-NET 9482, the one karaoke ships: a third of the work of UVR's
     * larger Voc_FT, which runs slower than real time on a phone's CPU.
     */
    MDX_9482(nFft = 6_144, dimF = 2_048, compensate = 1.035f);

    /** Context the model needs either side of what is kept from a chunk. */
    val trim: Int get() = nFft / 2
}

/** The vocal in one 44.1 kHz chunk of [MdxSeparator.CHUNK] frames. */
fun interface ChunkSeparator {
    fun separate(left: FloatArray, right: FloatArray, vocalLeft: FloatArray, vocalRight: FloatArray)
}

/**
 * One chunk of vocal separation with one of UVR's MDX-Net models (Kim /
 * Anjok07, MIT) — see [MdxModel] — the model [KaraokeModel] ships.
 *
 * MDX-Net is a TFC-TDF U-Net over a complex spectrogram: it takes the mix's
 * STFT (left and right, real and imaginary, as four channels) and returns the
 * vocal's. The STFT and its inverse are done here, exactly as the model was
 * trained on them — `torch.stft` with a periodic Hann window, `center=True`
 * reflect padding, [MdxModel.nFft] points, hop 1024, the lowest [MdxModel.dimF]
 * bins kept and the three lowest zeroed — so that the numbers match the Python
 * reference (python-audio-separator) bin for bin.
 *
 * Input is 44.1 kHz, [CHUNK] frames: about 5.9 s, of which the outer
 * [MdxModel.trim] on either side is context the caller throws away. Inference is the expensive
 * part (a couple of seconds per chunk on a phone's big cores); the transforms
 * are ~5% of it.
 *
 * Not thread-safe; one instance per worker.
 *
 * The ONNX Runtime half is JVM; iOS hands it to Swift (KaraokeNative).
 */
expect class MdxSeparator(modelPath: String, threads: Int, model: MdxModel) : ChunkSeparator {
    val model: MdxModel

    /** Thrown out of [separate] when [abort] cut it short. */
    class Aborted() : Exception

    /** Cuts the inference under way short, from any thread; [separate] then throws [Aborted]. */
    fun abort()

    fun close()

    companion object {
        val SAMPLE_RATE: Int
        val HOP: Int
        val DIM_T: Int
        val CHUNK: Int
    }
}
