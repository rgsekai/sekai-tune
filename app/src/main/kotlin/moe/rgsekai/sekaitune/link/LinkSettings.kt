// LinkSettings.kt

package moe.rgsekai.sekaitune.link

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.AudioNormalizationKey
import moe.rgsekai.sekaitune.constants.AudioQuality
import moe.rgsekai.sekaitune.constants.AudioQualityKey
import moe.rgsekai.sekaitune.constants.CrossfadeDurationKey
import moe.rgsekai.sekaitune.constants.CrossfadeEnabledKey
import moe.rgsekai.sekaitune.constants.PlayerDesignStyleKey
import moe.rgsekai.sekaitune.constants.SkipSilenceKey
import moe.rgsekai.sekaitune.constants.VideoQuality
import moe.rgsekai.sekaitune.constants.VideoQualityKey
import moe.rgsekai.sekaitune.ui.player.styles.PlayerStyle
import moe.rgsekai.sekaitune.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// the settings a controlling phone may change on this device, sent with their labels so the phone draws whatever this one has
internal class LinkSettings(context: Context) {
    private val context = context.applicationContext

    private val available: List<LinkSetting> by lazy {
        val tv = DeviceKind.of(this.context).isTv
        all().filter { !(tv && it.phoneOnly) }
    }

    // called from a link server thread, so the wait for the settings store is fine here
    fun read(): JSONArray = runBlocking {
        val prefs = context.dataStore.data.first()
        JSONArray(available.map { it.toJson(context, prefs) })
    }

    // false when the key is unknown here or the value doesn't fit it
    fun write(key: String, value: Any?): Boolean = runBlocking {
        val setting = available.firstOrNull { it.key == key } ?: return@runBlocking false
        runCatching { withContext(Dispatchers.Main) { setting.write(context, value) } }.getOrDefault(false)
    }

    private fun all(): List<LinkSetting> = listOf(
        LinkSetting.Switch(
            key = "skipSilence",
            section = R.string.link_section_playback,
            title = R.string.link_setting_skip_silence,
            read = { prefs -> prefs[SkipSilenceKey] ?: false },
            write = preference(SkipSilenceKey),
        ),
        LinkSetting.Switch(
            key = "normalize",
            section = R.string.link_section_playback,
            title = R.string.link_setting_normalize,
            read = { prefs -> prefs[AudioNormalizationKey] ?: false },
            write = preference(AudioNormalizationKey),
        ),
        LinkSetting.Switch(
            key = "crossfade",
            section = R.string.link_section_playback,
            title = R.string.link_setting_transition,
            read = { prefs -> prefs[CrossfadeEnabledKey] ?: false },
            write = preference(CrossfadeEnabledKey),
        ),
        LinkSetting.Slider(
            key = "crossfadeSeconds",
            section = R.string.link_section_playback,
            title = R.string.link_setting_crossfade_length,
            min = 1f,
            max = 12f,
            step = 1f,
            label = { context, value -> context.getString(R.string.remote_seconds, value.toInt()) },
            read = { prefs -> prefs[CrossfadeDurationKey] ?: 4f },
            write = { context, value -> context.dataStore.edit { it[CrossfadeDurationKey] = (value as Number).toFloat() } },
        ),
        LinkSetting.Choice(
            key = "audioQuality",
            section = R.string.link_section_youtube,
            title = R.string.link_setting_audio_quality,
            options = { context ->
                listOf(
                    AudioQuality.AUTO.name to context.getString(R.string.link_quality_auto),
                    AudioQuality.HIGH.name to context.getString(R.string.link_quality_high),
                    AudioQuality.LOW.name to context.getString(R.string.link_quality_low),
                )
            },
            read = { prefs -> prefs[AudioQualityKey] ?: AudioQuality.AUTO.name },
            write = { context, value -> context.dataStore.edit { it[AudioQualityKey] = AudioQuality.valueOf(value.toString()).name } },
        ),
        LinkSetting.Choice(
            key = "videoQuality",
            section = R.string.link_section_youtube,
            title = R.string.link_setting_video_quality,
            options = { context ->
                VideoQualities.map { quality ->
                    quality.name to if (quality == VideoQuality.AUTO) context.getString(R.string.link_quality_auto) else context.getString(R.string.link_video_lines, quality.shortSide)
                }
            },
            read = { prefs -> prefs[VideoQualityKey] ?: VideoQuality.AUTO.name },
            write = { context, value -> context.dataStore.edit { it[VideoQualityKey] = VideoQuality.valueOf(value.toString()).name } },
        ),
        LinkSetting.Choice(
            key = "playerStyle",
            section = R.string.link_section_player,
            title = R.string.link_setting_player_style,
            options = { context -> stylesOn(context).map { it.name to context.getString(it.displayNameRes) } },
            read = { prefs -> prefs[PlayerDesignStyleKey] ?: PlayerStyle.DEFAULT.name },
            write = { context, value ->
                val style = stylesOn(context).firstOrNull { it.name == value.toString() } ?: PlayerStyle.DEFAULT
                context.dataStore.edit { it[PlayerDesignStyleKey] = style.name }
            },
        ),
    )

