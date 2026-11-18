// LinkControlRoutes.kt

package moe.rgsekai.sekaitune.link

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import moe.rgsekai.sekaitune.constants.PlayerDesignStyleKey
import moe.rgsekai.sekaitune.extensions.metadata
import moe.rgsekai.sekaitune.extensions.toMediaItem
import moe.rgsekai.sekaitune.playback.PlayerConnection
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import moe.rgsekai.sekaitune.ui.player.styles.PlayerStyle
import moe.rgsekai.sekaitune.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// what a paired phone may ask of the device it controls: what's on, play this, line that up, skip, and the settings it may change here
internal class LinkControlRoutes(
    context: Context,
    private val onOpenPlayer: () -> Unit = {},
    private val onSync: () -> Unit = {},
) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val settings = LinkSettings(context)

    // asked of the system, so worked out on the first call instead of while the app starts
    private val styles by lazy { LinkSettings.stylesOn(this.context) }

    // songs go straight to the service: a phone that is itself steering something mustn't pass them on again
    fun handle(request: LinkRequest, connection: PlayerConnection): LinkResponse {
        val body = if (request.method == "POST") runCatching { JSONObject(request.bodyText()) }.getOrNull() ?: JSONObject() else JSONObject()
        return when (request.path) {
            "/now" -> LinkResponse.json(now(connection).toString())
            "/play" -> {
                val songs = LinkSongs.listFromJson(body.optJSONArray("songs"))
                if (songs.isEmpty()) return LinkResponse.status(400)
                val start = body.optInt("index").coerceIn(0, songs.lastIndex)
                onMain {
                    connection.service.playQueue(
                        ListQueue(
                            title = body.optString("title").ifBlank { null },
                            items = songs.map { it.toMediaItem() },
                            startIndex = start,
                            position = body.optLong("position"),
                        ),
                    )
                    onOpenPlayer()
                }
                ok()
            }
            "/next" -> {
                val songs = LinkSongs.listFromJson(body.optJSONArray("songs"))
                onMain { connection.service.playNext(songs.map { it.toMediaItem() }) }
                ok()
            }
            "/queue" -> {
                val songs = LinkSongs.listFromJson(body.optJSONArray("songs"))
                onMain { connection.service.addToQueue(songs.map { it.toMediaItem() }) }
                ok()
            }
            "/jump" -> {
                val index = body.optInt("index", -1)
                onMain {
                    val player = connection.player
                    if (index in 0 until player.mediaItemCount) {
                        player.seekToDefaultPosition(index)
                        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) player.prepare()
                        player.playWhenReady = true
                    }
                }
                ok()
            }
            "/remove" -> {
                val index = body.optInt("index", -1)
                onMain {
                    val player = connection.player
                    // the song playing stays, the same as on the queue page
                    if (index in 0 until player.mediaItemCount && index != player.currentMediaItemIndex) player.removeMediaItem(index)
                }
                ok()
            }
            "/control" -> control(connection, body)
            "/style" -> {
                val style = body.optString("style")
                if (styles.none { it.name == style }) return LinkResponse.status(400)
                settings.write("playerStyle", style)
                onMain(onOpenPlayer)
                ok()
            }
            "/settings" -> {
                if (request.method == "POST" && !settings.write(body.optString("key"), body.opt("value"))) return LinkResponse.status(400)
                LinkResponse.json(JSONObject().put("settings", settings.read()).toString())
            }
            else -> LinkResponse.status(404)
        }
    }

    private fun control(connection: PlayerConnection, body: JSONObject): LinkResponse {
        val position = body.optLong("position", -1L)
        when (body.optString("action")) {
            "sync" -> onSync()
            else -> onMain {
                val player = connection.player
                when (body.optString("action")) {
                    "toggle" -> if (player.isPlaying) player.pause() else player.play()
                    "play" -> player.play()
                    "pause" -> player.pause()
                    "next" -> player.seekToNextMediaItem()
                    "previous" -> player.seekToPreviousMediaItem()
                    "seek" -> if (position >= 0) player.seekTo(position)
                    "like" -> connection.service.toggleLike()
                    "open" -> onOpenPlayer()
                }
            }
        }
        return ok()
    }

    // what this device is playing and what comes after it, read on the main thread where the player lives
    private fun now(connection: PlayerConnection): JSONObject {
        var json = JSONObject()
        onMainAndWait {
            val player = connection.player
            val song = connection.mediaMetadata.value
            json = JSONObject()
                .put("playing", connection.isPlaying.value)
                .put("position", player.currentPosition.coerceAtLeast(0L))
                .put("duration", player.duration.coerceAtLeast(0L))
                .put("index", player.currentMediaItemIndex)
                .put("upNext", upNext(player))
                .apply { song?.let { put("song", LinkSongs.toJson(it)) } }
        }
        val songId = json.optJSONObject("song")?.optString("id")
        val liked = songId?.let { id -> runCatching { runBlocking { connection.database.song(id).first()?.song?.liked } }.getOrNull() } == true
        val style = runBlocking { context.dataStore.data.first()[PlayerDesignStyleKey] }
        return json
            .put("liked", liked)
            .put("style", style ?: PlayerStyle.DEFAULT.name)
            .put("styles", JSONArray(styles.map { JSONObject().put("name", it.name).put("label", context.getString(it.displayNameRes)) }))
    }

    // the songs still to play, in the order they will, shuffle included
    private fun upNext(player: Player): JSONArray {
        val out = JSONArray()
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return out
        var index = player.currentMediaItemIndex
        while (out.length() < MaxUpNext) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) break
            val song = player.getMediaItemAt(index).metadata ?: continue
            out.put(JSONObject().put("index", index).put("song", LinkSongs.toJson(song)))
        }
        return out
    }

    private fun ok() = LinkResponse.json("{}")

    private fun onMain(block: () -> Unit) {
        main.post(block)
    }

    private fun onMainAndWait(block: () -> Unit) {
        val done = CountDownLatch(1)
        main.post {
            try {
                block()
            } finally {
                done.countDown()
            }
        }
        done.await(2, TimeUnit.SECONDS)
    }

    private companion object {
        const val MaxUpNext = 25
    }
}

