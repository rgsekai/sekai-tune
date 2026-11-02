package com.my.kizzy.gateway

import io.ktor.client.HttpClient

internal expect fun gatewayHttpClient(): HttpClient

/**
 * Milliseconds since boot, counting time the device spent asleep. Android's
 * `SystemClock.elapsedRealtime` where there is one: `nanoTime` can stop in deep sleep.
 */
internal expect fun elapsedRealtimeMs(): Long
