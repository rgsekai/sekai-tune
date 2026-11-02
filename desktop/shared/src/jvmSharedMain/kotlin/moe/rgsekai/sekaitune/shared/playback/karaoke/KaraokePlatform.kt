package moe.rgsekai.sekaitune.shared.playback.karaoke

import moe.rgsekai.sekaitune.shared.playback.smart.MelSpectrogram

internal actual class WakeSignal actual constructor() {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = Object()

    actual fun await(millis: Long) {
        synchronized(lock) { lock.wait(millis) }
    }

    actual fun signal() {
        synchronized(lock) { lock.notifyAll() }
    }
}

internal actual fun startKaraokeThread(name: String, lowPriority: Boolean, body: () -> Unit) {
    Thread(body, name).apply {
        isDaemon = true
        if (lowPriority) priority = Thread.NORM_PRIORITY - 1
        start()
    }
}

internal actual fun sleepMillis(millis: Long) = Thread.sleep(millis)

internal actual fun cpuCount(): Int = Runtime.getRuntime().availableProcessors()

internal actual fun resampleAudio(samples: FloatArray, from: Int, to: Int): FloatArray? =
    MelSpectrogram.resample(samples, from.toDouble(), to.toDouble())
