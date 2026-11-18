package moe.rgsekai.sekaitune.link.remote

import androidx.compose.runtime.Immutable
import moe.rgsekai.sekaitune.link.DeviceKind
import moe.rgsekai.sekaitune.link.LinkSongs
import moe.rgsekai.sekaitune.models.MediaMetadata
import org.json.JSONObject

// the device a phone is steering, as the pill and the remote show it
@Immutable
data class RemoteTarget(val id: String, val name: String, val kind: DeviceKind)

// one song still to play on the steered device, with its place in that device's queue
@Immutable
data class RemoteQueueItem(val index: Int, val song: MediaMetadata)

// what the steered device said it plays, and when it said it, so the seek bar can run on between asks
@Immutable
data class RemoteNow(
    val song: MediaMetadata?,
    val playing: Boolean,
    val position: Long,
    val duration: Long,
    val liked: Boolean,
    val upNext: List<RemoteQueueItem>,
    val readAt: Long,
) {
    // where the song is now, carried on from the last answer while it plays
    fun positionAt(now: Long): Long {
        if (!playing) return position
        val moved = position + (now - readAt)
        return if (duration > 0) moved.coerceAtMost(duration) else moved
    }

    companion object {
        fun fromJson(json: JSONObject, readAt: Long): RemoteNow {
            val queue = json.optJSONArray("upNext")
            return RemoteNow(
                song = json.optJSONObject("song")?.let(LinkSongs::fromJson),
                playing = json.optBoolean("playing"),
                position = json.optLong("position"),
                duration = json.optLong("duration"),
                liked = json.optBoolean("liked"),
                upNext = (0 until (queue?.length() ?: 0)).mapNotNull { i ->
                    val item = queue?.optJSONObject(i) ?: return@mapNotNull null
                    val song = item.optJSONObject("song")?.let(LinkSongs::fromJson) ?: return@mapNotNull null
                    RemoteQueueItem(item.optInt("index"), song)
                },
                readAt = readAt,
            )
        }
    }
}

// one setting on the steered device, as that device described it, in its own words
@Immutable
data class RemoteSetting(
    val key: String,
    val section: String,
    val title: String,
    val type: Type,
    val value: Any?,
    val options: List<Pair<String, String>>,
    val min: Float,
    val max: Float,
    val step: Float,
) {
    enum class Type { Switch, Choice, Seconds }

    companion object {
        fun listFromJson(json: JSONObject): List<RemoteSetting> {
            val array = json.optJSONArray("settings") ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val type = when (item.optString("type")) {
                    "switch" -> Type.Switch
                    "choice" -> Type.Choice
                    "seconds" -> Type.Seconds
                    // a newer device may have kinds this phone can't draw yet
                    else -> return@mapNotNull null
                }
                val options = item.optJSONArray("options")
                RemoteSetting(
                    key = item.optString("key"),
                    section = item.optString("section"),
                    title = item.optString("title"),
                    type = type,
                    value = item.opt("value"),
                    options = (0 until (options?.length() ?: 0)).mapNotNull { o ->
                        options?.optJSONObject(o)?.let { it.optString("value") to it.optString("label") }
                    },
                    min = item.optDouble("min", 0.0).toFloat(),
                    max = item.optDouble("max", 1.0).toFloat(),
                    step = item.optDouble("step", 1.0).toFloat(),
                )
            }
        }
    }
}

