package moe.rgsekai.sekaitune.car.screens

import androidx.car.app.model.CarIcon
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Template
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.car.CarApp
import moe.rgsekai.sekaitune.car.CarShelf
import moe.rgsekai.sekaitune.car.CarSong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@UnstableApi
internal class CarHomeTab(app: CarApp) : CarDataTab<CarHomeTab.Data>(app, "home", R.string.car_tab_home, R.drawable.home) {
    class Data(
        val picks: List<CarSong>,
        val pickCovers: List<CarIcon>,
        val shelves: List<CarShelf>,
        val shelfCovers: List<CarIcon>,
    )

    override suspend fun fetch(): Data = coroutineScope {
        val picks = async { app.catalog.quickPicks().take(app.gridLimit) }
        val shelves = async { runCatching { app.catalog.shelves() }.getOrDefault(emptyList()).take(app.gridLimit) }
        val songs = picks.await()
        val rows = shelves.await()
        Data(songs, app.images.covers(songs.map { it.thumbnail }), rows, app.images.covers(rows.map { it.thumbnail }))
    }

    override fun empty() = Data(emptyList(), emptyList(), emptyList(), emptyList())

    override fun build(data: Data): Template {
        val template = SectionedItemTemplate.Builder()
        if (data.picks.isNotEmpty()) {
            val tiles = data.picks.mapIndexed { index, song -> songTile(song, data.pickCovers[index]) { app.play(app.catalog.radio(song)) } }
            template.addSection(coverGrid().setTitle(app.string(R.string.car_quick_picks)).setItems(tiles).build())
        }
        if (data.shelves.isNotEmpty()) {
            val tiles = data.shelves.mapIndexed { index, shelf ->
                collectionTile(shelf.title, null, data.shelfCovers[index]) { app.push(CarShelfScreen(app, shelf)) }
            }
            template.addSection(coverGrid().setTitle(app.string(R.string.car_categories)).setItems(tiles).build())
        }
        if (data.picks.isEmpty() && data.shelves.isEmpty()) {
            template.addSection(RowSection.Builder().setNoItemsMessage(app.string(R.string.car_home_empty)).build())
        }
        return template.addAction(app.playerButton()).build()
    }

    override fun loadingTemplate(): Template = SectionedItemTemplate.Builder().setLoading(true).build()
}

