package com.my.kizzy.gateway

import moe.rgsekai.sekaitune.shared.platform.nanoTime
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun gatewayHttpClient(): HttpClient = HttpClient(Darwin) {
    install(WebSockets)
}

internal actual fun elapsedRealtimeMs(): Long = nanoTime() / 1_000_000L
