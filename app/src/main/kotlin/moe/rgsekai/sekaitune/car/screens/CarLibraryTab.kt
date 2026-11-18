package moe.rgsekai.sekaitune.car.screens

import androidx.annotation.DrawableRes
import androidx.car.app.model.Row
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Template
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.car.CarApp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@UnstableApi
internal class CarLibraryTab(app: CarApp) : CarDataTab<CarLibraryTab.Data>(app, "library", R.string.car_tab_library, R.drawable.library_music) {
    class Data(val liked: Int, val downloaded: Int)

    override suspend fun fetch(): Data = coroutineScope {
        val liked = async { app.catalog.likedCount() }
        val downloaded = async { app.catalog.downloadedCount() }
        Data(liked.await(), downloaded.await())
    }

    override fun empty() = Data(0, 0)

    override fun build(data: Data): Template {
        val songs = { count: Int -> app.carContext.resources.getQuantityString(R.plurals.n_song, count, count) }
        val rows = listOf(
            entry(R.string.car_liked, songs(data.liked), R.drawable.favorite) {
                app.push(CarSongsScreen(app, app.string(R.string.car_liked)) { app.catalog.liked() })
            },
            entry(R.string.car_downloaded, songs(data.downloaded), R.drawable.download) {
                app.push(CarSongsScreen(app, app.string(R.string.car_downloaded)) { app.catalog.downloaded() })
            },
            entry(R.string.car_playlists, null, R.drawable.queue_music) {
                app.push(CarCollectionsScreen(app, app.string(R.string.car_playlists), grid = false) { app.catalog.playlists() })
            },
            entry(R.string.car_albums, null, R.drawable.album) {
                app.push(CarCollectionsScreen(app, app.string(R.string.car_albums), grid = true) { app.catalog.albums() })
            },
            entry(R.string.car_artists, null, R.drawable.artist) {
                app.push(CarCollectionsScreen(app, app.string(R.string.car_artists), grid = true) { app.catalog.artists() })
            },
        )
        return SectionedItemTemplate.Builder()
            .addSection(RowSection.Builder().setItems(rows).build())
            .addAction(app.playerButton())
            .build()
    }

    override fun loadingTemplate(): Template = SectionedItemTemplate.Builder().setLoading(true).build()

    private fun entry(title: Int, text: String?, @DrawableRes icon: Int, onClick: () -> Unit): Row =
        Row.Builder()
            .setTitle(app.string(title))
            .apply { if (text != null) addText(text) }
            .setImage(app.images.icon(icon), Row.IMAGE_TYPE_ICON)
            .setBrowsable(true)
            .setOnClickListener(onClick)
            .build()
}

