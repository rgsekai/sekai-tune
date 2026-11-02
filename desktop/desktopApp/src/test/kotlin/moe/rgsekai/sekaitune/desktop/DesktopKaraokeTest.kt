package moe.rgsekai.sekaitune.desktop

import okio.Path.Companion.toOkioPath
import moe.rgsekai.sekaitune.shared.playback.Karaoke
import moe.rgsekai.sekaitune.shared.playback.KaraokeProcessor
import moe.rgsekai.sekaitune.shared.playback.karaoke.KaraokeModel
import moe.rgsekai.sekaitune.shared.playback.karaoke.KaraokeStems
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The desktop deck's neural karaoke end to end on a real recording: the deck's
 * FFmpeg decode, a second decode feeding the stems, the model, alignment, and
 * the subtraction. Needs a track, so it only runs where `SEKAI_KARAOKE_REF` holds
 * the model (`UVR_MDXNET_9482.onnx`) and `SEKAI_KARAOKE_TRACK`
 * names an audio file. Writes the before and after as WAVs beside the track.
 */
class DesktopKaraokeTest {

    @Test
    fun `a deck takes the vocal out of a real song`() {
        val models = System.getenv("SEKAI_KARAOKE_REF")?.let(::File) ?: return
        val track = System.getenv("SEKAI_KARAOKE_TRACK")?.let(::File) ?: return
        DesktopAnalysisRuntime.ensureStarted()
        val model = KaraokeModel.model
        KaraokeModel.install { File(models, KaraokeModel.ASSET).path }
        KaraokeStems.install(kotlin.io.path.createTempDirectory("karaoke").toOkioPath())
        Karaoke.setEnabled(true)

        val deck = DesktopAudioDecoder()
        deck.open(track.path, requested = DesktopPcmFormat(44_100, 2, bytesPerSample = 4, isFloat = true)).getOrThrow()
        val rate = deck.outputFormat.sampleRate
        val channels = deck.outputFormat.channels
        val stems = KaraokeStems.forTrack("test|$model|${track.path}", "test") {
            DesktopKaraokeStream.open(DesktopStream(url = track.path))
        }
        val karaoke = KaraokeProcessor().apply {
            configure(rate, channels)
            setEnabled(true)
            bind(stems)
        }

        val before = ArrayList<FloatArray>()
        val after = ArrayList<FloatArray>()
        var frame = 0L
        var stemFrames = 0L
        val seconds = 25
        while (frame < rate.toLong() * seconds) {
            val block = deck.readSamples() ?: break
            val count = deck.sampleCount
            before += block.copyOf(count)
            karaoke.process(block, count, frame * 1_000_000 / rate)
            after += block.copyOf(count)
            frame += count / channels
            if (karaoke.isUsingStems) stemFrames += count / channels
            // In real time, as a listener hears it: the worker separates ahead of the deck.
            Thread.sleep(count / channels * 1_000L / rate)
        }
        deck.close()
        Karaoke.setEnabled(false)

        val original = before.flatMap { it.asList() }.toFloatArray()
        val processed = after.flatMap { it.asList() }.toFloatArray()
        write(File(track.parentFile, "karaoke_before.wav"), original, rate, channels)
        write(File(track.parentFile, "karaoke_after_${model.name.lowercase()}.wav"), processed, rate, channels)
        val tail = original.size / 2
        var input = 0.0
        var output = 0.0
        for (i in tail until original.size) {
            input += original[i] * original[i]
            output += processed[i] * processed[i]
        }
        println("neural for ${stemFrames * 100 / frame}% of the deck; second half ${10 * log10(output / input)} dB")
        // The first few seconds are the spectral fallback: the opening silence can't be aligned
        // on, and the first segment takes a few seconds to separate.
        assertTrue(stemFrames > frame / 2, "the neural vocal covered only $stemFrames of $frame frames")
    }

    private fun write(file: File, samples: FloatArray, rate: Int, channels: Int) {
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            val data = samples.size * 2
            fun int(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun short(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.writeBytes("RIFF"); int(36 + data); out.writeBytes("WAVEfmt "); int(16); short(1); short(channels)
            int(rate); int(rate * channels * 2); short(channels * 2); short(16); out.writeBytes("data"); int(data)
            val bytes = ByteArray(data)
            for (i in samples.indices) {
                val v = (samples[i].coerceIn(-1f, 1f) * 32_767).toInt()
                bytes[2 * i] = v.toByte()
                bytes[2 * i + 1] = (v shr 8).toByte()
            }
            out.write(bytes)
        }
    }
}
