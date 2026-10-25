/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.webdav

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * In-memory thread-safe state holder for active WebDAV credentials and connection parameters.
 *
 * Scopes authorization strictly to the configured scheme, host, and port so credentials
 * never leak to YouTube or any external host.
 */
object WebDavAuth {

    @Volatile var scheme: String? = null
        private set

    @Volatile var host: String? = null
        private set

    @Volatile var port: Int? = null
        private set

    @Volatile var authHeader: String? = null
        private set

    @Volatile var trustSelfSigned: Boolean = false
        private set

    /**
     * Updates in-memory WebDAV credentials and server identity.
     */
    fun update(
        url: String,
        username: String,
        password: String,
        trustSelfSignedCert: Boolean = false,
    ) {
        val address = WebDavConfig.splitAddress(url)
        scheme = address?.scheme?.lowercase(Locale.ROOT)
        host = address?.host?.lowercase(Locale.ROOT)
        port = address?.port
        authHeader = WebDavConfig.basicAuthHeader(username, password)
        trustSelfSigned = trustSelfSignedCert
    }

    /**
     * Clears all in-memory credentials and connection parameters.
     */
    fun clear() {
        scheme = null
        host = null
        port = null
        authHeader = null
        trustSelfSigned = false
    }

    /**
     * Determines whether an outgoing request should have WebDAV Authorization attached.
     * Enforces matching scheme, host, and port.
     */
    fun shouldAuthorize(requestScheme: String, requestHost: String, requestPort: Int): Boolean {
        val expectedScheme = scheme ?: return false
        val expectedHost = host ?: return false
        val expectedPort = port ?: return false
        val header = authHeader ?: return false

        if (header.isBlank()) return false
        if (!requestScheme.equals(expectedScheme, ignoreCase = true)) return false
        if (!requestHost.equals(expectedHost, ignoreCase = true)) return false
        if (requestPort != expectedPort) return false

        return true
    }

    /**
     * An OkHttp Interceptor that safely injects the WebDAV Basic Auth header
     * exclusively into requests matching the configured WebDAV server.
     */
    val authInterceptor: Interceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url

        if (request.header("Authorization") == null &&
            shouldAuthorize(url.scheme, url.host, url.port)
        ) {
            val header = authHeader
            if (!header.isNullOrBlank()) {
                val authenticatedRequest = request.newBuilder()
                    .header("Authorization", header)
                    .build()
                return@Interceptor chain.proceed(authenticatedRequest)
            }
        }
        chain.proceed(request)
    }

    /**
     * Creates a derived OkHttpClient specifically for WebDAV operations.
     * If [trustSelfSigned] is enabled, it configures a lenient trust manager
     * and hostname verifier scoped strictly to [host].
     */
    fun createWebDavHttpClient(baseClient: OkHttpClient = OkHttpClient()): OkHttpClient {
        val builder = baseClient.newBuilder()
            .addInterceptor(authInterceptor)

        if (trustSelfSigned) {
            val configuredHost = host
            try {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                    },
                )

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                val sslSocketFactory = sslContext.socketFactory

                val lenientHostnameVerifier = HostnameVerifier { hostname, session ->
                    if (configuredHost != null && hostname.equals(configuredHost, ignoreCase = true)) {
                        true
                    } else {
                        okhttp3.internal.tls.OkHostnameVerifier.verify(hostname, session)
                    }
                }

                builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier(lenientHostnameVerifier)
            } catch (_: Exception) {
                // If SSL configuration fails, proceed with default builder
            }
        }

        return builder.build()
    }
}
