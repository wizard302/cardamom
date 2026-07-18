package io.github.wizard302.cardamom.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.wizard302.cardamom.data.media.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the MediaController connection to PlaybackService and mirrors the
 * player state into StateFlows the Compose UI can collect. Main-thread only
 * (MediaController requirement); all mutating calls go through [withController].
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var controller: MediaController? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _currentMetadata = MutableStateFlow<MediaMetadata?>(null)
    val currentMetadata: StateFlow<MediaMetadata?> = _currentMetadata.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    /** 1-based index and queue size for the "3/108" indicator. */
    private val _queuePosition = MutableStateFlow(0 to 0)
    val queuePosition: StateFlow<Pair<Int, Int>> = _queuePosition.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _currentMetadata.value = mediaMetadata
            updateQueuePosition()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            controller?.let { _durationMs.value = it.duration.coerceAtLeast(0L) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleEnabled.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            updateQueuePosition()
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val c = future.get()
                controller = c
                c.addListener(listener)
                // Sync initial state.
                _currentMetadata.value = c.mediaMetadata.takeIf { c.mediaItemCount > 0 }
                _isPlaying.value = c.isPlaying
                _durationMs.value = c.duration.coerceAtLeast(0L)
                _shuffleEnabled.value = c.shuffleModeEnabled
                _repeatMode.value = c.repeatMode
                updateQueuePosition()
                _connected.value = true
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun updateQueuePosition() {
        controller?.let {
            _queuePosition.value = (it.currentMediaItemIndex + 1) to it.mediaItemCount
        }
    }

    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    private inline fun withController(action: MediaController.() -> Unit) {
        controller?.action()
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) = withController {
        setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0L)
        prepare()
        play()
    }

    fun togglePlayPause() = withController { if (isPlaying) pause() else play() }

    fun next() = withController { seekToNextMediaItem() }

    fun previous() = withController {
        // Standard behaviour: restart the track if we're past 3 s, else go back.
        if (currentPosition > 3_000) seekTo(0) else seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = withController { seekTo(positionMs) }

    fun toggleShuffle() = withController { shuffleModeEnabled = !shuffleModeEnabled }

    fun cycleRepeatMode() = withController {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(albumArtUri)
                    .build(),
            )
            .build()
}
