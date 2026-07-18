package io.github.wizard302.cardamom.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.wizard302.cardamom.MainActivity
import io.github.wizard302.cardamom.data.media.MediaStoreScanner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
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
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var queueStateStore: QueueStateStore

    @Inject lateinit var scanner: MediaStoreScanner

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val persistListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveQueueState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveQueueState()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            saveQueueState()
        }
    }

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
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(persistListener)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        scope.launch { restoreQueueState(player) }
    }

    private fun saveQueueState() {
        val player = mediaSession?.player ?: return
        val ids = List(player.mediaItemCount) { i ->
            player.getMediaItemAt(i).mediaId.toLongOrNull()
        }.filterNotNull()
        val index = player.currentMediaItemIndex
        val position = player.currentPosition.coerceAtLeast(0L)
        scope.launch { queueStateStore.save(ids, index, position) }
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
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
