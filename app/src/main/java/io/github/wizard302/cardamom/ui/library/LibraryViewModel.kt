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

    fun playNext(tracks: List<Track>) = playerConnection.playNext(tracks)

    fun addToQueue(tracks: List<Track>) = playerConnection.addToQueue(tracks)

    fun playAlbum(albumId: Long) = play(albumTracks(albumId), 0)
    fun playNextAlbum(albumId: Long) = playNext(albumTracks(albumId))
    fun addAlbumToQueue(albumId: Long) = addToQueue(albumTracks(albumId))

    fun playArtist(artistId: Long) = play(artistTracks(artistId), 0)
    fun playNextArtist(artistId: Long) = playNext(artistTracks(artistId))
    fun addArtistToQueue(artistId: Long) = addToQueue(artistTracks(artistId))

    private fun albumTracks(albumId: Long): List<Track> =
        repository.tracks.value
            .filter { it.albumId == albumId }
            .sortedBy { it.trackNumber }

    private fun artistTracks(artistId: Long): List<Track> =
        repository.tracks.value
            .filter { it.artistId == artistId }
            .sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }))
}
