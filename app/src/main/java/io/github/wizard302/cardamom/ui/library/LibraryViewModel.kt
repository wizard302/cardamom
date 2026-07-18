package io.github.wizard302.cardamom.ui.library

import androidx.lifecycle.ViewModel
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    val tracks = repository.tracks
    val albums = repository.albums
    val artists = repository.artists

    fun onPermissionGranted() = repository.refresh()

    /** Plays [queue] starting from [startIndex] ("play from here" semantics). */
    fun play(queue: List<Track>, startIndex: Int) =
        playerConnection.playQueue(queue, startIndex)
}
