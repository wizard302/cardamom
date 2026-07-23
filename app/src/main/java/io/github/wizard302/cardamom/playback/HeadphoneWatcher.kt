package io.github.wizard302.cardamom.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player

/**
 * Handles headphone transitions ourselves instead of ExoPlayer's
 * `setHandleAudioBecomingNoisy`, because both edges are user-configurable:
 * pausing on disconnect can be turned off, and resuming on reconnect must only
 * happen when *we* were the ones who paused.
 *
 * Disconnects arrive as ACTION_AUDIO_BECOMING_NOISY (covers wired and
 * Bluetooth). Connects are observed through [AudioDeviceCallback], which —
 * unlike ACTION_HEADSET_PLUG — also fires for Bluetooth and USB audio.
 */
class HeadphoneWatcher(
    private val context: Context,
    private val player: Player,
) {
    var pauseOnDisconnect: Boolean = true
    var resumeOnConnect: Boolean = false

    /** True only between an auto-pause and the next plug-in or manual play. */
    private var pausedByDisconnect = false

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY &&
                pauseOnDisconnect && player.isPlaying
            ) {
                player.pause()
                pausedByDisconnect = true
            }
        }
    }

    // Fires with the current devices on registration too — harmless, because
    // pausedByDisconnect is still false at that point.
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val headphonesConnected = addedDevices.any { it.isSink && it.type in RESUME_DEVICE_TYPES }
            if (headphonesConnected && resumeOnConnect && pausedByDisconnect) {
                pausedByDisconnect = false
                player.play()
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Any playback that starts another way makes the pending resume stale.
            if (isPlaying) pausedByDisconnect = false
        }
    }

    fun register() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        player.addListener(playerListener)
    }

    fun unregister() {
        player.removeListener(playerListener)
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        context.unregisterReceiver(receiver)
    }

    private companion object {
        val RESUME_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}
