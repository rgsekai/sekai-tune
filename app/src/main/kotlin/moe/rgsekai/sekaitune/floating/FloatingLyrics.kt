package moe.rgsekai.sekaitune.floating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import moe.rgsekai.sekaitune.MainActivity
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.car.CarLyricsSource
import moe.rgsekai.sekaitune.constants.FloatingLyricsKey
import moe.rgsekai.sekaitune.constants.FloatingLyricsXKey
import moe.rgsekai.sekaitune.constants.FloatingLyricsYKey
import moe.rgsekai.sekaitune.constants.LyricsRomanizeChineseKey
import moe.rgsekai.sekaitune.constants.LyricsRomanizeHindiKey
import moe.rgsekai.sekaitune.constants.LyricsRomanizeJapaneseKey
import moe.rgsekai.sekaitune.constants.LyricsRomanizeKoreanKey
import moe.rgsekai.sekaitune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.LyricsEntity
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.lyrics.LyricsRomanizationPreferences
import moe.rgsekai.sekaitune.lyrics.LyricsUtils
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.ui.player.video.isPipActive
import moe.rgsekai.sekaitune.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingLyrics(
    private val context: Context,
    private val database: MusicDatabase,
    private val source: CarLyricsSource,
    private val song: StateFlow<MediaMetadata?>,
    private val player: StateFlow<Player?>,
    private val scope: CoroutineScope,
) {
    private val appVisible = MutableStateFlow(true)
    private val dismissed = MutableStateFlow(false)
    private val screenOn = MutableStateFlow(true)
    private val state = PillState { player.value }
    private var job: Job? = null
    private var window: PillWindow? = null
    private var follow: Job? = null

    private val visibility = LifecycleEventObserver { _, _ ->
        val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        appVisible.value = visible
        if (visible) dismissed.value = false
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> screenOn.value = false
                Intent.ACTION_SCREEN_ON -> screenOn.value = true
            }
        }
    }

    private val host = object : PillHost {
        override fun reserveFrame(shape: PillShape, slots: Int) {
            window?.reserveFrame(shape, slots)
        }

        override fun fitFrame(shape: PillShape, slots: Int) {
            window?.fitFrame(shape, slots)
        }

        override fun openApp() {
            state.expanded = false
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            runCatching { context.startActivity(open) }
        }

        override fun togglePlay() {
            val current = player.value ?: return
            if (current.isPlaying) current.pause() else current.play()
        }

        override fun seekTo(positionMs: Long) {
            player.value?.seekTo(positionMs)
        }
    }

    private val windowListener = object : PillWindowListener {
        override fun onDocked(end: Boolean) {
            state.dockEnd = end
        }

        override fun onDragStarted() {
            state.expanded = false
            state.tucked = true
            state.lifted = true
        }

        override fun onSettling() {
            state.tucked = false
            state.lifted = false
        }

        override fun onMoved(x: Int, y: Int) {
            scope.launch {
                context.dataStore.edit {
                    it[FloatingLyricsXKey] = x
                    it[FloatingLyricsYKey] = y
                }
            }
        }

        override fun onDropped(drop: PillDrop) {
            dismissed.value = true
            if (drop != PillDrop.NEVER) return
            scope.launch { context.dataStore.edit { it[FloatingLyricsKey] = false } }
            Toast.makeText(context, R.string.floating_lyrics_turned_off, Toast.LENGTH_LONG).show()
        }

        override fun onTouched() {
            if (state.expanded) state.touchTick++
        }

        override fun onTouchedOutside() {
            state.expanded = false
        }

        override fun onCoverTapped() {
            state.touchTick++
            state.expanded = !state.expanded
        }

        override fun onCoverLongPressed() {
            host.openApp()
        }
    }

    fun start() {
        if (job != null) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        screenOn.value = pm?.isInteractive ?: true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(screenReceiver, filter)

        ProcessLifecycleOwner.get().lifecycle.addObserver(visibility)
        job = scope.launch {
            val wanted = context.dataStore.data.map { it[FloatingLyricsKey] ?: false }.distinctUntilChanged()
            val hasSong = song.map { it != null }.distinctUntilChanged()
            combine(
                wanted,
                appVisible,
                dismissed,
                hasSong,
                isPipActive,
                screenOn,
            ) { values: Array<Boolean> ->
                val on = values[0]
                val visible = values[1]
                val gone = values[2]
                val playing = values[3]
                val pip = values[4]
                val screen = values[5]
                on && !visible && !gone && playing && !pip && screen
            }
                .distinctUntilChanged()
                .collect { show ->
                    if (show && Settings.canDrawOverlays(context)) open() else close()
                }
        }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(screenReceiver) }
        ProcessLifecycleOwner.get().lifecycle.removeObserver(visibility)
        job?.cancel()
        job = null
        close()
    }

    private suspend fun open() {
        if (window != null) return
        val prefs = context.dataStore.data.first()
        val pill = PillWindow(context, prefs[FloatingLyricsXKey], prefs[FloatingLyricsYKey], windowListener)
        if (!pill.show { FloatingPill(state, pill.metrics, host) }) return
        window = pill
        follow = scope.launch { followSong() }
    }

    private fun close() {
        follow?.cancel()
        follow = null
        window?.remove()
        window = null
        state.expanded = false
        state.tucked = false
        state.lifted = false
    }

    private suspend fun followSong() = coroutineScope {
        launch { followPlayer() }
        song.distinctUntilChangedBy { it?.id }.collectLatest { metadata ->
            state.song = metadata
            state.script = null
            if (metadata != null) followLyrics(metadata)
        }
    }

    private suspend fun followLyrics(metadata: MediaMetadata) = coroutineScope {
        val artist = metadata.artists.joinToString { it.name }
        val appRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        launch { state.accent = coverAccent(context, metadata.thumbnailUrl) ?: state.accent }
        val offsetFlow = database.song(metadata.id).map { it?.song?.lyricsOffset ?: 0 }
        combine(
            database.lyrics(metadata.id),
            offsetFlow,
            context.dataStore.data.map { prefs ->
                LyricsRomanizationPreferences(
                    romanizeJapanese = prefs[LyricsRomanizeJapaneseKey] ?: true,
                    romanizeKorean = prefs[LyricsRomanizeKoreanKey] ?: true,
                    romanizeChinese = prefs[LyricsRomanizeChineseKey] ?: true,
                    romanizeHindi = prefs[LyricsRomanizeHindiKey] ?: true,
                    romanizeOther = prefs[LyricsRomanizeOtherLanguagesKey] ?: true,
                )
            }.distinctUntilChanged(),
        ) { saved, shift, prefs ->
            Triple(saved, shift, prefs)
        }
            .distinctUntilChanged()
            .collectLatest { (saved, shift, readingPrefs) ->
                state.offsetMs = shift
                val raw = saved?.lyrics
                if (raw == null) {
                    val fetched = source.fetch(metadata)
                    if (fetched != null && fetched != LyricsEntity.LYRICS_NOT_FOUND) {
                        showLyrics(fetched, metadata.title, artist, appRtl, readingPrefs, metadata.duration)
                    }
                    return@collectLatest
                }
                showLyrics(raw, metadata.title, artist, appRtl, readingPrefs, metadata.duration)
            }
    }

    private suspend fun showLyrics(
        raw: String,
        title: String,
        artist: String,
        appRtl: Boolean,
        readingPrefs: LyricsRomanizationPreferences,
        durationSec: Int = 0,
    ) {
        val lines = withContext(Dispatchers.Default) { PillScript.lines(raw, durationSec) }
        val plain = withContext(Dispatchers.Default) { PillScript.build(lines, title, artist, appRtl) }
        if (state.script == null || !plain.sung) state.script = plain
        if (!plain.sung) return

        val readings = buildReadings(lines, readingPrefs)
        if (readings != null) {
            val withReadings = withContext(Dispatchers.Default) {
                PillScript.build(lines, title, artist, appRtl, readings)
            }
            state.script = withReadings
        }
    }

    private suspend fun buildReadings(
        lines: List<LyricsEntry>,
        readingPrefs: LyricsRomanizationPreferences,
    ): PillReadings? = withContext(Dispatchers.Default) {
        val romanizedList = lines.map { entry ->
            val romanizedText = LyricsUtils.providedRomanizedTextForEntry(entry, readingPrefs)
                ?: if (LyricsUtils.shouldRomanizeLyricsLine(entry.text, readingPrefs)) {
                    runCatching {
                        when {
                            LyricsUtils.isJapanese(entry.text) && readingPrefs.romanizeJapanese ->
                                LyricsUtils.romanizeJapanese(entry.text)
                            LyricsUtils.isKorean(entry.text) && readingPrefs.romanizeKorean ->
                                LyricsUtils.romanizeKorean(entry.text)
                            else -> null
                        }
                    }.getOrNull()
                } else null
            romanizedText?.takeIf { it.isNotBlank() }?.let { LineReading(it) }
        }

        val translationList = lines.map { entry ->
            LyricsUtils.providedTranslationTextForEntry(entry)
        }

        val hasRomanized = romanizedList.any { it != null }
        val hasTranslations = translationList.any { !it.isNullOrBlank() }

        if (hasRomanized || hasTranslations) {
            PillReadings(
                romanized = romanizedList,
                translations = translationList,
                romanizedAsMain = false,
            )
        } else {
            null
        }
    }

    private suspend fun followPlayer() {
        player.collectLatest { current ->
            if (current == null) return@collectLatest
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    state.playing = isPlaying
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    state.seekTick++
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    state.seekTick++
                }
            }
            state.playing = current.isPlaying
            state.seekTick++
            current.addListener(listener)
            try {
                awaitCancellation()
            } finally {
                current.removeListener(listener)
            }
        }
    }
}

