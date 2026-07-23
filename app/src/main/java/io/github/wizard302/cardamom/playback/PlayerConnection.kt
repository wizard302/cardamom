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

    private val _currentItem = MutableStateFlow<MediaItem?>(null)
    val currentItem: StateFlow<MediaItem?> = _currentItem.asStateFlow()

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

    /** Current queue as MediaItems, in playback order. */
    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue: StateFlow<List<MediaItem>> = _queue.asStateFlow()

    /** Index of the item currently playing, -1 when the queue is empty. */
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _currentMetadata.value = mediaMetadata
            updatePosition()
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
            // The queue list is rebuilt only here: transitions inside an
            // unchanged timeline just move the index.
            updateQueue()
            updatePosition()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Gapless transitions keep the playback state READY, so
            // onPlaybackStateChanged never fires — refresh the duration here.
            controller?.let { _durationMs.value = it.duration.coerceAtLeast(0L) }
            updatePosition()
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
                updateQueue()
                updatePosition()
                _connected.value = true
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun updateQueue() {
        controller?.let { c ->
            _queue.value = List(c.mediaItemCount) { i -> c.getMediaItemAt(i) }
        }
    }

    private fun updatePosition() {
        controller?.let { c ->
            _queuePosition.value = (c.currentMediaItemIndex + 1) to c.mediaItemCount
            _currentIndex.value = if (c.mediaItemCount > 0) c.currentMediaItemIndex else -1
            _currentItem.value = c.currentMediaItem
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

    /** Inserts [tracks] right after the current item; starts playback if idle. */
    fun playNext(tracks: List<Track>) = withController {
        val insertAt = if (mediaItemCount == 0) 0 else currentMediaItemIndex + 1
        addMediaItems(insertAt, tracks.map { it.toMediaItem() })
        if (playbackState == Player.STATE_IDLE) prepare()
        if (mediaItemCount == tracks.size) play()
    }

    /** Appends [tracks] to the end of the queue; starts playback if idle. */
    fun addToQueue(tracks: List<Track>) = withController {
        addMediaItems(tracks.map { it.toMediaItem() })
        if (playbackState == Player.STATE_IDLE) prepare()
        if (mediaItemCount == tracks.size) play()
    }

    fun seekToQueueItem(index: Int) = withController {
        seekTo(index, 0L)
        if (playbackState == Player.STATE_IDLE) prepare()
        play()
    }

    fun removeQueueItem(index: Int) = withController { removeMediaItem(index) }

    fun moveQueueItem(from: Int, to: Int) = withController { moveMediaItem(from, to) }

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
}
