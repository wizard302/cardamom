package io.github.wizard302.cardamom.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import io.github.wizard302.cardamom.data.media.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Bounds of the playback-speed slider, shared by the UI and the persisted value. */
const val MIN_SPEED = 0.5f
const val MAX_SPEED = 2.0f

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

    /**
     * Current queue in the order it will actually play — the shuffled order
     * when shuffle is on. Each slot carries its timeline index, which is what
     * seeking and removal take.
     */
    private val _queue = MutableStateFlow<List<QueueSlot>>(emptyList())
    val queue: StateFlow<List<QueueSlot>> = _queue.asStateFlow()

    /** Timeline index of the item currently playing, -1 when the queue is empty. */
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** Playback speed; 1.0 is normal. */
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

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
            updateQueue()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onPlaybackParametersChanged(
            playbackParameters: androidx.media3.common.PlaybackParameters,
        ) {
            _speed.value = playbackParameters.speed
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

    /** True while a connection attempt is in flight, so it isn't started twice. */
    private var connecting = false

    /**
     * Drops a controller whose session went away (the service crashed or was
     * killed), so the next call reconnects instead of talking to a dead binder.
     */
    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (this@PlayerConnection.controller !== controller) return
            controller.removeListener(listener)
            this@PlayerConnection.controller = null
            _connected.value = false
            _isPlaying.value = false
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        future.addListener(
            {
                connecting = false
                val c = runCatching { future.get() }.getOrElse { e ->
                    // Left disconnected; the next player action retries.
                    Log.w(TAG, "Could not connect to the playback service", e)
                    return@addListener
                }
                controller = c
                c.addListener(listener)
                // Sync initial state.
                _currentMetadata.value = c.mediaMetadata.takeIf { c.mediaItemCount > 0 }
                _isPlaying.value = c.isPlaying
                _durationMs.value = c.duration.coerceAtLeast(0L)
                _shuffleEnabled.value = c.shuffleModeEnabled
                _repeatMode.value = c.repeatMode
                _speed.value = c.playbackParameters.speed
                updateQueue()
                updatePosition()
                _connected.value = true
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun updateQueue() {
        controller?.let { c ->
            // The session ships the shuffle order inside the timeline, so walking
            // it with the shuffle flag yields the real play order.
            val timeline = c.currentTimeline
            val shuffle = c.shuffleModeEnabled
            val slots = ArrayList<QueueSlot>(timeline.windowCount)
            var i = timeline.getFirstWindowIndex(shuffle)
            while (i != C.INDEX_UNSET && slots.size < timeline.windowCount) {
                slots += QueueSlot(index = i, item = c.getMediaItemAt(i))
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
            }
            _queue.value = slots
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
        val c = controller
        if (c == null) {
            // Lost the session earlier; reconnect so the next tap works.
            connect()
            return
        }
        c.action()
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) = withController {
        setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0L)
        prepare()
        play()
    }

    /** Inserts [tracks] right after the current item; starts playback if idle. */
    fun playNext(tracks: List<Track>) = enqueue(tracks, next = true)

    /** Appends [tracks] to the end of the queue; starts playback if idle. */
    fun addToQueue(tracks: List<Track>) = enqueue(tracks, next = false)

    /**
     * Queue insertions are carried out by the service (see [COMMAND_ENQUEUE]):
     * a controller-side insertion reaches the player asynchronously, so the
     * shuffle order could not be fixed up afterwards without racing it.
     */
    @OptIn(UnstableApi::class)
    private fun enqueue(tracks: List<Track>, next: Boolean) = withController {
        if (tracks.isEmpty()) return@withController
        val items = tracks.map { it.toMediaItem() }
        val command = SessionCommand(COMMAND_ENQUEUE, Bundle.EMPTY)
        if (!isSessionCommandAvailable(command)) {
            // No session to do it for us; insert directly and accept that
            // shuffle will scatter the items.
            val insertAt = when {
                mediaItemCount == 0 -> 0
                next -> currentMediaItemIndex + 1
                else -> mediaItemCount
            }
            addMediaItems(insertAt, items)
            if (playbackState == Player.STATE_IDLE) prepare()
            if (mediaItemCount == items.size) play()
            return@withController
        }
        sendCustomCommand(
            command,
            Bundle().apply {
                putParcelableArrayList(
                    EXTRA_ITEMS,
                    items.mapTo(ArrayList()) { it.toBundleIncludeLocalConfiguration() },
                )
                putBoolean(EXTRA_PLAY_NEXT, next)
            },
        )
    }

    fun seekToQueueItem(index: Int) = withController {
        seekTo(index, 0L)
        if (playbackState == Player.STATE_IDLE) prepare()
        play()
    }

    /**
     * Removes the item at timeline [index]. Done by the service when possible:
     * the controller's own removal shows an unshuffled placeholder timeline
     * until the session answers, which makes a shuffled queue list jump.
     */
    @OptIn(UnstableApi::class)
    fun removeQueueItem(index: Int) = withController {
        val command = SessionCommand(COMMAND_QUEUE_REMOVE, Bundle.EMPTY)
        if (isSessionCommandAvailable(command)) {
            sendCustomCommand(command, Bundle().apply { putInt(EXTRA_INDEX, index) })
        } else {
            removeMediaItem(index)
        }
    }

    /**
     * Puts back a queue item removed with [removeQueueItem]: [index] is its old
     * timeline index, [playPosition] its old position in the play order.
     */
    @OptIn(UnstableApi::class)
    fun restoreQueueItem(item: MediaItem, index: Int, playPosition: Int) = withController {
        val command = SessionCommand(COMMAND_QUEUE_RESTORE, Bundle.EMPTY)
        if (isSessionCommandAvailable(command)) {
            sendCustomCommand(
                command,
                Bundle().apply {
                    putBundle(EXTRA_ITEM, item.toBundleIncludeLocalConfiguration())
                    putInt(EXTRA_INDEX, index)
                    putInt(EXTRA_TO, playPosition)
                },
            )
        } else {
            addMediaItem(index.coerceIn(0, mediaItemCount), item)
        }
    }

    /**
     * Moves a queue item between two positions of the play order (see [queue]).
     * With shuffle on, the service rearranges the shuffle order rather than the
     * timeline, which would have no audible effect.
     */
    @OptIn(UnstableApi::class)
    fun moveQueueItem(from: Int, to: Int) = withController {
        val command = SessionCommand(COMMAND_QUEUE_MOVE, Bundle.EMPTY)
        if (isSessionCommandAvailable(command)) {
            sendCustomCommand(
                command,
                Bundle().apply {
                    putInt(EXTRA_FROM, from)
                    putInt(EXTRA_TO, to)
                },
            )
        } else if (!shuffleModeEnabled) {
            moveMediaItem(from, to)
        }
    }

    fun togglePlayPause() = withController { if (isPlaying) pause() else play() }

    fun next() = withController { seekToNextMediaItem() }

    fun previous() = withController {
        // Standard behaviour: restart the track if we're past 3 s, else go back.
        if (currentPosition > 3_000) seekTo(0) else seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = withController { seekTo(positionMs) }

    fun toggleShuffle() = withController { shuffleModeEnabled = !shuffleModeEnabled }

    /** Pitch is left untouched: only the tempo changes. */
    fun setSpeed(speed: Float) = withController {
        setPlaybackSpeed(speed.coerceIn(MIN_SPEED, MAX_SPEED))
    }

    fun cycleRepeatMode() = withController {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}

/** One queue entry: [index] is its position in the player timeline. */
data class QueueSlot(val index: Int, val item: MediaItem)

private const val TAG = "Cardamom"
