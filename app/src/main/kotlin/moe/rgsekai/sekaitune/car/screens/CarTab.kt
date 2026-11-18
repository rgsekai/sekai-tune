package moe.rgsekai.sekaitune.car.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.car.app.model.Template
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.car.CarApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

// one tab of the car's home screen as the tab bar sees it
internal interface CarTabPage {
    val id: String

    @get:StringRes val title: Int

    @get:DrawableRes val icon: Int

    val isReady: Boolean

    fun template(): Template

    fun reload(onFirstShown: () -> Unit)

    fun catchUp()

    fun onSelected() = Unit

    fun onLeft() = Unit
}

// a tab that shows loaded data
@UnstableApi
internal abstract class CarDataTab<T : Any>(
    protected val app: CarApp,
    override val id: String,
    @StringRes override val title: Int,
    @DrawableRes override val icon: Int,
) : CarTabPage {
    private var latest: T? = null
    private var shown: T? = null
    private var loading: Job? = null

    protected abstract suspend fun fetch(): T

    protected abstract fun empty(): T

    protected abstract fun build(data: T): Template

    protected abstract fun loadingTemplate(): Template

    override fun template(): Template = shown?.let(::build) ?: loadingTemplate()

    override val isReady: Boolean get() = shown != null

    override fun reload(onFirstShown: () -> Unit) {
        if (loading?.isActive == true) return
        loading = app.scope.launch {
            val fresh = try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("Car").w(e, "couldn't load the $id tab")
                latest ?: empty()
            }
            latest = fresh
            if (shown == null) {
                shown = fresh
                onFirstShown()
            }
        }
    }

    override fun catchUp() {
        latest?.let { shown = it }
    }
}

