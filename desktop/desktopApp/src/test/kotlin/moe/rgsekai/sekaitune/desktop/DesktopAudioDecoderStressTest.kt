package moe.rgsekai.sekaitune.desktop

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopAudioDecoderStressTest {

    private val scratch = mutableListOf<File>()

    @AfterTest
    fun cleanUp() = scratch.forEach { it.delete() }

    @Test
    fun `repeated seek skip and close from another thread for 60 seconds does not crash`() {
        val file = tone(seconds = 10.0, rate = 44_100, hz = 440.0)
        val format = DesktopPcmFormat(44_100, 2, 2)

        val deadline = System.currentTimeMillis() + 60_000L
        val iterations = AtomicInteger(0)
        val seekCount = AtomicInteger(0)
        val closeCount = AtomicInteger(0)

        val executor = Executors.newFixedThreadPool(4)
        try {
            while (System.currentTimeMillis() < deadline) {
                val decoder = DesktopAudioDecoder()
                val opened = decoder.open(file.absolutePath, requested = format)
                if (opened.isFailure) continue

                val running = AtomicBoolean(true)

                // Background reading thread
                val readFuture = executor.submit {
                    try {
                        while (running.get()) {
                            val block = decoder.read() ?: break
                            if (block.isEmpty()) Thread.yield()
                        }
                    } catch (_: Throwable) {
                        // Handled errors are allowed, but JVM must not crash
                    }
                }

                // Concurrent thread doing random seeks, skip, and close
                val mutatorFuture = executor.submit {
                    val rng = Random(System.nanoTime())
                    val actions = rng.nextInt(3, 15)
                    for (i in 0 until actions) {
                        if (!running.get()) break
                        val action = rng.nextInt(10)
                        when {
                            action < 6 -> {
                                val targetUs = rng.nextLong(0, 8_000_000L)
                                decoder.seek(targetUs)
                                seekCount.incrementAndGet()
                            }
                            action in 6..7 -> {
                                Thread.sleep(rng.nextLong(1, 10))
                            }
                            else -> {
                                // Skip / close early
                                break
                            }
                        }
                    }
                    running.set(false)
                    decoder.close()
                    closeCount.incrementAndGet()
                }

                mutatorFuture.get(5, TimeUnit.SECONDS)
                readFuture.get(5, TimeUnit.SECONDS)
                decoder.close()
                iterations.incrementAndGet()
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        println("Stress test completed: ${iterations.get()} cycles, ${seekCount.get()} seeks, ${closeCount.get()} closes in 60s without crashing")
        assertTrue(iterations.get() > 50, "Expected > 50 iterations in 60s, got ${iterations.get()}")
    }

    @Test
    fun `stream URL becomes invalid mid-play ends cleanly without crashing`() {
        val toneFile = tone(seconds = 5.0, rate = 44_100, hz = 440.0)
        val toneBytes = toneFile.readBytes()

        val requestCount = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/bad_url") { exchange ->
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        server.createContext("/stream.wav") { exchange ->
            if (requestCount.incrementAndGet() == 1) {
                // First request provides headers and initial portion of stream
                exchange.responseHeaders.set("Content-Type", "audio/wav")
                exchange.sendResponseHeaders(200, toneBytes.size.toLong())
                val os = exchange.responseBody
                os.write(toneBytes, 0, minOf(32768, toneBytes.size))
                os.flush()
                exchange.close()
            } else {
                // Subsequent request fails with 403 Forbidden (URL expired / invalid)
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
            }
        }
        server.start()

        try {
            val port = server.address.port

            // 1. Bad URL fails open cleanly
            val badDecoder = DesktopAudioDecoder()
            val badResult = badDecoder.open(
                "http://127.0.0.1:$port/bad_url",
                requested = DesktopPcmFormat(44_100, 2, 2)
            )
            assertTrue(badResult.isFailure, "Opening invalid URL should fail")
            badDecoder.close()

            // 2. Stream URL becoming invalid mid-play terminates read cleanly without crashing JVM
            val streamDecoder = DesktopAudioDecoder()
            val result = streamDecoder.open(
                "http://127.0.0.1:$port/stream.wav",
                requested = DesktopPcmFormat(44_100, 2, 2)
            )
            if (result.isSuccess) {
                var reads = 0
                while (reads < 200) {
                    val block = streamDecoder.read() ?: break
                    reads++
                }
                streamDecoder.close()
            }
        } finally {
            server.stop(0)
        }
    }

    private fun tone(seconds: Double, rate: Int, hz: Double): File {
        val frames = (rate * seconds).toInt()
        val bytes = ByteArray(frames * 4)
        for (frame in 0 until frames) {
            val value = (sin(2.0 * PI * hz * frame / rate) * 30_000).toInt().toShort()
            val at = frame * 4
            bytes[at] = value.toInt().toByte()
            bytes[at + 1] = (value.toInt() shr 8).toByte()
            bytes[at + 2] = bytes[at]
            bytes[at + 3] = bytes[at + 1]
        }
        val format = AudioFormat(rate.toFloat(), 16, 2, true, false)
        val file = File.createTempFile("Sekai Tune-stress-tone", ".wav").also { scratch += it }
        AudioInputStream(ByteArrayInputStream(bytes), format, frames.toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
        }
        return file
    }
}
