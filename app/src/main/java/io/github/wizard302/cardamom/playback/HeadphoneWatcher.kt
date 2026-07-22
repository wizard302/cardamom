package io.github.wizard302.cardamom.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.media3.common.Player

/**
 * Handles headphone transitions ourselves instead of ExoPlayer's
 * `setHandleAudioBecomingNoisy`, because both edges are user-configurable:
 * pausing on disconnect can be turned off, and resuming on reconnect must only
 * happen when *we* were the ones who paused.
 */
class HeadphoneWatcher(
    private val context: Context,
    private val player: Player,
) {
    var pauseOnDisconnect: Boolean = true
    var resumeOnConnect: Boolean = false

    /** True only between an auto-pause and the next plug-in or manual play. */
    private var pausedByDisconnect = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    if (pauseOnDisconnect && player.isPlaying) {
                        player.pause()
                        pausedByDisconnect = true
                    }
                }
                // Sticky broadcast: the current state arrives on registration too,
                // which is harmless because pausedByDisconnect is false by then.
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val pluggedIn = intent.getIntExtra("state", 0) == 1
                    if (pluggedIn && resumeOnConnect && pausedByDisconnect) {
                        pausedByDisconnect = false
                        player.play()
                    }
                }
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
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        player.addListener(playerListener)
    }

    fun unregister() {
        player.removeListener(playerListener)
        context.unregisterReceiver(receiver)
    }
}
