package io.github.wizard302.cardamom.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.cardamom.data.playlist.PlaylistRepository
import io.github.wizard302.cardamom.playback.EXTRA_PATH
import io.github.wizard302.cardamom.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val connection: PlayerConnection,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    init {
        connection.connect()
    }

    val metadata = connection.currentMetadata
    val isPlaying = connection.isPlaying
    val durationMs = connection.durationMs
    val shuffleEnabled = connection.shuffleEnabled
    val repeatMode = connection.repeatMode
    val queuePosition = connection.queuePosition
    val queue = connection.queue
    val currentIndex = connection.currentIndex

    /** Whether the currently playing track is in Favorites. */
    val isCurrentFavorite: StateFlow<Boolean> =
        combine(connection.currentItem, playlistRepository.favoriteIds) { item, favIds ->
            val mediaId = item?.mediaId?.toLongOrNull()
            mediaId != null && mediaId in favIds
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Polled playback position; runs only while the UI collects it. */
    val positionMs: StateFlow<Long> = flow {
        while (true) {
            emit(connection.currentPositionMs())
            delay(500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun togglePlayPause() = connection.togglePlayPause()
    fun next() = connection.next()
    fun previous() = connection.previous()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun toggleShuffle() = connection.toggleShuffle()
    fun cycleRepeatMode() = connection.cycleRepeatMode()
    fun seekToQueueItem(index: Int) = connection.seekToQueueItem(index)
    fun removeQueueItem(index: Int) = connection.removeQueueItem(index)
    fun moveQueueItem(from: Int, to: Int) = connection.moveQueueItem(from, to)

    /** Toggles Favorites membership for the currently playing track. */
    fun toggleFavorite() {
        val item = connection.currentItem.value ?: return
        val mediaId = item.mediaId.toLongOrNull() ?: return
        val meta = item.mediaMetadata
        viewModelScope.launch {
            playlistRepository.toggleFavorite(
                mediaId = mediaId,
                path = meta.extras?.getString(EXTRA_PATH).orEmpty(),
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                album = meta.albumTitle?.toString().orEmpty(),
                durationMs = connection.durationMs.value.coerceAtLeast(0L),
            )
        }
    }
}
