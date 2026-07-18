package io.github.wizard302.cardamom.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.cardamom.data.media.Album
import io.github.wizard302.cardamom.data.media.Artist
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: LibraryRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    private val artistId: Long = checkNotNull(savedStateHandle["artistId"])

    val artist: StateFlow<Artist?> = repository.artists
        .map { list -> list.firstOrNull { it.id == artistId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val albums: StateFlow<List<Album>> = repository.albums
        .map { list -> list.filter { it.artistId == artistId }.sortedBy { it.year } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tracks: StateFlow<List<Track>> = repository.tracks
        .map { list ->
            list.filter { it.artistId == artistId }
                .sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(startIndex: Int) = playerConnection.playQueue(tracks.value, startIndex)
}
