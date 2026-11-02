package moe.rgsekai.sekaitune.shared.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

actual fun <T> runBlockingCompat(block: suspend CoroutineScope.() -> T): T = runBlocking(block = block)
