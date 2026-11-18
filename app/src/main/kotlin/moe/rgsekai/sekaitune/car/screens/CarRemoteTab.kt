package moe.rgsekai.sekaitune.car.screens

import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.car.CarApp
import moe.rgsekai.sekaitune.link.phone.PhoneLinkHost
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// lets a passenger's phone steer the music
@UnstableApi
internal class CarRemoteTab(
    private val app: CarApp,
    private val onChange: () -> Unit,
) : CarTabPage {
    override val id = "remote"
    override val title = R.string.car_tab_remote
    override val icon = R.drawable.smartphone
    override val isReady = true

    private data class Shown(val code: String?, val phones: List<String>)

    private var shown = Shown(code = null, phones = emptyList())
    private var watching: Job? = null

    override fun template(): Template {
        val code = shown.code?.let { "${it.take(3)} ${it.drop(3)}" } ?: app.string(R.string.car_pair_getting_code)
        val status = when (shown.phones.size) {
            0 -> app.string(R.string.car_pair_waiting)
            1 -> app.string(R.string.car_pair_connected, shown.phones.first())
            else -> app.carContext.resources.getQuantityString(
                R.plurals.car_pair_connected_more,
                shown.phones.size - 1,
                shown.phones.first(),
                shown.phones.size - 1,
            )
        }
        val pane = Pane.Builder()
            .addRow(row(R.string.car_pair_code, code, R.drawable.key))
            .addRow(row(R.string.car_pair_status, status, R.drawable.smartphone))
            .addRow(row(R.string.car_pair_how, app.string(R.string.car_pair_steps), R.drawable.info))
            .build()
        return PaneTemplate.Builder(pane).build()
    }

    override fun reload(onFirstShown: () -> Unit) = Unit

    override fun catchUp() = Unit

    override fun onSelected() {
        app.pairing.open()
        shown = snapshot()
        if (watching?.isActive == true) return
        watching = app.scope.launch {
            combine(app.pairing.code, app.link.live, app.link.devices, ticks()) { _, _, _, _ -> snapshot() }.collect { next ->
                if (next == shown) return@collect
                shown = next
                onChange()
            }
        }
    }

    override fun onLeft() {
        watching?.cancel()
        watching = null
        app.pairing.close()
    }

    private fun snapshot(): Shown {
        val steering = app.link.devices.value.filter { !it.kind.isTv && it.remoteControl }.map { it.id }.toSet()
        val phones = app.link.liveDevices().filter { it.id in steering }.map { it.name }.sorted()
        return Shown(app.pairing.code.value?.code, phones)
    }

    private fun ticks() = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(PhoneLinkHost.LiveForMs / 4)
        }
    }

    private fun row(title: Int, text: String, icon: Int): Row =
        Row.Builder()
            .setTitle(app.string(title))
            .addText(text)
            .setImage(app.images.icon(icon), Row.IMAGE_TYPE_ICON)
            .build()
}

