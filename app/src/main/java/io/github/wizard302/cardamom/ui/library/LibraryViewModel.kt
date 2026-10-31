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
import io.github.wizard302.cardamom.util.foldForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
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

    /**
     * The query the lists filter by. Typing is debounced so a long library is
     * not re-filtered on every keystroke; clearing the field applies at once.
     */
    private val appliedQuery = _query.debounce { if (it.isEmpty()) 0L else QUERY_DEBOUNCE_MS }

    // Search keys are folded once per library change, not on every keystroke.
    private val searchableTracks = repository.tracks.map { list ->
        list.map { Searchable(it, searchKey(it.title, it.artist, it.album)) }
    }.flowOn(Dispatchers.Default)

    private val searchableAlbums = repository.albums.map { list ->
        list.map { Searchable(it, searchKey(it.title, it.artist)) }
    }.flowOn(Dispatchers.Default)

    private val searchableArtists = repository.artists.map { list ->
        list.map { Searchable(it, searchKey(it.name)) }
    }.flowOn(Dispatchers.Default)

    // Filtering and sorting a large library is too slow for the main thread.
    val tracks: StateFlow<List<Track>> =
        combine(searchableTracks, appliedQuery, trackSort) { list, query, sort ->
            list.filterBy(query).sortedFor(sort)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> =
        combine(searchableAlbums, appliedQuery, albumSort) { list, query, sort ->
            list.filterBy(query).sortedFor(sort)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> =
        combine(searchableArtists, appliedQuery, artistSort) { list, query, sort ->
            list.filterBy(query).sortedFor(sort)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

/** A library item paired with its folded search text. */
private class Searchable<T>(val item: T, val key: String)

// The separator keeps a query from matching across the end of one field and
// the start of the next.
private fun searchKey(vararg fields: String): String =
    fields.joinToString("\u0000") { it.foldForSearch() }

/** Accent- and case-insensitive substring filter; a blank query keeps everything. */
private fun <T> List<Searchable<T>>.filterBy(query: String): List<T> {
    val needle = query.trim().foldForSearch()
    if (needle.isEmpty()) return map { it.item }
    return mapNotNull { if (needle in it.key) it.item else null }
}

/**
 * Sorts by a key computed once per element: lowercasing inside a comparator
 * would allocate two strings on every one of the n log n comparisons.
 */
private inline fun <T, K> List<T>.sortedByKey(key: (T) -> K, comparator: Comparator<K>): List<T> =
    map { key(it) to it }.sortedWith { a, b -> comparator.compare(a.first, b.first) }.map { it.second }

private val stringThenInt: Comparator<Pair<String, Int>> = compareBy({ it.first }, { it.second })
private val stringThenString: Comparator<Pair<String, String>> = compareBy({ it.first }, { it.second })

private fun List<Track>.sortedFor(sort: TrackSort): List<Track> = when (sort) {
    TrackSort.TITLE -> sortedByKey({ it.title.lowercase() }, naturalOrder())
    TrackSort.ARTIST -> sortedByKey({ it.artist.lowercase() to it.title.lowercase() }, stringThenString)
    TrackSort.ALBUM -> sortedByKey({ it.album.lowercase() to it.trackNumber }, stringThenInt)
    // Newest first — "recently added" is only useful in that direction.
    TrackSort.DATE_ADDED -> sortedByDescending { it.dateAdded }
    TrackSort.DURATION -> sortedBy { it.durationMs }
}

private fun List<Album>.sortedFor(sort: AlbumSort): List<Album> = when (sort) {
    AlbumSort.TITLE -> sortedByKey({ it.title.lowercase() }, naturalOrder())
    AlbumSort.ARTIST -> sortedByKey({ it.artist.lowercase() to it.title.lowercase() }, stringThenString)
    AlbumSort.YEAR -> sortedByDescending { it.year }
    AlbumSort.DATE_ADDED -> sortedByDescending { it.dateAdded }
}

private fun List<Artist>.sortedFor(sort: ArtistSort): List<Artist> = when (sort) {
    ArtistSort.NAME -> sortedByKey({ it.name.lowercase() }, naturalOrder())
    ArtistSort.TRACK_COUNT -> sortedByDescending { it.trackCount }
}

private const val QUERY_DEBOUNCE_MS = 150L
