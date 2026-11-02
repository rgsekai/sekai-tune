package moe.rgsekai.sekaitune.shared.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** `Dispatchers.IO` and `runBlocking`, neither of which common coroutines exposes. */
expect val ioDispatcher: CoroutineDispatcher

expect fun <T> runBlockingCompat(block: suspend CoroutineScope.() -> T): T
