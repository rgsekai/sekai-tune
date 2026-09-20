/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rgsekai.sekaitune.LocalPlayerConnection
import timber.log.Timber
import kotlin.math.hypot

/**
 * Pure helper to extract normalized bass energy (0.0 to 1.0) from raw Visualizer FFT bytes.
 * Visualizer FFT layout: fft[0]=DC, fft[1]=Nyquist, (fft[2k], fft[2k+1]) = (real, imag) of bin k.
 */
fun extractBassEnergyFromFft(fft: ByteArray, captureSize: Int = 256): Float {
    if (fft.isEmpty()) return 0f
    var bassSum = 0f
    val maxBin = (captureSize / 16).coerceIn(2, 6)
    var count = 0
    for (k in 1..maxBin) {
        val realIdx = 2 * k
        val imagIdx = 2 * k + 1
        if (imagIdx < fft.size) {
            val r = fft[realIdx].toFloat()
            val i = fft[imagIdx].toFloat()
            bassSum += hypot(r, i)
            count++
        }
    }
    if (count == 0) return 0f
    val rawBass = bassSum / count.toFloat()
    // Normalize raw magnitude (typical byte amplitude 4..54) to [0.0, 1.0]
    return ((rawBass - 4f) / 50f).coerceIn(0f, 1f)
}

/**
 * Applies asymmetric exponential moving average for punchy attack and smooth release.
 */
fun smoothBassEnergy(current: Float, target: Float): Float {
    return if (target > current) {
        current * 0.4f + target * 0.6f
    } else {
        current * 0.85f + target * 0.15f
    }
}

/**
 * Extracts smoothed low-frequency bass energy from the active audio session using
 * Android's built-in [Visualizer] FFT capture mode.
 *
 * Designed to be zero-cost when disabled (no Visualizer instance allocated).
 * Gracefully catches all OEM / permission exceptions and falls back to 0.0f (no crash).
 */
@Composable
fun rememberBassEnergy(
    audioSessionId: Int = 0,
    isPlaying: Boolean = true,
    enabled: Boolean = false,
): State<Float> {
    val context = LocalContext.current
    val bassEnergyState = remember { mutableFloatStateOf(0f) }
    val playerConnection = LocalPlayerConnection.current

    LaunchedEffect(audioSessionId, enabled, isPlaying, playerConnection) {
        if (!enabled || !isPlaying) {
            bassEnergyState.floatValue = 0f
            return@LaunchedEffect
        }

        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Timber.tag("CanvasAudioReactor").d("Visualizer init skipped - RECORD_AUDIO permission: DENIED")
            bassEnergyState.floatValue = 0f
            return@LaunchedEffect
        }

        var activeSessionId = if (audioSessionId > 0) audioSessionId else (playerConnection?.localPlayer?.audioSessionId ?: 0)

        // Wait / poll briefly until ExoPlayer assigns a valid audioSessionId > 0
        var attempts = 0
        while (isActive && activeSessionId <= 0 && attempts < 50) {
            delay(100)
            attempts++
            activeSessionId = if (audioSessionId > 0) audioSessionId else (playerConnection?.localPlayer?.audioSessionId ?: 0)
        }

        var visualizer: Visualizer? = null
        var smoothedEnergy = 0f

        try {
            Timber.tag("CanvasAudioReactor").d(
                "Visualizer init attempt on session %d - RECORD_AUDIO permission: ALLOWED",
                activeSessionId,
            )

            val viz = try {
                Visualizer(activeSessionId)
            } catch (t: Throwable) {
                if (activeSessionId != 0) {
                    Timber.tag("CanvasAudioReactor").w(t, "Visualizer failed on session %d; trying session 0 fallback", activeSessionId)
                    Visualizer(0)
                } else {
                    throw t
                }
            }
            val captureRange = Visualizer.getCaptureSizeRange()
            val captureSize = 256.coerceIn(captureRange[0], captureRange[1])
            viz.captureSize = captureSize

            // Bound capture rate to <= 30 Hz for battery efficiency
            val rate = Visualizer.getMaxCaptureRate().coerceAtMost(30_000)

            var lastLogTime = 0L

            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) {
                        // Waveform capture not needed; FFT is used
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (fft == null || fft.isEmpty()) return
                        val normalized = extractBassEnergyFromFft(fft, captureSize)
                        smoothedEnergy = smoothBassEnergy(smoothedEnergy, normalized)
                        bassEnergyState.floatValue = smoothedEnergy

                        val now = System.currentTimeMillis()
                        if (now - lastLogTime >= 500L) {
                            lastLogTime = now
                            Timber.tag("CanvasAudioReactor").d("bassEnergy: %.3f (raw: %.3f)", smoothedEnergy, normalized)
                        }
                    }
                },
                rate,
                false, // waveform
                true, // fft
            )

            viz.enabled = true
            visualizer = viz
            Timber.tag("CanvasAudioReactor").d("Visualizer enabled on session %d at %d mHz", activeSessionId, rate)

            try {
                awaitCancellation()
            } finally {
                try {
                    visualizer?.enabled = false
                    visualizer?.release()
                    Timber.tag("CanvasAudioReactor").d("Visualizer released")
                } catch (t: Throwable) {
                    Timber.tag("CanvasAudioReactor").w(t, "Error releasing visualizer")
                }
                visualizer = null
                bassEnergyState.floatValue = 0f
            }
        } catch (t: Throwable) {
            Timber.tag("CanvasAudioReactor").w(t, "Visualizer could not be initialized on session %d; fallback to time-only animation", activeSessionId)
            bassEnergyState.floatValue = 0f
        }
    }

    return bassEnergyState
}
