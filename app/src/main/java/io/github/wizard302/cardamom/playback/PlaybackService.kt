package io.github.wizard302.cardamom.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.os.BundleCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.wizard302.cardamom.MainActivity
import io.github.wizard302.cardamom.data.media.MediaStoreScanner
import io.github.wizard302.cardamom.data.settings.ReplayGainMode
import io.github.wizard302.cardamom.data.settings.SettingsRepository
import io.github.wizard302.cardamom.widget.PlayerWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Single playback service. The player and the queue live here; the UI talks to it
 * through a MediaController (see PlayerConnection). Media3 provides the media
 * notification, MediaButton/Bluetooth handling and lock-screen controls.
 *
 * The queue and position are persisted to DataStore (QueueStateStore) on pause,
 * track change and queue edits, and restored on cold service start.
 */
@OptIn(FlowPreview::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var queueStateStore: QueueStateStore

    @Inject lateinit var scanner: MediaStoreScanner

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var audioEffects: AudioEffectsController

    @Inject lateinit var sleepTimer: SleepTimerController

    @Inject lateinit var replayGain: ReplayGainController

    private var mediaSession: MediaSession? = null
    private var headphoneWatcher: HeadphoneWatcher? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Queue edits (especially a bulk "play all") fire many timeline callbacks in
    // a burst; persisting is debounced so DataStore sees one write per burst.
    private val saveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Drives ReplayGain: the volume is recomputed whenever this changes. */
    private val currentItem = MutableStateFlow<MediaItem?>(null)

    private val persistListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveQueueState()
            refreshWidget()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveQueueState()
            refreshWidget()
            currentItem.value = mediaItem
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            saveQueueState()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            refreshWidget()
        }
    }

    /**
     * Sleep timer hooks. "End of track" fires on the next automatic transition
     * (a repeat-one loop counts); a user-requested pause disarms the timer, so
     * stopping by hand does not leave it silently running.
     */
    private val sleepTimerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!sleepTimer.stopAfterTrack.value) return
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) {
                return
            }
            sleepTimer.cancel()
            mediaSession?.player?.pause()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                sleepTimer.cancel()
            }
        }
    }

    private fun refreshWidget() {
        mediaSession?.player?.let { PlayerWidget.update(this, it) }
    }

    private val enqueueCommand = SessionCommand(COMMAND_ENQUEUE, Bundle.EMPTY)

    private val sessionCallback = object : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(enqueueCommand)
                        .build(),
                )
                .build()

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != COMMAND_ENQUEUE) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            val items = BundleCompat.getParcelableArrayList(args, EXTRA_ITEMS, Bundle::class.java)
                ?.map { MediaItem.fromBundle(it) }
                .orEmpty()
            enqueue(items, next = args.getBoolean(EXTRA_PLAY_NEXT, true))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /**
     * Inserts [items] and fixes the shuffle order in one pass on the player
     * thread, so repeated "play next" taps stack up in the order they were
     * made instead of racing each other.
     */
    private fun enqueue(items: List<MediaItem>, next: Boolean) {
        val player = mediaSession?.player ?: return
        if (items.isEmpty()) return
        val wasEmpty = player.mediaItemCount == 0
        val insertAt = if (next && !wasEmpty) {
            player.currentMediaItemIndex + 1
        } else {
            player.mediaItemCount
        }
        player.addMediaItems(insertAt, items)
        applyShuffleReorder(insertAt, items.size, next)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (wasEmpty) player.play()
    }

    /**
     * Puts freshly inserted items where the user asked for them in the shuffled
     * play order — ExoPlayer would otherwise scatter them at random.
     */
    @OptIn(UnstableApi::class)
    private fun applyShuffleReorder(insertAt: Int, count: Int, next: Boolean) {
        val player = mediaSession?.player as? ExoPlayer ?: return
        if (!player.shuffleModeEnabled || insertAt < 0 || count <= 0) return
        val order = player.shuffleOrder
        if (order.length != player.mediaItemCount) return
        val play = buildList {
            var i = order.firstIndex
            while (i != C.INDEX_UNSET) {
                add(i)
                i = order.getNextIndex(i)
            }
        }
        val reordered = reorderShuffle(
            order = play,
            insertAt = insertAt,
            count = count,
            current = player.currentMediaItemIndex,
            next = next,
        ) ?: return
        player.setShuffleOrder(
            ShuffleOrder.DefaultShuffleOrder(reordered, System.nanoTime()),
        )
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Headphone transitions are handled by HeadphoneWatcher instead, so
            // that both pause-on-disconnect and resume-on-connect stay settable.
            .setHandleAudioBecomingNoisy(false)
            .build()
        player.addListener(persistListener)
        player.addListener(sleepTimerListener)

        // Pin a stable audio session id up front so the equalizer can attach even
        // before playback starts, then hand it to the effects controller.
        val sessionId = (getSystemService(AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
        if (sessionId != AudioManager.ERROR) {
            player.setAudioSessionId(sessionId)
            audioEffects.attach(sessionId)
        }

        headphoneWatcher = HeadphoneWatcher(this, player).also { watcher ->
            watcher.register()
            scope.launch {
                settings.pauseOnDisconnect.collect { watcher.pauseOnDisconnect = it }
            }
            scope.launch {
                settings.resumeOnConnect.collect { watcher.resumeOnConnect = it }
            }
        }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
            .build()

        scope.launch {
            sleepTimer.expired.collect {
                mediaSession?.player?.pause()
            }
        }

        // The speed is stored once globally; the UI writes it, we apply it on
        // start. Only the persisted value is read here — later changes arrive
        // through the MediaController.
        scope.launch { player.setPlaybackSpeed(settings.playbackSpeed.first()) }

        // Volume leveling: recomputed on every track change and whenever the
        // settings change, so switching the mode off restores full volume at once.
        // collectLatest drops an in-flight tag read when the track moves on.
        scope.launch {
            combine(
                currentItem,
                settings.rgMode,
                settings.rgPreampDb,
            ) { item, mode, preamp -> Triple(item, mode, preamp) }
                .collectLatest { (item, mode, preamp) ->
                    val uri = item?.localConfiguration?.uri
                    val gain = if (mode == ReplayGainMode.OFF || uri == null) {
                        null
                    } else {
                        replayGain.gainFor(uri, item.mediaId)
                    }
                    player.volume = replayGainVolume(gain, mode, preamp)
                }
        }

        scope.launch { restoreQueueState(player) }
        scope.launch {
            saveRequests.debounce(1_000).collect { persistQueueState() }
        }
    }

    private fun saveQueueState() {
        saveRequests.tryEmit(Unit)
    }

    /** Reads the player on Main (collector context) and persists to DataStore. */
    private suspend fun persistQueueState() {
        val player = mediaSession?.player ?: return
        val ids = List(player.mediaItemCount) { i ->
            player.getMediaItemAt(i).mediaId.toLongOrNull()
        }.filterNotNull()
        val index = player.currentMediaItemIndex
        val position = player.currentPosition.coerceAtLeast(0L)
        queueStateStore.save(ids, index, position)
    }

    private suspend fun restoreQueueState(player: Player) {
        val saved = queueStateStore.load() ?: return
        // A controller may have set a queue while we were loading.
        if (player.mediaItemCount > 0) return
        val tracksById = scanner.scanTracks().associateBy { it.id }
        val items = saved.trackIds.mapNotNull { tracksById[it]?.toMediaItem() }
        if (items.isEmpty()) return
        // Account for tracks that disappeared before the saved index.
        val survivingBefore = saved.trackIds
            .take(saved.index.coerceAtMost(saved.trackIds.size))
            .count { it in tracksById }
        player.setMediaItems(
            items,
            survivingBefore.coerceIn(0, items.size - 1),
            saved.positionMs,
        )
        player.playWhenReady = false
        player.prepare()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.player?.let { player ->
            val ids = List(player.mediaItemCount) { i ->
                player.getMediaItemAt(i).mediaId.toLongOrNull()
            }.filterNotNull()
            if (ids.isNotEmpty()) {
                // Last chance to persist; a small synchronous write is acceptable here.
                runBlocking {
                    queueStateStore.save(
                        ids,
                        player.currentMediaItemIndex,
                        player.currentPosition.coerceAtLeast(0L),
                    )
                }
            }
        }
        audioEffects.release()
        headphoneWatcher?.unregister()
        headphoneWatcher = null
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
