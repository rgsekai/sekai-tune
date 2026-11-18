package moe.rgsekai.sekaitune.car.screens

import androidx.car.app.Screen
import androidx.car.app.media.model.MediaPlaybackTemplate
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Template
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.car.CarApp

@UnstableApi
class CarNowPlayingScreen(private val app: CarApp) : Screen(app.carContext) {
    override fun onGetTemplate(): Template {
        val lyrics = Action.Builder()
            .setIcon(app.images.icon(R.drawable.lyrics))
            .setTitle(app.string(R.string.car_lyrics))
            .setOnClickListener { app.push(CarLyricsScreen(app)) }
            .build()
        val header = Header.Builder()
            .setStartHeaderAction(Action.BACK)
            .addEndHeaderAction(lyrics)
            .build()
        return MediaPlaybackTemplate.Builder().setHeader(header).build()
    }
}

