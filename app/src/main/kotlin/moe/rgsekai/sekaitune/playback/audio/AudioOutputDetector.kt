/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Detects whether the current active audio route is headphones/external audio gear
 * or the phone's built-in speaker.
 */
object AudioOutputDetector {

    private val EXTERNAL_OUTPUT_TYPES = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_AUX_LINE,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
    )

    /**
     * Checks if headphones, earphones, or external audio devices are currently connected.
     */
    fun isHeadphonesOrExternal(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        return try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { it.type in EXTERNAL_OUTPUT_TYPES }
        } catch (_: Exception) {
            true // Fail open to allow audio processing if device query fails
        }
    }

    /**
     * Registers an [AudioDeviceCallback] to be notified when audio output routes change (e.g., headphones plugged/unplugged).
     */
    fun registerDeviceCallback(
        context: Context,
        onChange: () -> Unit
    ): AudioDeviceCallback? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                onChange()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                onChange()
            }
        }
        return try {
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
            callback
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Unregisters the [AudioDeviceCallback].
     */
    fun unregisterDeviceCallback(context: Context, callback: AudioDeviceCallback?) {
        if (callback == null) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audioManager.unregisterAudioDeviceCallback(callback)
        } catch (_: Exception) {
            // Ignore
        }
    }
}
