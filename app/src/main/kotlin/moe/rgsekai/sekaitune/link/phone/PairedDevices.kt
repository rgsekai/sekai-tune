// PairedDevices.kt

package moe.rgsekai.sekaitune.link.phone

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import moe.rgsekai.sekaitune.link.DeviceKind
import moe.rgsekai.sekaitune.utils.SecretVault
import org.json.JSONArray
import org.json.JSONObject

// a device this phone is paired with; the key is what both sides show on every call
@Immutable
data class PairedDevice(
    val id: String,
    val name: String,
    val key: String,
    val kind: DeviceKind,
    val pairedAt: Long,
    val lastSeenAt: Long,
    val lastSyncAt: Long = 0L,
    // where a phone or tablet's own link server was last found; a tv says where it is on every hello instead
    val host: String? = null,
    val port: Int = 0,
    // this phone may play, skip and change settings there, and for another phone the other way round too
    val remoteControl: Boolean = true,
    // steering starts by itself as soon as the device turns up
    val autoControl: Boolean = false,
    // a tv may stream this phone's downloads and song files
    val streamLocal: Boolean = true,
    // a tv's library is replaced by this phone's and kept in step
    val mirrorLibrary: Boolean = true,
)

// a paired device that's around right now, with where it listens for this phone
@Immutable
data class LiveDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val host: String,
    val port: Int,
    val key: String,
    val seenAt: Long,
)

// paired devices as json in shared preferences (the keys sealed), so the boot receiver can read them without hilt
internal object PairedDeviceStore {
    const val Prefs = "sekaitune_link_devices"
    private const val ListKey = "devices"

    fun read(prefs: SharedPreferences): List<PairedDevice> {
        val array = runCatching { JSONArray(prefs.getString(ListKey, null) ?: return emptyList()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            PairedDevice(
                id = json.optString("id"),
                name = json.optString("name"),
                // sealed by this phone's vault; one from another phone's backup can't be opened, so that device pairs again
                key = SecretVault.open(json.optString("key"))?.ifBlank { null } ?: return@mapNotNull null,
                kind = DeviceKind.fromWire(json.optString("kind").ifBlank { null }, DeviceKind.Tv),
                pairedAt = json.optLong("pairedAt"),
                lastSeenAt = json.optLong("lastSeenAt"),
                lastSyncAt = json.optLong("lastSyncAt"),
                host = json.optString("host").ifBlank { null },
                port = json.optInt("port"),
                remoteControl = json.optBoolean("remoteControl", true),
                autoControl = json.optBoolean("autoControl", false),
                streamLocal = json.optBoolean("streamLocal", true),
                mirrorLibrary = json.optBoolean("mirrorLibrary", true),
            )
        }
    }

    fun write(prefs: SharedPreferences, list: List<PairedDevice>) {
        val array = JSONArray(
            list.map {
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("key", SecretVault.seal(it.key))
                    .put("kind", it.kind.wire)
                    .put("pairedAt", it.pairedAt)
                    .put("lastSeenAt", it.lastSeenAt)
                    .put("lastSyncAt", it.lastSyncAt)
                    .put("host", it.host.orEmpty())
                    .put("port", it.port)
                    .put("remoteControl", it.remoteControl)
                    .put("autoControl", it.autoControl)
                    .put("streamLocal", it.streamLocal)
                    .put("mirrorLibrary", it.mirrorLibrary)
            },
        )
        prefs.edit { putString(ListKey, array.toString()) }
    }

    fun hasAny(prefs: SharedPreferences): Boolean {
        val stored = prefs.getString(ListKey, null) ?: return false
        return stored.length > 2
    }
}

