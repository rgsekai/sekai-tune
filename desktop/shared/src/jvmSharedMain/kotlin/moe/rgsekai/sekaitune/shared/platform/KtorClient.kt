package moe.rgsekai.sekaitune.shared.platform

import moe.rgsekai.sekaitune.shared.data.Http
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

actual fun ktorClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp) {
    // Same OkHttp instance ExoPlayer streams through — see Http.
    engine { preconfigured = Http.client }
    config()
}
