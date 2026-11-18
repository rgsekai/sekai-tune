package moe.rgsekai.sekaitune.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.ScreenManager
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.media.MediaPlaybackManager
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.car.screens.CarLyricsScreen
import moe.rgsekai.sekaitune.car.screens.CarNowPlayingScreen
import moe.rgsekai.sekaitune.link.phone.PhoneLinkHost
import moe.rgsekai.sekaitune.link.phone.PhonePairing
import moe.rgsekai.sekaitune.playback.queues.Queue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

// what every screen of one car session shares
@UnstableApi
class CarApp(
    val carContext: CarContext,
    val scope: LifecycleCoroutineScope,
    val playback: CarPlayback,
    val catalog: CarCatalog,
    val images: CarImages,
    val lyrics: CarLyricsSource,
    val pairing: PhonePairing,
    val link: PhoneLinkHost,
) {
    private val screens: ScreenManager get() = carContext.getCarService(ScreenManager::class.java)

    private val _tokenReady = MutableStateFlow(false)

    // android auto's player only works once it has our media session, so nothing opens it before then
    val tokenReady: StateFlow<Boolean> = _tokenReady.asStateFlow()

    // how many rows and tiles this car takes in one list
    val listLimit: Int by lazy { limit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST, DefaultListLimit) }
    val gridLimit: Int by lazy { limit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID, DefaultGridLimit) }

    fun registerSession() {
        if (_tokenReady.value) return
        val token = playback.sessionToken() ?: return
        runCatching { carContext.getCarService(MediaPlaybackManager::class.java).registerMediaPlaybackToken(token) }
            .onSuccess { _tokenReady.value = true }
            .onFailure { Timber.tag("Car").w(it, "android auto didn't take the media session") }
    }

    // plays what was picked and opens the player, the way android auto's own media apps do
    fun play(queue: Queue) {
        playback.play(queue)
        showNowPlaying()
    }

    fun showNowPlaying() {
        scope.launch {
            tokenReady.first { it }
            val top = screens.top
            if (top is CarNowPlayingScreen || top is CarLyricsScreen) return@launch
            // the player and its lyrics take two more places, and android auto stops at five deep
            if (screens.stackSize > MaxStackBeforePlayer) screens.popToRoot()
            screens.push(CarNowPlayingScreen(this@CarApp))
        }
    }

    fun push(screen: androidx.car.app.Screen) {
        if (screens.stackSize >= MaxStack) return
        screens.push(screen)
    }

    fun toast(text: CharSequence) = CarToast.makeText(carContext, text, CarToast.LENGTH_LONG).show()

    fun string(id: Int, vararg args: Any): String = carContext.getString(id, *args)

    private fun limit(type: Int, fallback: Int): Int =
        runCatching { carContext.getCarService(ConstraintManager::class.java).getContentLimit(type) }.getOrDefault(fallback).coerceIn(1, MaxItems)

    private companion object {
        const val MaxStack = 5
        const val MaxStackBeforePlayer = 3
        const val DefaultListLimit = 50
        const val DefaultGridLimit = 12
        const val MaxItems = 100
    }
}

