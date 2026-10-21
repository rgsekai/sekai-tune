/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

import moe.rgsekai.sekaitune.constants.AudioQuality
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioQualityTierTest {

    @Test
    fun testAudioQualityEnumOrdering() {
        val expected = arrayOf(
            AudioQuality.LOW,
            AudioQuality.MEDIUM,
            AudioQuality.HIGH,
            AudioQuality.MAX,
        )
        assertArrayEquals(expected, AudioQuality.values())
    }

    @Test
    fun testStoredTierMigration() {
        assertEquals(AudioQuality.LOW, AudioQuality.fromPreferences("LOW"))
        assertEquals(AudioQuality.LOW, AudioQuality.fromPreferences("low"))

        // Auto migrates to Medium
        assertEquals(AudioQuality.MEDIUM, AudioQuality.fromPreferences("AUTO"))
        assertEquals(AudioQuality.MEDIUM, AudioQuality.fromPreferences("auto"))
        assertEquals(AudioQuality.MEDIUM, AudioQuality.fromPreferences("MEDIUM"))
        assertEquals(AudioQuality.MEDIUM, AudioQuality.fromPreferences("medium"))

        // High stays High
        assertEquals(AudioQuality.HIGH, AudioQuality.fromPreferences("HIGH"))
        assertEquals(AudioQuality.HIGH, AudioQuality.fromPreferences("high"))

        // Highest and Max (beta) migrate to Max
        assertEquals(AudioQuality.MAX, AudioQuality.fromPreferences("HIGHEST"))
        assertEquals(AudioQuality.MAX, AudioQuality.fromPreferences("highest"))
        assertEquals(AudioQuality.MAX, AudioQuality.fromPreferences("MAX"))
        assertEquals(AudioQuality.MAX, AudioQuality.fromPreferences("max"))
        assertEquals(AudioQuality.MAX, AudioQuality.fromPreferences("Max (beta)"))

        // Fallback for null or unknown
        assertEquals(AudioQuality.MEDIUM, AudioQuality.fromPreferences(null))
        assertEquals(AudioQuality.MEDIUM, AudioQuality.fromPreferences(""))
        assertEquals(AudioQuality.MEDIUM, AudioQuality.fromPreferences("UNKNOWN_VALUE"))
    }

    @Test
    fun testCompatibilityAliases() {
        assertEquals(AudioQuality.MEDIUM, AudioQuality.AUTO)
        assertEquals(AudioQuality.MAX, AudioQuality.HIGHEST)
    }
}
