package moe.rgsekai.sekaitune.shared.playback.karaoke

/** What [KaraokeStems]'s two worker threads need from the platform. */

/** A wait the worker can be woken out of early. */
internal expect class WakeSignal() {
    fun await(millis: Long)
    fun signal()
}

/** Runs [body] on a daemon thread of its own; [lowPriority] puts it just under normal. */
internal expect fun startKaraokeThread(name: String, lowPriority: Boolean, body: () -> Unit)

internal expect fun sleepMillis(millis: Long)

internal expect fun cpuCount(): Int

/** [samples] from [from] Hz to [to] Hz with the native resampler; null where it isn't available (iOS). */
internal expect fun resampleAudio(samples: FloatArray, from: Int, to: Int): FloatArray?
