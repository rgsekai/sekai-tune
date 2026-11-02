package moe.rgsekai.sekaitune.shared.playback.smart

import moe.rgsekai.sekaitune.shared.data.settings.AppSettings
import moe.rgsekai.sekaitune.shared.data.settings.TrackAnalysisState
import moe.rgsekai.sekaitune.shared.platform.ConcurrentMap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlin.time.Clock
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** Where a track's bytes can be read from, as the player handed them to the engine. */
class AnalysisSource(val url: String, val headers: Map<String, String>, val mime: String, val length: Long)

/**
 * Automix's analysis on iOS: the counterpart of Android's TrackAnalyzer, minus the cache-rendition and
 * vocal-model machinery. One worker at a time, off every audio and UI thread. Decode, the DSP features and the
 * Beat This! model run in Swift ([IosAutomix.native]); stitching them into a [TrackAnalysis] is the same as Android's
 * whole-track pass (features over the whole track, beat grid over its head and tail windows).
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalCoroutinesApi::class)
class AutomixAnalyzer(private val scope: CoroutineScope, private val chunkBytesFor: (String) -> Long) {
    private val store = AnalysisStore {
        (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String).toPath()
    }
    private val tracker = BeatTracker({ IosAutomix.native?.modelPath().orEmpty() }, { AppSettings.automixPerformanceMode.value.inferenceThreads })
    private val results = ConcurrentMap<String, TrackAnalysis>()
    private val running = ConcurrentMap<String, Boolean>()
    private val worker = Dispatchers.Default.limitedParallelism(1)

    fun analysisFor(trackId: String): TrackAnalysis =
        results[trackId] ?: store.load(trackId)?.also { results[trackId] = it } ?: TrackAnalysis(trackId = trackId)

    fun isRunning(trackId: String) = running[trackId] == true

    fun stateOf(trackId: String): TrackAnalysisState {
        val a = analysisFor(trackId)
        return when {
            a.isUsable -> TrackAnalysisState.ANALYSED
            isRunning(trackId) -> TrackAnalysisState.ANALYSING
            a.status == TrackAnalysis.STATUS_READY -> TrackAnalysisState.FAILED
            else -> TrackAnalysisState.WAITING
        }
    }

    /** Cheap to call every tick: a track measured, failed or already in flight is a no-op. */
    fun request(trackId: String, source: AnalysisSource, durationSeconds: Double) {
        if (results[trackId] != null || running[trackId] == true || IosAutomix.native == null) return
        if (store.load(trackId) != null) return
        running[trackId] = true
        scope.launch(worker) {
            try {
                results[trackId] = analyze(trackId, source, durationSeconds)
            } catch (e: Throwable) {
                AnalysisLog.warn("Analysis of $trackId failed", e)
                results[trackId] = failed(trackId, durationSeconds)
            } finally {
                running.remove(trackId)
            }
        }
    }

    private fun failed(trackId: String, duration: Double) =
        TrackAnalysis(status = TrackAnalysis.STATUS_READY, trackId = trackId, duration = duration)

    private fun analyze(trackId: String, s: AnalysisSource, knownDuration: Double): TrackAnalysis {
        val native = IosAutomix.native ?: return failed(trackId, knownDuration)
        val pcm = native.decode(s.url, s.headers, s.mime, s.length, chunkBytesFor(s.url)) ?: return failed(trackId, knownDuration)
        try {
            val rate = AutomixNative.DECODE_RATE
            val seconds = pcm.frames / rate
            // A decode that stopped early reads as a track that goes quiet, and the mix-out would land where the bytes ran out.
            if (knownDuration > 0 && seconds < knownDuration * MIN_DECODED_FRACTION) return failed(trackId, knownDuration)
            val features = pcm.features(seconds)?.let { runCatching { TrackFeatureJson.parse(it) }.getOrNull() }
                ?: return failed(trackId, seconds)

            val window = BeatTracker.WINDOW_SECONDS
            val head = grid(pcm, 0.0, minOf(window, seconds))
            val tailStart = maxOf(0.0, seconds - window)
            val tail = if (tailStart > window / 2) grid(pcm, tailStart, seconds) else null
            val leading = tail ?: head
            // The open-unmix vocal mask over the same head and tail windows (Android's TrackAnalyzer.region): the model
            // reads at most VocalModel.maxRegionSeconds of each, so it stands in for the DSP heuristic only there.
            val headMask = vocalMask(s, features, 0.0, minOf(window, seconds))
            val tailMask = if (tailStart > window / 2) vocalMask(s, features, tailStart, seconds) else null
            val merged = VocalModel.mergeMasks(features.energyCurve.size, headMask, tailMask)
            println(
                "Automix: analysed $trackId bpm=${leading?.bpm ?: features.bpm} conf=${leading?.beatConfidence ?: features.beatConfidence} " +
                    "contentEnd=${features.contentEndTime} vocalMask=${if (headMask != null || tailMask != null) "model" else "dsp"}",
            )
            (merged ?: features.vocalActivityMask).let { m ->
                // Only what the model measured: everything outside the head and tail windows reads as the neutral 0.5.
                val measured = m.filter { it != VocalModel.NEUTRAL_VOCAL }
                println("Automix: vocal mask summary $trackId source=${if (merged != null) "model" else "dsp"} size=${m.size} " +
                    "measured=${measured.size} min=${measured.minOrNull()} mean=${if (measured.isEmpty()) 0.0 else measured.average()} " +
                    "max=${measured.maxOrNull()} above0.5=${measured.count { it > 0.5 }}")
            }
            return TrackAnalysis(
                status = TrackAnalysis.STATUS_READY,
                trackId = trackId,
                duration = seconds,
                contentEndTime = features.contentEndTime.takeIf { it > 0 } ?: seconds,
                bpm = leading?.bpm ?: features.bpm,
                beatInterval = leading?.beatInterval ?: features.beatInterval,
                beatConfidence = leading?.beatConfidence ?: features.beatConfidence,
                downbeats = (head?.downbeats.orEmpty() + tail?.downbeats.orEmpty()).ifEmpty { features.downbeats }.sorted(),
                firstBeat = head?.firstBeat ?: features.firstBeat,
                phraseBoundaries = features.phraseBoundaries,
                key = features.key,
                keyConfidence = features.keyConfidence,
                audibleStartTime = features.audibleStartTime,
                pickupTime = features.pickupTime,
                introEndTime = features.introEndTime,
                outroStartTime = features.outroStartTime,
                mixInTime = features.mixInTime,
                mixOutTime = features.mixOutTime,
                mixInCandidates = features.mixInCandidates,
                mixOutCandidates = features.mixOutCandidates,
                energyCurve = features.energyCurve,
                lowEnergyCurve = features.lowEnergyCurve,
                // The model's mask where it ran, the DSP heuristic's where it didn't, as Android does.
                vocalActivityMask = merged ?: features.vocalActivityMask,
                vocalProbability = features.vocalProbability,
            ).also { store.save(trackId, it) }
        } finally {
            pcm.close()
        }
    }

    /** open-unmix's vocal activity over one region of the track, filled onto the energy curve; null where it didn't run. */
    private fun vocalMask(s: AnalysisSource, features: TrackFeatureSet, start: Double, end: Double): DoubleArray? {
        val native = IosAutomix.native ?: return null
        val curve = features.energyCurve
        val seconds = minOf(VocalModel.maxRegionSeconds, end - start)
        if (curve.isEmpty() || seconds < 1.0) return null
        val low = VocalModel.lowBin
        val high = VocalModel.highBin
        val started = Clock.System.now()
        val vocal = native.vocalBands(
            native.vocalModelPath(), AppSettings.automixPerformanceMode.value.inferenceThreads, s.url, s.headers, s.mime,
            s.length, chunkBytesFor(s.url), start, seconds, low, high,
        ) ?: return null
        val mix = FloatArray(vocal.count)
        val target = FloatArray(vocal.count)
        mix.usePinned { m -> target.usePinned { t -> vocal.copy(m.addressOf(0), t.addressOf(0)) } }
        val values = VocalModel.reduceToBandCurve({ mix[it] }, { target[it] }, vocal.frames, firstBin = low, storedBins = high - low + 1)
        println("Automix: vocal mask ${vocal.frames} frames from ${start}s in ${(Clock.System.now() - started).inWholeMilliseconds}ms")
        return VocalModel.maskOver(curve, values, start)
    }

    private fun grid(pcm: AutomixPcm, fromSeconds: Double, toSeconds: Double): BeatTracker.Grid? {
        val rate = AutomixNative.DECODE_RATE
        val from = (fromSeconds * rate).toInt()
        val count = ((toSeconds * rate).toInt() - from).coerceAtLeast(0)
        if (count < rate) return null
        val samples = FloatArray(count)
        samples.usePinned { pcm.copy(it.addressOf(0), from, count) }
        return tracker.track(samples, offsetSeconds = fromSeconds)
    }

    private companion object {
        const val MIN_DECODED_FRACTION = 0.95
    }
}
