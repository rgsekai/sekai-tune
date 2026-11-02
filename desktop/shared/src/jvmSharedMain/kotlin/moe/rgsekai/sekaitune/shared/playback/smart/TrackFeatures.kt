/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard).
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Kushagra Singh (BitChord adaptation)
 *
 * Orchard's original source is licensed under the GNU Affero General Public
 * License, version 3 or later. Per AGPLv3 section 13, this file is combined
 * here into Sekai Tune -- a work licensed under the GNU General Public
 * License, version 3 or later -- and remains itself governed by the AGPLv3
 * as part of that combination.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package moe.rgsekai.sekaitune.shared.playback.smart


/**
 * Whole-track envelope, structure and key analysis, from the native DSP
 * analyzer (`native/analyzer/audio_analysis.cpp`).
 *
 * This answers "where does the music actually end, where can a transition
 * enter and leave, how loud is it there, and is anyone singing" — the
 * transition policy needs a beat grid (also produced here, from
 * autocorrelation) to know how to mix, and these features to know *where*,
 * and through the energy curve, whether an interior mix-out anchor would
 * skip silence or skip a minute of music.
 */
object TrackFeatures {

    /** True when the native library loaded. Analysis is optional, so this is a fact, not a fault. */
    val available: Boolean get() = NativeAnalysisLibrary.available

    /**
     * The rate the analyzer's window and hop constants assume, deliberately
     * low, because envelope and structure work needs time resolution rather
     * than bandwidth, and an eighth of the samples is an eighth of the work.
     */
    val sampleRate: Double by lazy { if (available) nativeSampleRate() else 11_025.0 }

    /**
     * Analyses [samples], which must be mono float PCM at [sampleRate].
     *
     * Returns null when the native library is missing or the analyzer
     * declined the input. Callers treat that as "no evidence", which the
     * policy already degrades on.
     */
    fun analyze(samples: FloatArray, durationSeconds: Double): TrackFeatureSet? {
        if (!available || samples.isEmpty()) return null
        val json = runCatching { nativeAnalyze(samples, sampleRate, durationSeconds) }
            .onFailure { AnalysisLog.warn("Native analysis failed", it) }
            .getOrNull() ?: return null
        return runCatching { TrackFeatureJson.parse(json) }
            .onFailure { AnalysisLog.warn("Could not parse analysis output", it) }
            .getOrNull()
    }

    /**
     * Converts mono float PCM from [inputRate] to [sampleRate] (or any other
     * target), with an anti-aliasing windowed-sinc filter — see
     * `native/analyzer/resampler.cpp`.
     *
     * Returns the input unchanged when the rates already match, and null when
     * the native library is missing or the rates are unusable.
     */
    fun resample(samples: FloatArray, inputRate: Double, outputRate: Double = sampleRate): FloatArray? {
        if (!available || samples.isEmpty() || inputRate <= 0 || outputRate <= 0) return null
        return nativeResample(samples, inputRate, outputRate).takeIf { it.isNotEmpty() }
    }

    @JvmStatic private external fun nativeAnalyze(
        samples: FloatArray,
        sampleRate: Double,
        duration: Double,
    ): String

    @JvmStatic private external fun nativeSampleRate(): Double

    @JvmStatic private external fun nativeResample(
        samples: FloatArray,
        inputRate: Double,
        outputRate: Double,
    ): FloatArray
}
