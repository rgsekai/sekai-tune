package moe.rgsekai.sekaitune.shared.playback.karaoke

import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow

internal actual class WakeSignal actual constructor() {
    private val condition = NSCondition()

    actual fun await(millis: Long) {
        condition.lock()
        condition.waitUntilDate(NSDate.dateWithTimeIntervalSinceNow(millis / 1000.0))
        condition.unlock()
    }

    actual fun signal() {
        condition.lock()
        condition.broadcast()
        condition.unlock()
    }
}

internal actual fun startKaraokeThread(name: String, lowPriority: Boolean, body: () -> Unit) {
    NSThread(block = body).apply {
        this.name = name
        if (lowPriority) threadPriority = 0.4
        start()
    }
}

internal actual fun sleepMillis(millis: Long) = NSThread.sleepForTimeInterval(millis / 1000.0)

internal actual fun cpuCount(): Int = NSProcessInfo.processInfo.activeProcessorCount.toInt()

// ponytail: no resampler on iOS; the Swift decode already delivers 44.1 kHz, which is all karaoke asks of it.
internal actual fun resampleAudio(samples: FloatArray, from: Int, to: Int): FloatArray? = null
