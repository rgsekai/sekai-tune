package moe.rgsekai.sekaitune.simpstream.cipher

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rgsekai.sekaitune.simpstream.SimpStreamLog
import kotlin.time.Clock

/**
 * On-device cipher engine solving YouTube's signature and `n`-parameter challenges using QuickJS
 * and precomputed player configs.
 */
internal class FaradayCipherEngine(
    private val httpClient: HttpClient,
    private val jsThread: CoroutineDispatcher,
    private val store: RemotePlayerConfigStore =
        RemotePlayerConfigStore(
            httpClient,
            InMemoryPlayerConfigRepository(PLAYER_CONFIG_URL),
        ),
) {
    private val mutex = Mutex()
    private val operationMutex = Mutex()

    @Volatile
    private var cachedPlayerId: String? = null

    @Volatile
    private var cachedPlayerIdAtMs: Long = 0L

    @Volatile
    private var cachedSolver: CachedSolver? = null

    private data class CachedSolver(
        val playerId: String,
        val configEpoch: Long,
        val solver: ZemerCipherSolver,
    )

    data class PlayerInfo(
        val playerId: String,
        val signatureTimestamp: Int,
    )

    data class DecodeResult(
        val signatures: Map<String, String>,
        val nParameters: Map<String, String>,
    )

    suspend fun playerInfo(): PlayerInfo? =
        operationMutex.withLock {
            val playerId = currentPlayerId() ?: return null
            val timestamp = store.getSignatureTimestamp(playerUrlFor(playerId)) ?: return null
            PlayerInfo(playerId, timestamp)
        }

    suspend fun decode(
        playerId: String,
        signatures: List<String>,
        nParameters: List<String>,
    ): DecodeResult = operationMutex.withLock { decodeLocked(playerId, signatures, nParameters) }

    private suspend fun decodeLocked(
        playerId: String,
        signatures: List<String>,
        nParameters: List<String>,
    ): DecodeResult {
        if (signatures.isEmpty() && nParameters.isEmpty()) return DecodeResult(emptyMap(), emptyMap())
        val solver = solverFor(playerId) ?: return DecodeResult(emptyMap(), emptyMap())
        return DecodeResult(
            signatures =
                signatures
                    .distinct()
                    .mapNotNull { challenge ->
                        solver.solveSignature(challenge)?.let { challenge to it }
                    }.toMap(),
            nParameters =
                nParameters
                    .distinct()
                    .mapNotNull { challenge ->
                        solver.solveN(challenge)?.let { challenge to it }
                    }.toMap(),
        )
    }

    suspend fun invalidate() = operationMutex.withLock { invalidateLocked() }

    private suspend fun invalidateLocked() {
        mutex.withLock {
            cachedPlayerId = null
            cachedPlayerIdAtMs = 0L
            cachedSolver?.solver?.dispose()
            cachedSolver = null
        }
        runCatching { store.refreshAfterStreamRejection() }
    }

    private suspend fun currentPlayerId(): String? {
        val now = Clock.System.now().toEpochMilliseconds()
        mutex.withLock {
            cachedPlayerId?.takeIf { now - cachedPlayerIdAtMs < PLAYER_ID_TTL_MS }?.let { return it }
        }
        val fetched = fetchPlayerId() ?: return null
        mutex.withLock {
            cachedPlayerId = fetched
            cachedPlayerIdAtMs = Clock.System.now().toEpochMilliseconds()
        }
        return fetched
    }

    private suspend fun fetchPlayerId(): String? =
        try {
            val response =
                httpClient.getTextWithoutRedirects(Url(IFRAME_API_URL), MAX_IFRAME_BYTES) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    header(HttpHeaders.Accept, "*/*")
                }
            val body = response.body
            if (body == null) {
                SimpStreamLog.w(TAG, "iframe_api returned HTTP ${response.status.value}")
                null
            } else {
                PLAYER_HASH_REGEX.find(body)?.groupValues?.getOrNull(1)
                    ?: run {
                        SimpStreamLog.w(TAG, "player hash not found in iframe_api")
                        null
                    }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SimpStreamLog.w(TAG, "iframe_api fetch failed: ${error.message}", error)
            null
        }

    private suspend fun solverFor(playerId: String): ZemerCipherSolver? {
        if (!PLAYER_ID_REGEX.matches(playerId)) return null
        val playerUrl = playerUrlFor(playerId)

        var config = store.getConfig(playerUrl)
        if (config == null) {
            store.forceRefresh(missingHash = playerId)
            config = store.getConfig(playerUrl)
        }
        if (config == null) {
            SimpStreamLog.d(TAG, "player not in remote table: $playerId")
            return null
        }

        val epoch = store.configEpoch
        mutex.withLock {
            cachedSolver
                ?.takeIf { it.playerId == playerId && it.configEpoch == epoch }
                ?.let { return it.solver }
        }

        return try {
            val playerCode = downloadPlayerCode(playerUrl) ?: return null
            val created = ZemerCipherSolver.create(playerCode, config, jsThread)
            val replaced =
                mutex.withLock {
                    val previous = cachedSolver
                    cachedSolver = CachedSolver(playerId, epoch, created)
                    previous
                }
            replaced?.solver?.dispose()
            created
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SimpStreamLog.w(TAG, "solver build failed for $playerId: ${error.message}", error)
            null
        }
    }

    private suspend fun downloadPlayerCode(playerUrl: String): String? =
        try {
            val response =
                httpClient.getTextWithoutRedirects(Url(playerUrl), MAX_PLAYER_SCRIPT_BYTES) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    header(HttpHeaders.Accept, "*/*")
                    header("Referer", "https://www.youtube.com/")
                }
            response.body ?: run {
                SimpStreamLog.w(TAG, "player script HTTP ${response.status.value}")
                null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SimpStreamLog.w(TAG, "player script fetch failed: ${error.message}", error)
            null
        }

    companion object {
        private const val TAG = "FaradayCipherEngine"

        const val PLAYER_CONFIG_URL: String =
            "https://raw.githubusercontent.com/maxrave-dev/simpmusic-files/main/registry/player_configs.json"

        private const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"
        private const val USER_AGENT = "okhttp/5.4.0"
        private const val MAX_IFRAME_BYTES = 512 * 1024
        private const val MAX_PLAYER_SCRIPT_BYTES = 8 * 1024 * 1024
        private const val PLAYER_ID_TTL_MS = 30 * 60 * 1000L

        private val PLAYER_HASH_REGEX = Regex("""player\\?/([a-z0-9]{8})\\?/""")
        private val PLAYER_ID_REGEX = Regex("^[A-Za-z0-9_-]{4,32}$")

        internal fun playerUrlFor(playerId: String): String = "https://www.youtube.com/s/player/$playerId/player_ias.vflset/en_GB/base.js"
    }
}

internal class InMemoryPlayerConfigRepository(
    private val url: String,
) : PlayerConfigRepository {
    override val enabled: Boolean = true
    override val sourceUrl: String = url
    override val defaultSourceUrl: String = url
    override var cachedJson: String = ""
    override var cachedAtMs: Long = 0L
    override var cachedSourceUrl: String = ""
    override var cachedEtag: String = ""
}
