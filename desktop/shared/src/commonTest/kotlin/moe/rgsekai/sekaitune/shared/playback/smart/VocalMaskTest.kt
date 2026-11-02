package moe.rgsekai.sekaitune.shared.playback.smart

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VocalMaskTest {
    private val fixed = VocalModel.FIXED_FRAMES

    @Test
    fun `the band is 200 Hz to 4 kHz of a 4096 point STFT at 44100`() {
        assertEquals(18, VocalModel.lowBin)
        assertEquals(372, VocalModel.highBin)
        assertEquals(22.24, VocalModel.maxRegionSeconds, 0.01)
    }

    @Test
    fun `the curve is the mean clipped ratio of vocal to mix over the band and channels`() {
        val bins = VocalModel.BINS
        // Mix of 1 everywhere; the vocal is 0.25 in frame 0, 0.75 in frame 1 and louder than the mix (clipped to 1) in frame 2.
        val mix = FloatArray(2 * bins * fixed) { 1f }
        val target = FloatArray(2 * bins * fixed)
        for (c in 0 until 2) for (b in 0 until bins) {
            target[(c * bins + b) * fixed + 0] = 0.25f
            target[(c * bins + b) * fixed + 1] = 0.75f
            target[(c * bins + b) * fixed + 2] = 3f
        }
        val curve = VocalModel.reduceToBandCurve({ mix[it] }, { target[it] }, usableFrames = 4)
        assertEquals(0.25f, curve[0], 1e-6f)
        assertEquals(0.75f, curve[1], 1e-6f)
        assertEquals(1f, curve[2], 1e-6f)
        assertEquals(0f, curve[3], 1e-6f)
    }

    @Test
    fun `a tensor holding only the band reduces to the same curve as the full one`() {
        val bins = VocalModel.BINS
        val lo = VocalModel.lowBin
        val hi = VocalModel.highBin
        val full = FloatArray(2 * bins * fixed) { 0.1f + (it % 17) / 20f }
        val fullTarget = FloatArray(2 * bins * fixed) { (it % 13) / 20f }
        val band = hi - lo + 1
        val mixBand = FloatArray(2 * band * fixed)
        val targetBand = FloatArray(2 * band * fixed)
        for (c in 0 until 2) for (b in 0 until band) for (f in 0 until fixed) {
            mixBand[(c * band + b) * fixed + f] = full[(c * bins + lo + b) * fixed + f]
            targetBand[(c * band + b) * fixed + f] = fullTarget[(c * bins + lo + b) * fixed + f]
        }
        val a = VocalModel.reduceToBandCurve({ full[it] }, { fullTarget[it] }, 50)
        val b = VocalModel.reduceToBandCurve({ mixBand[it] }, { targetBand[it] }, 50, firstBin = lo, storedBins = band)
        assertContentEquals(a, b)
    }

    @Test
    fun `the mask is neutral outside the region the model ran over`() {
        val curve = (0 until 100).map { EnergySample(it * 1.0, 0.5) }
        val values = FloatArray(200) { 0.9f }
        val mask = VocalModel.maskOver(curve, values, regionStart = 10.0)
        assertEquals(VocalModel.NEUTRAL_VOCAL, mask[5])
        assertEquals(0.9, mask[10], 1e-6)
        // 43 frames a second: region frame 200 falls past the 200 values, about 4.6 s in.
        assertEquals(VocalModel.NEUTRAL_VOCAL, mask[16])
    }

    @Test
    fun `merging keeps measured instants and answers null when nothing ran`() {
        assertNull(VocalModel.mergeMasks(3, null, null))
        val head = doubleArrayOf(0.9, 0.5, 0.5)
        val tail = doubleArrayOf(0.5, 0.5, 0.1)
        val merged = VocalModel.mergeMasks(3, head, tail)!!
        assertEquals(listOf(0.9, 0.5, 0.1), merged)
        assertTrue(VocalModel.NEUTRAL_VOCAL < VOCAL_ACTIVE_THRESHOLD)
    }
}
