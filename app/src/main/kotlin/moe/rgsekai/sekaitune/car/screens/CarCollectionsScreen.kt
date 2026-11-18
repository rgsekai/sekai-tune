package moe.rgsekai.sekaitune.car.screens

import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridSection
import androidx.car.app.model.Header
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.car.CarApp
import moe.rgsekai.sekaitune.car.CarCollection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@UnstableApi
class CarCollectionsScreen(
    private val app: CarApp,
    private val title: String,
    private val grid: Boolean,
    private val load: suspend () -> List<CarCollection>,
) : Screen(app.carContext) {
    private class Loaded(val lists: List<CarCollection>, val covers: List<CarIcon>, val failed: Boolean)

    private var loaded: Loaded? = null

    init {
        lifecycleScope.launch {
            loaded = try {
                val lists = load().take(if (grid) app.gridLimit.coerceAtLeast(app.listLimit) else app.listLimit)
                Loaded(lists, app.images.covers(lists.map { it.thumbnail }), failed = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Loaded(emptyList(), emptyList(), failed = true)
            }
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val header = Header.Builder().setStartHeaderAction(Action.BACK).setTitle(title).build()
        val data = loaded ?: return SectionedItemTemplate.Builder().setHeader(header).setLoading(true).build()
        val empty = app.string(if (data.failed) R.string.car_couldnt_load else R.string.car_list_empty)
        val open = { list: CarCollection -> app.push(CarSongsScreen(app, list.title, list.songs)) }
        val section = if (grid && data.lists.isNotEmpty()) {
            coverGrid()
                .setItems(data.lists.mapIndexed { index, list -> collectionTile(list.title, list.subtitle, data.covers[index]) { open(list) } })
                .apply { if (data.lists.all { it.round }) setItemImageShape(GridSection.ITEM_IMAGE_SHAPE_CIRCLE) }
                .setNoItemsMessage(empty)
                .build()
        } else {
            RowSection.Builder()
                .setItems(data.lists.mapIndexed { index, list -> collectionRow(list, data.covers[index]) { open(list) } })
                .setNoItemsMessage(empty)
                .build()
        }
        return SectionedItemTemplate.Builder()
            .setHeader(header)
            .addSection(section)
            .addAction(app.playerButton())
            .build()
    }
}

