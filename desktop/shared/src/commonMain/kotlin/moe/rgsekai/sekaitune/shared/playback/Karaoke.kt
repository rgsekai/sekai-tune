package moe.rgsekai.sekaitune.shared.playback

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The karaoke switch, shared by the lyrics page that flips it and the engine
 * whose [KaraokeProcessor]s act on it. Not saved: coming back to an app that
 * plays every song without its singer, a session after asking for one, would
 * read as broken rather than remembered.
 */
object Karaoke {
    private val _enabled = MutableStateFlow(false)
    private val _unavailable = MutableStateFlow(false)
    private val _level = MutableStateFlow(1f)
    private val _neural = MutableStateFlow(false)

    /**
     * The neural vocal is what is coming out. While karaoke is on and this is
     * false, the vocal is being prepared — the mic shows a ring — and left whole.
     */
    val neural: StateFlow<Boolean> = _neural

    /** Hands a processor's [KaraokeProcessor.isUsingStems] to [neural]. From the audio thread. */
    fun reportNeural(value: Boolean) {
        _neural.value = value && _enabled.value
    }

    @Volatile
    private var track: String? = null

    /**
     * The song playing is now [id]. Karaoke belongs to one song: a different one
     * switches it off and lets the last one's separated audio go.
     */
    fun trackChanged(id: String?) {
        if (id == track) return
        track = id
        if (_enabled.value) setEnabled(false)
        moe.rgsekai.sekaitune.shared.playback.karaoke.KaraokeStems.retainOnly(id)
    }
    private val _warm = MutableStateFlow(false)
    private val showing = AtomicInt(0)

    /**
     * Get the neural vocal ready even while karaoke is off — the lyrics are on
     * screen, so the mic is a tap away — and switching on is then a crossfade
     * rather than seconds of separating from cold. Only once the model is here:
     * looking at lyrics never downloads it.
     */
    val warm: StateFlow<Boolean> = _warm

    /** The lyrics have come on screen; the returned call takes them off again. */
    fun lyricsShown(): () -> Unit {
        if (showing.incrementAndFetch() == 1) {
            _warm.value = true
            moe.rgsekai.sekaitune.shared.playback.karaoke.KaraokeStems.wake()
        }
        val released = AtomicBoolean(false)
        return {
            if (released.compareAndSet(false, true) && showing.decrementAndFetch() == 0) _warm.value = false
        }
    }

    val enabled: StateFlow<Boolean> = _enabled

    /** How much of the vocal is taken out while on, 0..1 — the mic's slider. */
    val level: StateFlow<Float> = _level

    fun setLevel(value: Float) {
        _level.value = value.coerceIn(0f, 1f)
    }

    /** The track playing can't have its vocal taken out (mono); see [KaraokeProcessor]. */
    val unavailable: StateFlow<Boolean> = _unavailable

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        if (!value) {
            _unavailable.value = false
            _neural.value = false
        }
        moe.rgsekai.sekaitune.shared.playback.karaoke.KaraokeStems.wake()
    }

    /** Hands a processor's verdict to [unavailable]. From the audio thread. */
    fun reportUnavailable(value: Boolean) {
        _unavailable.value = value && _enabled.value
    }
}
