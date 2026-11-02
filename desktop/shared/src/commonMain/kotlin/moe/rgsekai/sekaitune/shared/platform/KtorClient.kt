package moe.rgsekai.sekaitune.shared.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/** A Ktor client on the platform's engine: the app's shared OkHttp on JVM (googlevideo binds URLs to it), Darwin on iOS. */
expect fun ktorClient(config: HttpClientConfig<*>.() -> Unit): HttpClient
