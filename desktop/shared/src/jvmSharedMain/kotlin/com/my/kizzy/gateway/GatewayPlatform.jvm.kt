package com.my.kizzy.gateway

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun gatewayHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(WebSockets)
}

private val androidElapsedRealtime: java.lang.reflect.Method? = runCatching {
    Class.forName("android.os.SystemClock").getMethod("elapsedRealtime")
}.getOrNull()

internal actual fun elapsedRealtimeMs(): Long =
    androidElapsedRealtime?.let { runCatching { it.invoke(null) as Long }.getOrNull() }
        ?: (System.nanoTime() / 1_000_000L)
