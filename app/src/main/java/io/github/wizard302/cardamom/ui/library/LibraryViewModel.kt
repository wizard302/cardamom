package io.github.wizard302.cardamom.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.wizard302.cardamom.data.media.Album
import io.github.wizard302.cardamom.data.media.Artist
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.data.settings.AlbumSort
import io.github.wizard302.cardamom.data.settings.ArtistSort
import io.github.wizard302.cardamom.data.settings.SettingsRepository
import io.github.wizard302.cardamom.data.settings.TrackSort
import io.github.wizard302.cardamom.playback.PlayerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val settings: SettingsRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    /** Library-wide search query; empty means "show everything". */
    val query: StateFlow<String> = _query.asStateFlow()

    val trackSort: StateFlow<TrackSort> =
        settings.trackSort.stateIn(viewModelScope, SharingStarted.Eagerly, TrackSort.TITLE)
    val albumSort: StateFlow<AlbumSort> =
        settings.albumSort.stateIn(viewModelScope, SharingStarted.Eagerly, AlbumSort.TITLE)
    val artistSort: StateFlow<ArtistSort> =
        settings.artistSort.stateIn(viewModelScope, SharingStarted.Eagerly, ArtistSort.NAME)

    val tracks: StateFlow<List<Track>> =
        combine(repository.tracks, _query, trackSort) { list, query, sort ->
            list.filter { it.matches(query) }.sortedWith(sort.comparator())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> =
        combine(repository.albums, _query, albumSort) { list, query, sort ->
            list.filter { it.matches(query) }.sortedWith(sort.comparator())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> =
        combine(repository.artists, _query, artistSort) { list, query, sort ->
            list.filter { it.matches(query) }.sortedWith(sort.comparator())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Last-opened library tab, restored on launch; -1 until the store is read. */
    val libraryTab: StateFlow<Int> =
        settings.libraryTab.stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    fun setLibraryTab(index: Int) = viewModelScope.launch { settings.setLibraryTab(index) }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setTrackSort(sort: TrackSort) = viewModelScope.launch { settings.setTrackSort(sort) }
    fun setAlbumSort(sort: AlbumSort) = viewModelScope.launch { settings.setAlbumSort(sort) }
    fun setArtistSort(sort: ArtistSort) = viewModelScope.launch { settings.setArtistSort(sort) }

    fun onPermissionGranted() = repository.refresh()

    /** Looks a track up in the unfiltered library, e.g. for Now Playing actions. */
    fun trackById(id: Long): Track? = repository.tracks.value.firstOrNull { it.id == id }

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

    // Collection playback always uses the full album/artist, not the filtered view.
    private fun albumTracks(albumId: Long): List<Track> =
        repository.tracks.value
            .filter { it.albumId == albumId }
            .sortedBy { it.trackNumber }

    private fun artistTracks(artistId: Long): List<Track> =
        repository.tracks.value
            .filter { it.artistId == artistId }
            .sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }))
}

private fun Track.matches(query: String): Boolean =
    query.isBlank() ||
        title.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        album.contains(query, ignoreCase = true)

private fun Album.matches(query: String): Boolean =
    query.isBlank() ||
        title.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true)

private fun Artist.matches(query: String): Boolean =
    query.isBlank() || name.contains(query, ignoreCase = true)

private fun TrackSort.comparator(): Comparator<Track> = when (this) {
    TrackSort.TITLE -> compareBy { it.title.lowercase() }
    TrackSort.ARTIST -> compareBy({ it.artist.lowercase() }, { it.title.lowercase() })
    TrackSort.ALBUM -> compareBy({ it.album.lowercase() }, { it.trackNumber })
    // Newest first — "recently added" is only useful in that direction.
    TrackSort.DATE_ADDED -> compareByDescending { it.dateAdded }
    TrackSort.DURATION -> compareBy { it.durationMs }
}

private fun AlbumSort.comparator(): Comparator<Album> = when (this) {
    AlbumSort.TITLE -> compareBy { it.title.lowercase() }
    AlbumSort.ARTIST -> compareBy({ it.artist.lowercase() }, { it.title.lowercase() })
    AlbumSort.YEAR -> compareByDescending { it.year }
    AlbumSort.DATE_ADDED -> compareByDescending { it.dateAdded }
}

private fun ArtistSort.comparator(): Comparator<Artist> = when (this) {
    ArtistSort.NAME -> compareBy { it.name.lowercase() }
    ArtistSort.TRACK_COUNT -> compareByDescending { it.trackCount }
}
