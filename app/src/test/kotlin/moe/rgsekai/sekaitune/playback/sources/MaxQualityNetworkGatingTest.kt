/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

import moe.rgsekai.sekaitune.constants.MaxQualityNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaxQualityNetworkGatingTest {

    @Test
    fun testWifiOnlyGating() {
        // Wi-Fi connected, unmetered, lowDataMode off
        assertTrue(
            AudioQualityManager.isMaxAllowed(
                preference = MaxQualityNetwork.WIFI_ONLY,
                isWifi = true,
                isCellular = false,
                isMetered = false,
                lowDataMode = false,
            )
        )

        // Cellular connected (not Wi-Fi) -> not allowed
        assertFalse(
            AudioQualityManager.isMaxAllowed(
                preference = MaxQualityNetwork.WIFI_ONLY,
                isWifi = false,
                isCellular = true,
                isMetered = true,
                lowDataMode = false,
            )
        )
    }

    @Test
    fun testMobileDataOnlyGating() {
        // Cellular connected, unmetered, lowDataMode off
        assertTrue(
            AudioQualityManager.isMaxAllowed(
                preference = MaxQualityNetwork.MOBILE_DATA_ONLY,
                isWifi = false,
                isCellular = true,
                isMetered = false,
                lowDataMode = false,
            )
        )

        // Wi-Fi connected (not cellular) -> not allowed
        assertFalse(
            AudioQualityManager.isMaxAllowed(
                preference = MaxQualityNetwork.MOBILE_DATA_ONLY,
                isWifi = true,
                isCellular = false,
                isMetered = false,
                lowDataMode = false,
            )
        )
    }

    @Test
    fun testWifiAndMobileDataGating() {
        // Wi-Fi
        assertTrue(
            AudioQualityManager.isMaxAllowed(
                preference = MaxQualityNetwork.WIFI_AND_MOBILE_DATA,
                isWifi = true,
                isCellular = false,
                isMetered = false,
                lowDataMode = false,
            )
        )

        // Cellular (unmetered)
        assertTrue(
            AudioQualityManager.isMaxAllowed(
                preference = MaxQualityNetwork.WIFI_AND_MOBILE_DATA,
                isWifi = false,
                isCellular = true,
                isMetered = false,
                lowDataMode = false,
            )
        )
    }

    @Test
    fun testLowDataModeWinsWhenMetered() {
        // Low Data Mode ON and metered network -> Low Data Mode must win (no Max / no addons)
        assertFalse(
            "Low Data Mode must win on metered cellular even with BOTH allowed",
            AudioQualityManager.isMaxAllowed(
                preference = MaxQualityNetwork.WIFI_AND_MOBILE_DATA,
                isWifi = false,
                isCellular = true,
                isMetered = true,
                lowDataMode = true,
            )
        )

        assertFalse(
            "Low Data Mode must win on metered cellular with MOBILE_DATA_ONLY",
            AudioQualityManager.isMaxAllowed(
                preference = MaxQualityNetwork.MOBILE_DATA_ONLY,
                isWifi = false,
                isCellular = true,
                isMetered = true,
                lowDataMode = true,
            )
        )

        assertFalse(
            "Low Data Mode must win on metered Wi-Fi with WIFI_ONLY",
            AudioQualityManager.isMaxAllowed(
                preference = MaxQualityNetwork.WIFI_ONLY,
                isWifi = true,
                isCellular = false,
                isMetered = true,
                lowDataMode = true,
            )
        )

        // If Low Data Mode is ON but network is NOT metered -> allowed
        assertTrue(
            AudioQualityManager.isMaxAllowed(
                preference = MaxQualityNetwork.WIFI_ONLY,
                isWifi = true,
                isCellular = false,
                isMetered = false,
                lowDataMode = true,
            )
        )
    }

    @Test
    fun testMaxQualityNetworkFromPreferences() {
        assertEquals(MaxQualityNetwork.WIFI_ONLY, MaxQualityNetwork.fromPreferences("WIFI_ONLY"))
        assertEquals(MaxQualityNetwork.WIFI_ONLY, MaxQualityNetwork.fromPreferences("wifi_only"))
        assertEquals(MaxQualityNetwork.MOBILE_DATA_ONLY, MaxQualityNetwork.fromPreferences("MOBILE_DATA_ONLY"))
        assertEquals(MaxQualityNetwork.MOBILE_DATA_ONLY, MaxQualityNetwork.fromPreferences("MOBILE_ONLY"))
        assertEquals(MaxQualityNetwork.WIFI_AND_MOBILE_DATA, MaxQualityNetwork.fromPreferences("WIFI_AND_MOBILE_DATA"))
        assertEquals(MaxQualityNetwork.WIFI_AND_MOBILE_DATA, MaxQualityNetwork.fromPreferences("BOTH"))
        assertEquals(MaxQualityNetwork.WIFI_ONLY, MaxQualityNetwork.fromPreferences(null))
        assertEquals(MaxQualityNetwork.WIFI_ONLY, MaxQualityNetwork.fromPreferences("unknown"))
    }
}
