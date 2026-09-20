/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CanvasAudioReactorTest {

    @Test
    fun testExtractBassEnergyWithEmptyOrSmallArray() {
        assertEquals(0f, extractBassEnergyFromFft(ByteArray(0)), 0.001f)
        assertEquals(0f, extractBassEnergyFromFft(ByteArray(2)), 0.001f)
        assertEquals(0f, extractBassEnergyFromFft(ByteArray(6)), 0.001f)
    }

    @Test
    fun testExtractBassEnergySilence() {
        val silentFft = ByteArray(128) { 0 }
        assertEquals(0f, extractBassEnergyFromFft(silentFft), 0.001f)
    }

    @Test
    fun testExtractBassEnergyIsolatesLowFrequencies() {
        // High frequencies only (k >= 10)
        val highFreqFft = ByteArray(128) { 0 }
        for (k in 10..30) {
            highFreqFft[2 * k] = 120.toByte()
            highFreqFft[2 * k + 1] = 120.toByte()
        }
        val highFreqEnergy = extractBassEnergyFromFft(highFreqFft)
        assertEquals(0f, highFreqEnergy, 0.001f)

        // Bass frequencies only (k = 1..4)
        val bassFft = ByteArray(128) { 0 }
        for (k in 1..4) {
            bassFft[2 * k] = 60.toByte()
            bassFft[2 * k + 1] = 60.toByte()
        }
        val bassEnergy = extractBassEnergyFromFft(bassFft)
        assertTrue("Bass energy must be extracted from low-frequency bins", bassEnergy > 0.2f)
        assertTrue("Bass energy must remain <= 1.0", bassEnergy <= 1.0f)
    }

    @Test
    fun testExtractBassEnergyClampedToOne() {
        val maxFft = ByteArray(128) { 127.toByte() }
        val energy = extractBassEnergyFromFft(maxFft)
        assertEquals(1.0f, energy, 0.001f)
    }

    @Test
    fun testSmoothBassEnergyAttackAndDecay() {
        // Attack test: fast rise towards target
        val attackResult = smoothBassEnergy(current = 0.0f, target = 1.0f)
        assertEquals(0.6f, attackResult, 0.001f)

        // Decay test: slow fall towards target
        val decayResult = smoothBassEnergy(current = 1.0f, target = 0.0f)
        assertEquals(0.85f, decayResult, 0.001f)
    }

    @Test
    fun testAudioReactiveIntensityConstants() {
        assertTrue("Kawarp audio-reactive intensity must be positive", KAWARP_AUDIO_REACTIVE_INTENSITY > 0f)
        assertTrue("Square tunnel audio-reactive intensity must be positive", TUNNEL_AUDIO_REACTIVE_INTENSITY > 0f)
    }
}
