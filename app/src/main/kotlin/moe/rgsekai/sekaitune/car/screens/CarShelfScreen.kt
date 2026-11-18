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
import moe.rgsekai.sekaitune.car.CarApp
import moe.rgsekai.sekaitune.car.CarShelf
import kotlinx.coroutines.launch

@UnstableApi
class CarShelfScreen(
    private val app: CarApp,
    private val shelf: CarShelf,
) : Screen(app.carContext) {
    private val songs = shelf.songs.take(app.listLimit)
    private val lists = shelf.collections.take(app.gridLimit)
    private var songCovers: List<CarIcon>? = null
    private var listCovers: List<CarIcon>? = null

    init {
        lifecycleScope.launch {
            listCovers = app.images.covers(lists.map { it.thumbnail })
            songCovers = app.images.covers(songs.map { it.thumbnail })
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val header = Header.Builder().setStartHeaderAction(Action.BACK).setTitle(shelf.title).build()
        val songIcons = songCovers
        val listIcons = listCovers
        if (songIcons == null || listIcons == null) return SectionedItemTemplate.Builder().setHeader(header).setLoading(true).build()
        val template = SectionedItemTemplate.Builder().setHeader(header)
        if (lists.isNotEmpty()) {
            val tiles = lists.mapIndexed { index, list ->
                collectionTile(list.title, list.subtitle, listIcons[index]) { app.push(CarSongsScreen(app, list.title, list.songs)) }
            }
            template.addSection(coverGrid().setItems(tiles).build())
        }
        if (songs.isNotEmpty()) {
            val rows = songs.mapIndexed { index, song -> songRow(song, songIcons[index]) { app.play(app.catalog.radio(song)) } }
            template.addSection(RowSection.Builder().setItems(rows).build())
        }
        return template.addAction(app.playerButton()).build()
    }
}

