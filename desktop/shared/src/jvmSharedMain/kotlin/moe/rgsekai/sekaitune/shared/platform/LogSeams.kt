package moe.rgsekai.sekaitune.shared.platform

import kotlinx.coroutines.asContextElement
import java.util.TimeZone
import kotlin.coroutines.CoroutineContext

private val working = ThreadLocal<String?>()

actual fun currentAbout(): String? = working.get()

actual fun aboutContext(id: String?): CoroutineContext = working.asContextElement(id)

actual fun localOffsetMillis(epochMs: Long): Long = TimeZone.getDefault().getOffset(epochMs).toLong()
