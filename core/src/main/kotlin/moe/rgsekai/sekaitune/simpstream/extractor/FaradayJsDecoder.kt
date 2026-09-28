package moe.rgsekai.sekaitune.simpstream.extractor

import dev.maxrave.pipepipe.extractor.exceptions.ParsingException
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeApiDecoder
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeJavaScriptDecoder
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import moe.rgsekai.sekaitune.simpstream.SimpStreamLog
import moe.rgsekai.sekaitune.simpstream.cipher.FaradayCipherEngine
import java.util.concurrent.Executors

private const val TAG = "FaradayJsDecoder"
private const val JS_THREAD_STACK_BYTES = 32L * 1024L * 1024L

/**
 * Plugs [FaradayCipherEngine] into PipePipe as its local decoder, so signature and `n` challenges
 * are solved on the device instead of at `api.pipepipe.dev`.
 */
internal class FaradayJsDecoder : YoutubeJavaScriptDecoder {
    @Volatile
    var lastOutcome: String = "unused"
        private set

    val lastOutcomeLabel: String
        get() =
            when {
                lastOutcome.startsWith("local") -> "local"
                lastOutcome.startsWith("fallback") -> "pipepipe.dev"
                else -> "cached"
            }

    /**
     * Dedicated thread with 32 MB native stack.
     *
     * YouTube's n-transform recurses deeply past default stack limits. Running on QuickJS with 32MB stack
     * prevents native SIGSEGV crashes.
     */
    private val jsThread =
        Executors
            .newSingleThreadExecutor { runnable -> Thread(null, runnable, "QuickJs", JS_THREAD_STACK_BYTES) }
            .asCoroutineDispatcher()

    private val engine by lazy { FaradayCipherEngine(HttpClient(OkHttp), jsThread) }

    override fun getPlayerData(videoId: String): YoutubeJavaScriptDecoder.PlayerData {
        val info =
            runBlocking { engine.playerInfo() }
                ?: run {
                    lastOutcome = "fallback:no-player-metadata"
                    throw ParsingException("Faraday: no player metadata")
                }
        return YoutubeJavaScriptDecoder.PlayerData(info.playerId, info.signatureTimestamp)
    }

    override fun decodeBatch(
        playerId: String,
        signatures: List<String>?,
        throttlingParameters: List<String>?,
    ): YoutubeApiDecoder.BatchDecodeResult {
        val wantedSignatures = signatures.orEmpty().distinct()
        val wantedNParameters = throttlingParameters.orEmpty().distinct()
        val result = runBlocking { engine.decode(playerId, wantedSignatures, wantedNParameters) }

        val missing =
            wantedSignatures.count { it !in result.signatures } +
                wantedNParameters.count { it !in result.nParameters }
        val wanted = wantedSignatures.size + wantedNParameters.size
        if (missing > 0) {
            lastOutcome = "fallback:$missing-of-$wanted-unsolved"
            throw ParsingException("Faraday: $missing of $wanted unsolved")
        }
        lastOutcome = "local:sig=${wantedSignatures.size},n=${wantedNParameters.size}"
        SimpStreamLog.d(TAG, "solved $wanted locally (sig=${wantedSignatures.size} n=${wantedNParameters.size})")
        return YoutubeApiDecoder.BatchDecodeResult(result.signatures, result.nParameters)
    }

    /** Discard cached player table and solver after CDN rejected deciphered URL. */
    fun invalidate() = runBlocking { engine.invalidate() }
}
