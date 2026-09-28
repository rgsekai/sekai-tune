package moe.rgsekai.sekaitune.simpstream.cipher

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wrapper for the QuickJS engine used to execute JavaScript code.
 */
class QuickJsEngine(
    private val jsThread: CoroutineDispatcher,
) {
    companion object {
        private const val EVALUATION_TIMEOUT_MS = 5000L
        private const val NATIVE_MEMORY_LIMIT_BYTES = 64L * 1024L * 1024L
        private const val NATIVE_STACK_LIMIT_BYTES = 16L * 1024L * 1024L

        private const val MAX_FUNCTION_INPUT_LENGTH = 4096
        private const val MAX_FUNCTION_RESULT_LENGTH = 4096
        private const val MAX_EVALUATION_RESULT_LENGTH = 16384

        private val JS_IDENTIFIER = Regex("""^[A-Za-z_$][A-Za-z0-9_$]*$""")

        fun jsStringLiteral(s: String): String =
            buildString(s.length + 8) {
                append('"')
                for (c in s) {
                    when (c) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> {
                            if (c.code < 0x20) {
                                append(String.format("\\u%04x", c.code))
                            } else {
                                append(c)
                            }
                        }
                    }
                }
                append('"')
            }
    }

    private val mutex = Mutex()
    private var quickJs: QuickJs? = null

    /** Initialize the QuickJS runtime. */
    suspend fun initialize() =
        withContext(jsThread) {
            mutex.withLock {
                if (quickJs == null) {
                    quickJs =
                        QuickJs
                            .create(jsThread)
                            .also {
                                it.evaluationTimeoutMillis = EVALUATION_TIMEOUT_MS
                                it.memoryLimit = NATIVE_MEMORY_LIMIT_BYTES
                                it.maxStackSize = NATIVE_STACK_LIMIT_BYTES
                            }
                }
            }
        }

    /** Execute JavaScript code and return the result. */
    suspend fun evaluate(
        code: String,
        maxResultLength: Int,
    ): String =
        withContext(jsThread) {
            require(maxResultLength in 1..MAX_EVALUATION_RESULT_LENGTH) { "Invalid QuickJS result limit" }
            mutex.withLock {
                val runtime = quickJs ?: throw IllegalStateException("QuickJS not initialized")
                val boundedCode =
                    """
                    (function() {
                      const value = ($code);
                      if (value == null) return "";
                      const text = String(value);
                      return text.length <= $maxResultLength ? text : "";
                    })()
                    """.trimIndent()
                runtime.evaluate<String?>(boundedCode).orEmpty()
            }
        }

    /** Execute JavaScript for side effects without asking QuickJS to marshal the final value. */
    suspend fun execute(code: String) {
        withContext(jsThread) {
            mutex.withLock {
                val runtime = quickJs ?: throw IllegalStateException("QuickJS not initialized")
                runtime.evaluate<Any?>("$code\n;undefined;")
            }
        }
    }

    /** Execute a JavaScript function with parameters. */
    suspend fun callFunction(
        functionName: String,
        input: String,
    ): String? =
        withContext(jsThread) {
            require(JS_IDENTIFIER.matches(functionName)) { "Invalid JavaScript function name" }
            if (input.length > MAX_FUNCTION_INPUT_LENGTH) return@withContext null
            mutex.withLock {
                val runtime = quickJs ?: throw IllegalStateException("QuickJS not initialized")
                val inputLiteral = jsStringLiteral(input)
                runtime.evaluate<String?>(
                    """
                    (function() {
                      const value = $functionName($inputLiteral);
                      if (value == null) return null;
                      const text = String(value);
                      return text.length <= $MAX_FUNCTION_RESULT_LENGTH ? text : null;
                    })()
                    """.trimIndent(),
                )
            }
        }

    /** Set up the global environment for YouTube player execution. */
    suspend fun setupYoutubeGlobals() =
        withContext(jsThread) {
            val setupCode =
                """
                if (typeof globalThis.XMLHttpRequest === "undefined") {
                    globalThis.XMLHttpRequest = { prototype: {} };
                }
                if (typeof URL === "undefined") {
                    globalThis.location = {
                        hash: "",
                        host: "www.youtube.com",
                        hostname: "www.youtube.com",
                        href: "https://www.youtube.com/watch?v=yt-dlp-wins",
                        origin: "https://www.youtube.com",
                        password: "",
                        pathname: "/watch",
                        port: "",
                        protocol: "https:",
                        search: "?v=yt-dlp-wins",
                        username: "",
                    };
                } else {
                    globalThis.location = new URL("https://www.youtube.com/watch?v=yt-dlp-wins");
                }
                if (typeof globalThis.document === "undefined") {
                    globalThis.document = Object.create(null);
                }
                if (typeof globalThis.navigator === "undefined") {
                    globalThis.navigator = Object.create(null);
                }
                if (typeof globalThis.self === "undefined") {
                    globalThis.self = globalThis;
                }
                if (typeof globalThis.window === "undefined") {
                    globalThis.window = globalThis;
                }
                if (typeof globalThis.Intl === "undefined") {
                    const NumberFormat = function(locale, options) {
                        this.options = options || {};
                    };
                    NumberFormat.supportedLocalesOf = function(locales) {
                        return Array.isArray(locales) ? locales : [locales];
                    };
                    NumberFormat.prototype.format = function(value) {
                        let formatted = String(value);
                        const minimumDigits = this.options.minimumIntegerDigits || 0;
                        while (formatted.length < minimumDigits) formatted = "0" + formatted;
                        return formatted;
                    };
                    const DateTimeFormat = function() {};
                    DateTimeFormat.prototype.resolvedOptions = function() {
                        return { timeZone: "UTC" };
                    };
                    DateTimeFormat.prototype.format = function(value) {
                        return String(value);
                    };
                    globalThis.Intl = { NumberFormat, DateTimeFormat };
                }
                """.trimIndent()

            execute(setupCode)
        }

    /** Clean up and release resources. */
    suspend fun dispose() {
        withContext(jsThread) {
            mutex.withLock {
                quickJs?.close()
                quickJs = null
            }
        }
    }
}