    private fun preference(key: Preferences.Key<Boolean>): suspend (Context, Boolean) -> Unit =
        { context, on -> context.dataStore.edit { it[key] = on } }

    companion object {
        private val VideoQualities = listOf(VideoQuality.AUTO, VideoQuality.Q1080, VideoQuality.Q720, VideoQuality.Q480, VideoQuality.Q360)

        fun stylesOn(context: Context): List<PlayerStyle> = PlayerStyle.entries
    }
}

// one setting as the phone draws it; each kind knows how to read itself from the store and write a value back
internal sealed class LinkSetting(
    val key: String,
    @StringRes val section: Int,
    @StringRes val title: Int,
    val phoneOnly: Boolean,
) {
    abstract suspend fun write(context: Context, value: Any?): Boolean

    protected abstract fun fill(json: JSONObject, context: Context, prefs: Preferences)

    protected abstract val type: String

    fun toJson(context: Context, prefs: Preferences): JSONObject = JSONObject()
        .put("key", key)
        .put("section", context.getString(section))
        .put("title", context.getString(title))
        .put("type", type)
        .also { fill(it, context, prefs) }

    class Switch(
        key: String,
        @StringRes section: Int,
        @StringRes title: Int,
        phoneOnly: Boolean = false,
        private val read: (Preferences) -> Boolean,
        private val write: suspend (Context, Boolean) -> Unit,
    ) : LinkSetting(key, section, title, phoneOnly) {
        override val type = "switch"

        override fun fill(json: JSONObject, context: Context, prefs: Preferences) {
            json.put("value", read(prefs))
        }

        override suspend fun write(context: Context, value: Any?): Boolean {
            write(context, value as? Boolean ?: return false)
            return true
        }
    }

    class Slider(
        key: String,
        @StringRes section: Int,
        @StringRes title: Int,
        val min: Float,
        val max: Float,
        val step: Float,
        private val label: (Context, Float) -> String,
        phoneOnly: Boolean = false,
        private val read: (Preferences) -> Float,
        private val write: suspend (Context, Float) -> Unit,
    ) : LinkSetting(key, section, title, phoneOnly) {
        override val type = "slider"

        override fun fill(json: JSONObject, context: Context, prefs: Preferences) {
            val v = read(prefs)
            json.put("value", v.toDouble())
                .put("min", min.toDouble())
                .put("max", max.toDouble())
                .put("step", step.toDouble())
                .put("label", label(context, v))
        }

        override suspend fun write(context: Context, value: Any?): Boolean {
            val v = (value as? Number)?.toFloat() ?: return false
            write(context, v.coerceIn(min, max))
            return true
        }
    }

    class Choice(
        key: String,
        @StringRes section: Int,
        @StringRes title: Int,
        private val options: (Context) -> List<Pair<String, String>>,
        phoneOnly: Boolean = false,
        private val read: (Preferences) -> String,
        private val write: suspend (Context, String) -> Unit,
    ) : LinkSetting(key, section, title, phoneOnly) {
        override val type = "choice"

        override fun fill(json: JSONObject, context: Context, prefs: Preferences) {
            val pairs = options(context)
            json.put("value", read(prefs))
                .put("options", JSONArray(pairs.map { JSONObject().put("value", it.first).put("label", it.second) }))
        }

        override suspend fun write(context: Context, value: Any?): Boolean {
            val str = value?.toString() ?: return false
            write(context, str)
            return true
        }
    }
}

