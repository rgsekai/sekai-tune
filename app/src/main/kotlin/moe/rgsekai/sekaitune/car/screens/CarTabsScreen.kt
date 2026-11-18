package moe.rgsekai.sekaitune.car.screens

import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.car.CarApp

// the car's first screen: home, remote control, recent and library
@UnstableApi
class CarTabsScreen(private val app: CarApp) : Screen(app.carContext) {
    private val home = CarHomeTab(app)
    private val remote = CarRemoteTab(app) { if (active is CarRemoteTab) invalidate() }
    private val tabs: List<CarTabPage> = listOf(home, remote, CarRecentTab(app), CarLibraryTab(app))
    private var active: CarTabPage = home

    private var covered = false

    private val callback = object : TabTemplate.TabCallback {
        override fun onTabSelected(tabContentId: String) = select(tabContentId)
    }

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (covered) tabs.forEach { it.catchUp() }
                    covered = false
                    tabs.forEach { tab -> tab.reload { if (tab === active) invalidate() } }
                    active.onSelected()
                    invalidate()
                }

                override fun onStop(owner: LifecycleOwner) {
                    covered = screenManager.top !== this@CarTabsScreen
                    active.onLeft()
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        if (!home.isReady) return TabTemplate.Builder(callback).setLoading(true).build()
        val template = TabTemplate.Builder(callback).setHeaderAction(Action.APP_ICON)
        tabs.forEach { tab ->
            template.addTab(
                Tab.Builder()
                    .setTitle(app.string(tab.title))
                    .setIcon(app.images.icon(tab.icon))
                    .setContentId(tab.id)
                    .build(),
            )
        }
        return template
            .setTabContents(TabContents.Builder(active.template()).build())
            .setActiveTabContentId(active.id)
            .build()
    }

    private fun select(id: String) {
        val next = tabs.firstOrNull { it.id == id } ?: return
        if (next === active) return
        active.onLeft()
        active = next
        next.catchUp()
        next.onSelected()
        next.reload { invalidate() }
        invalidate()
    }
}

