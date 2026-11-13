package moe.rgsekai.sekaitune.eq

import moe.rgsekai.sekaitune.eq.autoeq.AutoEqTuning
import moe.rgsekai.sekaitune.eq.autoeq.HeadphoneForm
import moe.rgsekai.sekaitune.eq.data.FilterType
import moe.rgsekai.sekaitune.eq.data.ParametricEQBand
import kotlinx.serialization.Serializable

@Serializable
data class AutoEqCorrection(
    val model: String,
    val source: String,
    val rig: String? = null,
    val path: String,
    val form: HeadphoneForm = HeadphoneForm.OverEar,
    val tuning: AutoEqTuning = AutoEqTuning(),
    val filters: List<ParametricEQBand>,
    val preamp: Double,
)

@Serializable
data class EqPreset(
    val id: String,
    val name: String,
    val nodes: List<ParametricEQBand>,
    val preamp: Double = 0.0,
)

@Serializable
data class EqSettings(
    val enabled: Boolean = false,
    val nodes: List<ParametricEQBand> = EqPresets.flatNodes(),
    val preamp: Double = 0.0,
    val autoPreamp: Boolean = true,
    val presetId: String? = EqPresets.FlatId,
    val correction: AutoEqCorrection? = null,
    val correctionEnabled: Boolean = true,
)

object EqPresets {
    const val FlatId = "flat"

    val Frequencies = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    private const val OctaveQ = 1.41

    class Preset(val id: String, val name: String, val gains: DoubleArray) {
        val nodes: List<ParametricEQBand> get() = nodesFor(gains)
    }

    fun nodesFor(gains: DoubleArray): List<ParametricEQBand> = Frequencies.mapIndexed { i, f ->
        ParametricEQBand(frequency = f, gain = gains.getOrElse(i) { 0.0 }, q = OctaveQ, filterType = FilterType.PK)
    }

    fun flatNodes(): List<ParametricEQBand> = nodesFor(DoubleArray(Frequencies.size))

    val BuiltIn = listOf(
        Preset(FlatId, "Flat", doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        Preset("acoustic", "Acoustic", doubleArrayOf(4.5, 4.5, 3.5, 1.0, 1.5, 1.5, 3.0, 3.5, 3.0, 1.5)),
        Preset("bass_boost", "Bass Booster", doubleArrayOf(5.5, 4.5, 3.5, 2.5, 1.2, 0.0, 0.0, 0.0, 0.0, 0.0)),
        Preset("bass_cut", "Bass Reducer", doubleArrayOf(-5.5, -4.5, -3.5, -2.5, -1.2, 0.0, 0.0, 0.0, 0.0, 0.0)),
        Preset("classical", "Classical", doubleArrayOf(4.5, 3.5, 3.0, 2.5, -1.5, -1.5, 0.0, 2.0, 3.0, 3.5)),
        Preset("dance", "Dance", doubleArrayOf(3.5, 6.5, 5.0, 0.0, 2.0, 3.5, 5.0, 4.5, 3.5, 0.0)),
        Preset("deep", "Deep", doubleArrayOf(5.0, 3.5, 1.5, 1.0, 3.0, 2.5, 1.5, -2.0, -3.5, -4.5)),
        Preset("electronic", "Electronic", doubleArrayOf(4.5, 4.0, 1.5, 0.0, -2.0, 2.0, 1.0, 1.5, 4.5, 5.0)),
        Preset("hiphop", "Hip-Hop", doubleArrayOf(5.0, 4.0, 1.5, 3.0, -1.0, -1.0, 1.5, -0.5, 2.0, 3.0)),
        Preset("jazz", "Jazz", doubleArrayOf(4.0, 3.0, 1.5, 2.0, -1.5, -1.5, 0.0, 1.5, 3.0, 3.5)),
        Preset("loudness", "Loudness", doubleArrayOf(6.0, 4.0, 0.0, 0.0, -2.0, 0.0, -1.0, -5.0, 5.0, 1.0)),
        Preset("lounge", "Lounge", doubleArrayOf(-3.0, -1.5, -0.5, 1.5, 4.0, 2.5, 0.0, -1.5, 2.0, 1.0)),
        Preset("piano", "Piano", doubleArrayOf(3.0, 2.0, 0.0, 2.5, 3.0, 1.5, 3.5, 4.5, 3.0, 3.5)),
        Preset("pop", "Pop", doubleArrayOf(-1.5, -1.0, 0.0, 2.0, 4.0, 4.0, 2.0, 0.0, -1.0, -1.5)),
        Preset("rnb", "R&B", doubleArrayOf(2.5, 7.0, 5.5, 1.5, -2.5, -1.5, 2.5, 3.0, 3.0, 3.5)),
        Preset("rock", "Rock", doubleArrayOf(5.0, 4.0, 3.0, 1.5, -0.5, -1.0, 0.5, 2.5, 3.5, 4.5)),
        Preset("small_speakers", "Small Speakers", doubleArrayOf(5.5, 4.0, 3.5, 2.5, 1.0, 0.0, -1.0, -2.0, -3.5, -4.0)),
        Preset("spoken", "Spoken Word", doubleArrayOf(-3.5, -0.5, 0.0, 0.5, 3.5, 4.5, 5.0, 4.0, 2.5, 0.0)),
        Preset("treble_boost", "Treble Booster", doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.5, 3.5, 4.5, 5.5)),
        Preset("treble_cut", "Treble Reducer", doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, -1.0, -2.5, -3.5, -4.5, -5.5)),
        Preset("vocal", "Vocal Booster", doubleArrayOf(-1.5, -3.0, -3.0, 1.5, 3.5, 3.5, 3.0, 1.5, 0.0, -1.5)),
    )
}

