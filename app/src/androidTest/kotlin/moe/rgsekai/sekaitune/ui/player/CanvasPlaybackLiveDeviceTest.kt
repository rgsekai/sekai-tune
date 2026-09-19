/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.canvas.CanvasRequestPolicy
import moe.rgsekai.sekaitune.canvas.CanvasSource
import moe.rgsekai.sekaitune.canvas.SekaiTuneCanvas
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CanvasPlaybackLiveDeviceTest {

    @Test
    fun testLivePlaybackLoopBoundaryNoFalsePositiveStall() = runBlocking {
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Fetch live Canvas with Mode=ALL (reproducing real session)
        val canvasArtwork = SekaiTuneCanvas.getCanvas(
            song = "After Hours",
            artists = listOf("The Weeknd"),
            durationMs = 361000L,
            policy = CanvasRequestPolicy(
                preferredSource = CanvasSource.TIDAL,
                allowFallback = true,
            ),
        )

        assertNotNull("Live Canvas must resolve for 'After Hours'", canvasArtwork)
        val animationUrl = canvasArtwork?.preferredAnimationUrl
        assertNotNull("Animation URL must not be null", animationUrl)
        println("=== [LIVE DEVICE TEST] Resolved Canvas Stream: $animationUrl ===")

        var exoPlayer: ExoPlayer? = null
        val loopCount = AtomicInteger(0)
        val stallCount = AtomicInteger(0)
        val firstFrameRendered = CountDownLatch(1)

        val watchdog = CanvasPlaybackWatchdog(
            stallTimeoutMs = 5000L,
            checkIntervalMs = 1000L,
            onStallDetected = { stalledMs, posMs ->
                stallCount.incrementAndGet()
                Timber.tag("CanvasPlayback").w("STALL TRIGGERED: stalledMs=$stalledMs at pos=$posMs")
            },
        )

        // Initialize and start ExoPlayer on main thread with a PlaceholderSurface for codec-compliant hardware decoding
        var surface: androidx.media3.exoplayer.video.PlaceholderSurface? = null

        withContext(Dispatchers.Main) {
            surface = androidx.media3.exoplayer.video.PlaceholderSurface.newInstance(context, false)

            val okHttpClient = OkHttpClient.Builder().build()
            val mediaSourceFactory = DefaultMediaSourceFactory(
                DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient)),
            )
            val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)

            val player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setRenderersFactory(renderersFactory)
                .build()
                .apply {
                    setVideoSurface(surface)
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .build()
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = true
                }

            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    Timber.tag("CanvasPlayback").d("Canvas rendered first frame for %s", animationUrl)
                    watchdog.onFirstFrameRendered()
                    firstFrameRendered.countDown()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN($playbackState)"
                    }
                    Timber.tag("CanvasPlayback").d("Canvas playbackState: %s (playWhenReady=%s)", stateName, player.playWhenReady)
                    if (playbackState == Player.STATE_READY && firstFrameRendered.count > 0) {
                        firstFrameRendered.countDown()
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    val loops = loopCount.incrementAndGet()
                    Timber.tag("CanvasPlayback").i(
                        "Canvas position discontinuity [Loop #%d]: %d ms -> %d ms (reason=%d)",
                        loops, oldPosition.positionMs, newPosition.positionMs, reason,
                    )
                    watchdog.onPositionDiscontinuity(newPosition.positionMs)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Timber.tag("CanvasPlayback").d("Canvas onIsPlayingChanged: %s", isPlaying)
                }
            })

            val lowercaseUrl = animationUrl!!.lowercase(Locale.ROOT)
            val mimeType = when {
                lowercaseUrl.contains("m3u8") || lowercaseUrl.contains(".hls") -> MimeTypes.APPLICATION_M3U8
                lowercaseUrl.contains("mp4") -> MimeTypes.VIDEO_MP4
                else -> null
            }

            val mediaItemBuilder = MediaItem.Builder().setUri(animationUrl)
            if (mimeType != null) mediaItemBuilder.setMimeType(mimeType)

            player.setMediaItem(mediaItemBuilder.build())
            player.prepare()
            player.play()
            exoPlayer = player
        }

        assertTrue("First frame must render within 10 seconds", firstFrameRendered.await(10, TimeUnit.SECONDS))

        // Monitor playback across at least 3 full loop cycles (~20 seconds for a ~5.3s TIDAL clip)
        println("=== [LIVE DEVICE TEST] Monitoring Live Canvas Playback across Loop Boundaries ===")
        val testDurationSeconds = 20
        for (sec in 1..testDurationSeconds) {
            delay(1000L)
            withContext(Dispatchers.Main) {
                val p = exoPlayer ?: return@withContext
                val currentPos = p.currentPosition
                val state = p.playbackState
                val isBufferingOrReady = state == Player.STATE_BUFFERING || state == Player.STATE_READY

                watchdog.tick(
                    currentPositionMs = currentPos,
                    isBufferingOrReady = isBufferingOrReady,
                    playWhenReady = p.playWhenReady,
                )

                Timber.tag("CanvasPlayback").d(
                    "t=%ds | pos=%d ms | state=%d | isPlaying=%s | stalledForMs=%d ms | loops=%d",
                    sec, currentPos, state, p.isPlaying, watchdog.stalledForMs, loopCount.get(),
                )
            }
        }

        withContext(Dispatchers.Main) {
            exoPlayer?.release()
            surface?.release()
        }

        println("=== [LIVE DEVICE TEST] Completed. Total Loops Observed: ${loopCount.get()}, Stall Triggers: ${stallCount.get()} ===")
        assertTrue("Must observe at least 2 full loop boundaries (observed: ${loopCount.get()})", loopCount.get() >= 2)
        assertEquals("False positive stall count must be exactly 0 across all loops", 0, stallCount.get())
        assertEquals("Watchdog stalled accumulator must be 0 ms", 0L, watchdog.stalledForMs)
    }
}
