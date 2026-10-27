/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback.smart

import android.media.audiofx.Equalizer
import timber.log.Timber

/**
 * Manages low-frequency crossover (Bass Swap) between the outgoing and incoming
 * audio streams during an Automix transition.
 *
 * Prevents low-frequency energy doubling (+3dB to +6dB buildup) and acoustic mud.
 */
class BassSwapController {

    private var outgoingEq: Equalizer? = null
    private var incomingEq: Equalizer? = null

    private var outgoingOriginalLowBandLevel: Short = 0
    private var incomingOriginalLowBandLevel: Short = 0

    private var isAttached = false

    fun attach(outgoingSessionId: Int, incomingSessionId: Int) {
        if (outgoingSessionId <= 0 || incomingSessionId <= 0) return

        runCatching {
            val outEq = Equalizer(0, outgoingSessionId).apply { enabled = true }
            val inEq = Equalizer(0, incomingSessionId).apply { enabled = true }

            outgoingOriginalLowBandLevel = outEq.getBandLevel(0)
            incomingOriginalLowBandLevel = inEq.getBandLevel(0)

            outgoingEq = outEq
            incomingEq = inEq
            isAttached = true
            Timber.tag(TAG).d("BassSwapController attached: outSession=$outgoingSessionId, inSession=$incomingSessionId")
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to attach BassSwapController; falling back to flat fade")
            release()
        }
    }

    /**
     * Update bass crossover levels according to transition progress [0f..1f].
     * Low-end crossover swaps at [bassSwapFraction] (typically 0.5f).
     */
    fun updateProgress(progress: Float, bassSwapFraction: Float = 0.5f) {
        if (!isAttached) return
        val outEq = outgoingEq ?: return
        val inEq = incomingEq ?: return

        runCatching {
            val minLevel = outEq.bandLevelRange?.getOrNull(0) ?: -1500
            val maxLevel = outEq.bandLevelRange?.getOrNull(1) ?: 1500
            val neutralLevel: Short = 0

            if (progress < bassSwapFraction) {
                // Outgoing keeps bass, incoming bass is kept down to avoid mud
                val factor = (progress / bassSwapFraction).coerceIn(0f, 1f)
                val outDamp = (neutralLevel + (minLevel / 2 - neutralLevel) * factor).toInt().toShort()
                outEq.setBandLevel(0, outDamp.coerceIn(minLevel, maxLevel))

                // Incoming bass suppressed until crossover point
                inEq.setBandLevel(0, (minLevel * 0.8f).toInt().toShort().coerceIn(minLevel, maxLevel))
            } else {
                // Past midpoint: swap low end! Incoming gets full bass, outgoing bass is cut
                outEq.setBandLevel(0, minLevel)
                val recoverFactor = ((progress - bassSwapFraction) / (1f - bassSwapFraction)).coerceIn(0f, 1f)
                val inLevel = ((minLevel / 3) * (1f - recoverFactor) + neutralLevel * recoverFactor).toInt().toShort()
                inEq.setBandLevel(0, inLevel.coerceIn(minLevel, maxLevel))
            }
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to modulate bass bands")
        }
    }

    fun release() {
        if (!isAttached && outgoingEq == null && incomingEq == null) return
        runCatching {
            outgoingEq?.let { eq ->
                eq.setBandLevel(0, outgoingOriginalLowBandLevel)
                eq.enabled = false
                eq.release()
            }
        }
        runCatching {
            incomingEq?.let { eq ->
                eq.setBandLevel(0, incomingOriginalLowBandLevel)
                eq.enabled = false
                eq.release()
            }
        }
        outgoingEq = null
        incomingEq = null
        isAttached = false
        Timber.tag(TAG).d("BassSwapController released")
    }

    companion object {
        private const val TAG = "BassSwapController"
    }
}
