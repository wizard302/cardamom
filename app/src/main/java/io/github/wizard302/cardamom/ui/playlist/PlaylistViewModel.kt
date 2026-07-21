package io.github.wizard302.cardamom.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.cardamom.data.db.PlaylistWithCount
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.data.playlist.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Playlists tab and the "Add to playlist" dialog. */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistWithCount>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoritesCount: StateFlow<Int> = repository.favorites
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun createPlaylist(name: String) {
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    fun rename(playlistId: Long, name: String) {
        viewModelScope.launch { repository.renamePlaylist(playlistId, name) }
    }

    fun delete(playlistId: Long) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    fun addToPlaylist(playlistId: Long, tracks: List<Track>) {
        viewModelScope.launch { repository.addTracks(playlistId, tracks) }
    }

    fun createPlaylistWith(name: String, tracks: List<Track>) {
        viewModelScope.launch { repository.createPlaylistWith(name, tracks) }
    }
}
