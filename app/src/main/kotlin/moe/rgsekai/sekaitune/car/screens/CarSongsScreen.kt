package moe.rgsekai.sekaitune.car.screens

import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.car.CarApp
import moe.rgsekai.sekaitune.car.CarSong
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@UnstableApi
class CarSongsScreen(
    private val app: CarApp,
    private val title: String,
    private val load: suspend () -> List<CarSong>,
) : Screen(app.carContext) {
    private class Loaded(val songs: List<CarSong>, val covers: List<CarIcon>, val failed: Boolean)

    private var loaded: Loaded? = null

    init {
        lifecycleScope.launch {
            loaded = try {
                val songs = load().take(app.listLimit)
                Loaded(songs, app.images.covers(songs.map { it.thumbnail }), failed = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Loaded(emptyList(), emptyList(), failed = true)
            }
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val header = Header.Builder().setStartHeaderAction(Action.BACK).setTitle(title)
        val data = loaded ?: return SectionedItemTemplate.Builder().setHeader(header.build()).setLoading(true).build()
        if (data.songs.isNotEmpty()) header.addEndHeaderAction(shuffle(data.songs))
        val rows = data.songs.mapIndexed { index, song ->
            songRow(song, data.covers[index]) { app.play(app.catalog.listFrom(title, data.songs, index)) }
        }
        val empty = app.string(if (data.failed) R.string.car_couldnt_load else R.string.car_list_empty)
        return SectionedItemTemplate.Builder()
            .setHeader(header.build())
            .addSection(RowSection.Builder().setItems(rows).setNoItemsMessage(empty).build())
            .addAction(app.playerButton())
            .build()
    }

    private fun shuffle(songs: List<CarSong>): Action =
        Action.Builder()
            .setIcon(app.images.icon(R.drawable.shuffle))
            .setTitle(app.string(R.string.car_shuffle))
            .setOnClickListener { app.play(ListQueue(title = title, items = songs.shuffled().map { it.item })) }
            .build()
}

