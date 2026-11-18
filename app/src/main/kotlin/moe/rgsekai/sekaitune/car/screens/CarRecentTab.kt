package moe.rgsekai.sekaitune.car.screens

import androidx.car.app.model.CarIcon
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Template
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.car.CarApp
import moe.rgsekai.sekaitune.car.CarCollection
import moe.rgsekai.sekaitune.car.CarSong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@UnstableApi
internal class CarRecentTab(app: CarApp) : CarDataTab<CarRecentTab.Data>(app, "recent", R.string.car_tab_recent, R.drawable.history) {
    class Data(
        val songs: List<CarSong>,
        val songCovers: List<CarIcon>,
        val lists: List<CarCollection>,
        val listCovers: List<CarIcon>,
    )

    override suspend fun fetch(): Data = coroutineScope {
        val songs = async { app.catalog.recentSongs().take(app.listLimit) }
        val lists = async { app.catalog.recentCollections().take(app.gridLimit) }
        val recentSongs = songs.await()
        val recentLists = lists.await()
        Data(recentSongs, app.images.covers(recentSongs.map { it.thumbnail }), recentLists, app.images.covers(recentLists.map { it.thumbnail }))
    }

    override fun empty() = Data(emptyList(), emptyList(), emptyList(), emptyList())

    override fun build(data: Data): Template {
        val template = SectionedItemTemplate.Builder()
        if (data.lists.isNotEmpty()) {
            val tiles = data.lists.mapIndexed { index, list ->
                collectionTile(list.title, list.subtitle, data.listCovers[index]) { app.push(CarSongsScreen(app, list.title, list.songs)) }
            }
            template.addSection(coverGrid().setTitle(app.string(R.string.car_recent_lists)).setItems(tiles).build())
        }
        val title = app.string(R.string.car_tab_recent)
        val rows = data.songs.mapIndexed { index, song ->
            songRow(song, data.songCovers[index]) { app.play(app.catalog.listFrom(title, data.songs, index)) }
        }
        template.addSection(
            RowSection.Builder()
                .setTitle(app.string(R.string.car_recent_songs))
                .setItems(rows)
                .setNoItemsMessage(app.string(R.string.car_recent_empty))
                .build(),
        )
        return template.addAction(app.playerButton()).build()
    }

    override fun loadingTemplate(): Template = SectionedItemTemplate.Builder().setLoading(true).build()
}

