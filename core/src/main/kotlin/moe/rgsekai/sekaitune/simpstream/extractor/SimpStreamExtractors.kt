package moe.rgsekai.sekaitune.simpstream.extractor

import moe.rgsekai.sekaitune.innertube.models.YouTubeClient
import moe.rgsekai.sekaitune.simpstream.ITAG
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import dev.maxrave.pipepipe.extractor.downloader.CancellableCall
import dev.maxrave.pipepipe.extractor.downloader.Downloader as PipePipeDownloader
import dev.maxrave.pipepipe.extractor.downloader.Request as PipePipeRequest
import dev.maxrave.pipepipe.extractor.downloader.Response as PipePipeResponse
import dev.maxrave.pipepipe.extractor.exceptions.ReCaptchaException as PipePipeReCaptchaException
import org.schabi.newpipe.extractor.downloader.Downloader as BraveDownloader
import org.schabi.newpipe.extractor.downloader.Request as BraveRequest
import org.schabi.newpipe.extractor.downloader.Response as BraveResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException as BraveReCaptchaException

private val CLEN_REGEX = Regex("""clen=(\d+)""")

internal fun contentLengthOf(url: String): Long? = CLEN_REGEX.find(url)?.groupValues?.get(1)?.toLongOrNull()

internal fun List<Pair<Int, String>>.hasRequiredItags(): Boolean {
    if (this.isEmpty()) return false
    val itags = this.mapTo(HashSet()) { it.first }
    val hasAudio = ITAG.AUDIO.any { it in itags } || itags.any { it == 140 || it == 251 || it == 250 || it == 249 || it == 139 || it == 96 }
    return hasAudio || itags.isNotEmpty()
}

class NewPipeDownloaderImpl(
    proxy: Proxy?,
) : PipePipeDownloader() {
    private val client =
        OkHttpClient
            .Builder()
            .proxy(proxy)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Throws(IOException::class, PipePipeReCaptchaException::class)
    override fun execute(request: PipePipeRequest): PipePipeResponse {
        val response = client.newCall(buildOkHttpRequest(request)).execute()

        if (response.code == 429) {
            response.close()
            throw PipePipeReCaptchaException("reCaptcha Challenge requested", request.url())
        }

        return response.toNewPipeResponse()
    }

    @Throws(IOException::class, PipePipeReCaptchaException::class)
    override fun executeAsync(
        request: PipePipeRequest,
        callback: AsyncCallback?,
    ): CancellableCall {
        val call = client.newCall(buildOkHttpRequest(request))
        val cancellable = CancellableCall(call)
        call.enqueue(
            object : okhttp3.Callback {
                override fun onFailure(
                    call: okhttp3.Call,
                    e: IOException,
                ) {
                    cancellable.setFinished()
                    callback?.onError(e)
                }

                override fun onResponse(
                    call: okhttp3.Call,
                    response: okhttp3.Response,
                ) {
                    try {
                        if (response.code == 429) {
                            response.close()
                            callback?.onError(
                                PipePipeReCaptchaException("reCaptcha Challenge requested", request.url()),
                            )
                            return
                        }
                        callback?.onSuccess(response.toNewPipeResponse())
                    } catch (e: Exception) {
                        callback?.onError(e)
                    } finally {
                        cancellable.setFinished()
                    }
                }
            },
        )
        return cancellable
    }

    private fun okhttp3.Response.toNewPipeResponse(): PipePipeResponse {
        val rawBytes = body.bytes()
        return PipePipeResponse(
            code,
            message,
            headers.toMultimap(),
            rawBytes.toString(Charsets.UTF_8),
            rawBytes,
            request.url.toString(),
        )
    }

    private fun buildOkHttpRequest(request: PipePipeRequest): okhttp3.Request {
        val builder =
            okhttp3.Request
                .Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())
                .addHeader("User-Agent", YouTubeClient.USER_AGENT_WEB)

        request.headers().forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                builder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    builder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                builder.header(headerName, headerValueList[0])
            }
        }
        return builder.build()
    }
}

class BraveNewPipeDownloaderImpl(
    proxy: Proxy?,
) : BraveDownloader() {
    private val client =
        OkHttpClient
            .Builder()
            .proxy(proxy)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Throws(IOException::class, BraveReCaptchaException::class)
    override fun execute(request: BraveRequest): BraveResponse {
        val response = client.newCall(buildOkHttpRequest(request)).execute()

        if (response.code == 429) {
            response.close()
            throw BraveReCaptchaException("reCaptcha Challenge requested", request.url())
        }

        val body = response.body.string()
        return BraveResponse(
            response.code,
            response.message,
            response.headers.toMultimap(),
            body,
            response.request.url.toString(),
        )
    }

    private fun buildOkHttpRequest(request: BraveRequest): okhttp3.Request {
        val builder =
            okhttp3.Request
                .Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())
                .addHeader("User-Agent", YouTubeClient.USER_AGENT_WEB)

        request.headers().forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                builder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    builder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                builder.header(headerName, headerValueList[0])
            }
        }
        return builder.build()
    }
}
