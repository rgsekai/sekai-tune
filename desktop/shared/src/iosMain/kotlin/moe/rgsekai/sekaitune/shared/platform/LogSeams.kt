package moe.rgsekai.sekaitune.shared.platform

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

// ponytail: no per-track attribution on iOS (no coroutine-aware thread-local); unattributed lines show in every track's log.
actual fun currentAbout(): String? = null

actual fun aboutContext(id: String?): CoroutineContext = EmptyCoroutineContext

actual fun localOffsetMillis(epochMs: Long): Long =
    NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)) * 1000L
