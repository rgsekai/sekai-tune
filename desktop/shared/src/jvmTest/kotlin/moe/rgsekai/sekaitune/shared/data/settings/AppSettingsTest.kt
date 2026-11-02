package moe.rgsekai.sekaitune.shared.data.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {
    /** Keys are hard-coded: they are what Android's `sekai_settings` file already holds, so a rename must fail here. */
    @Test
    fun valuesRoundTripUnderTheLegacyKeys() {
        val store = MapSettings()
        AppSettings.init(store, AppSettingsHost())
        AppSettings.setThemeMode(ThemeMode.LIGHT)
        AppSettings.setCrossfadeSeconds(7)
        AppSettings.setWebDavUrl("http://nas/ ")
        assertEquals("LIGHT", store.getString("theme_mode", ""))
        assertEquals(7, store.getInt("crossfade_seconds", -1))
        assertEquals("http://nas", store.getString("webdav_url", ""))

        AppSettings.init(store, AppSettingsHost())
        assertEquals(ThemeMode.LIGHT, AppSettings.themeMode.value)
        assertEquals(7, AppSettings.crossfadeSeconds.value)
        assertEquals("http://nas", AppSettings.webdavUrl.value)
    }
}
