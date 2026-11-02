package moe.rgsekai.sekaitune.shared.platform

import kotlin.coroutines.CoroutineContext

/** Per-coroutine "which track is this line about" slot used by TrackLog; read on the current thread. */
expect fun currentAbout(): String?

expect fun aboutContext(id: String?): CoroutineContext

/** Local-time-zone offset from UTC at [epochMs], in milliseconds. */
expect fun localOffsetMillis(epochMs: Long): Long
