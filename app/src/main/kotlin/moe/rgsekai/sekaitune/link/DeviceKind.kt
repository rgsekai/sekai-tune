// DeviceKind.kt

package moe.rgsekai.sekaitune.link

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

// what kind of thing a linked device is, so the list can draw it and only offer what it can really do
enum class DeviceKind(val wire: String) {
    Phone("phone"),
    Tablet("tablet"),
    Tv("tv"),
    Car("car"),
    ;

    // a tv mirrors a phone's library and is steered by it; the others are all phones of some size
    val isTv: Boolean get() = this == Tv

    companion object {
        // a peer from before kinds were sent says nothing, the caller knows what it must have been
        fun fromWire(value: String?, fallback: DeviceKind): DeviceKind = entries.firstOrNull { it.wire == value } ?: fallback

        fun of(context: Context): DeviceKind {
            if (context.isTelevision()) return Tv
            val mode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
            if (mode == Configuration.UI_MODE_TYPE_CAR || context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return Car
            return if (context.resources.configuration.smallestScreenWidthDp >= 600) Tablet else Phone
        }
    }
}

