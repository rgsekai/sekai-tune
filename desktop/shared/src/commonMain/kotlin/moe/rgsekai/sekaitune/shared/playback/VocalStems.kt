package moe.rgsekai.sekaitune.shared.playback

/**
 * A track's lead vocal, separated ahead of the playhead by a neural model, as
 * [KaraokeProcessor] reads it. Frames are the sink's own count of the track
 * ("media frames", from the stream's start); the stems map them onto the audio
 * they separated themselves, see [capture].
 *
 * Every call comes from the audio thread and must neither block nor allocate.
 */
interface VocalStems {
    /** The rate the vocal is separated at; a sink at any other rate can't use it. */
    val sampleRate: Int

    /** The sink has reached [frame]: separate around here, drop what is far behind. */
    fun observe(frame: Long)

    /**
     * The sink's input, interleaved, of which the first frame is [frame]. Used to
     * line the stems up against what the sink is actually playing, which can
     * differ from the separator's own decode by a codec's priming or a
     * container's coarse timestamps.
     */
    fun capture(frame: Long, samples: FloatArray, sampleCount: Int, channels: Int)

    /** The sink's count jumped (a seek, a new stream): line up again before [read] answers. */
    fun realign()

    /**
     * The vocal for [frames] frames from [frame], interleaved stereo, into
     * [into]. False — and [into] undefined — unless every one of them is
     * separated and aligned.
     */
    fun read(frame: Long, frames: Int, into: FloatArray): Boolean
}
