package io.github.wizard302.cardamom.ui.tageditor

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.data.tags.CoverEdit
import io.github.wizard302.cardamom.data.tags.TagRepository
import io.github.wizard302.cardamom.data.tags.writeWithScopedConsent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Only the fields whose `apply` flag is on are written; the rest of each
 * track's tags (title, per-track artist, track/disc numbers) are preserved.
 */
data class AlbumTagState(
    val loading: Boolean = true,
    val trackCount: Int = 0,
    val album: String = "",
    val applyAlbum: Boolean = false,
    val albumArtist: String = "",
    val applyAlbumArtist: Boolean = false,
    val year: String = "",
    val applyYear: Boolean = false,
    val genre: String = "",
    val applyGenre: Boolean = false,
    val cover: ByteArray? = null,
    val coverEdit: CoverEdit = CoverEdit.Keep,
    val saving: Boolean = false,
) {
    val hasChanges: Boolean
        get() = applyAlbum || applyAlbumArtist || applyYear || applyGenre || coverEdit != CoverEdit.Keep
}

@HiltViewModel
class AlbumTagEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val albumId: Long = checkNotNull(savedStateHandle["albumId"])

    /** Resolved asynchronously: the library may still be scanning after process death. */
    private var tracks: List<Track> = emptyList()

    private val _state = MutableStateFlow(AlbumTagState())
    val state: StateFlow<AlbumTagState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TagEditorEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var consent: CompletableDeferred<Boolean>? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            tracks = libraryRepository.awaitAlbumTracks(albumId).sortedBy { it.trackNumber }
            val album = libraryRepository.albums.value.firstOrNull { it.id == albumId }
            val first = tracks.firstOrNull()
            val read = first?.let { tagRepository.read(it.contentUri) }
            _state.update {
                it.copy(
                    loading = false,
                    trackCount = tracks.size,
                    album = album?.title ?: read?.tags?.album.orEmpty(),
                    albumArtist = read?.tags?.albumArtist.orEmpty(),
                    year = album?.year?.takeIf { y -> y > 0 }?.toString()
                        ?: read?.tags?.year.orEmpty(),
                    genre = read?.tags?.genre.orEmpty(),
                    cover = read?.cover,
                )
            }
        }
    }

    fun setAlbum(v: String) = _state.update { it.copy(album = v, applyAlbum = true) }
    fun setAlbumArtist(v: String) = _state.update { it.copy(albumArtist = v, applyAlbumArtist = true) }
    fun setYear(v: String) = _state.update { it.copy(year = v, applyYear = true) }
    fun setGenre(v: String) = _state.update { it.copy(genre = v, applyGenre = true) }

    fun toggleAlbum(on: Boolean) = _state.update { it.copy(applyAlbum = on) }
    fun toggleAlbumArtist(on: Boolean) = _state.update { it.copy(applyAlbumArtist = on) }
    fun toggleYear(on: Boolean) = _state.update { it.copy(applyYear = on) }
    fun toggleGenre(on: Boolean) = _state.update { it.copy(applyGenre = on) }

    fun replaceCover(data: ByteArray, mimeType: String) =
        _state.update { it.copy(cover = data, coverEdit = CoverEdit.Replace(data, mimeType)) }

    fun removeCover() = _state.update { it.copy(cover = null, coverEdit = CoverEdit.Remove) }

    fun onConsentResult(granted: Boolean) {
        consent?.complete(granted)
        consent = null
    }

    fun save() {
        val s = _state.value
        if (tracks.isEmpty() || !s.hasChanges) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val ok = writeWithScopedConsent(
                context = context,
                uris = tracks.map { it.contentUri },
                requestConsent = ::requestConsent,
                write = { applyToAllTracks(s) },
            )
            if (ok) {
                tracks.forEach { tagRepository.notifyFileChanged(it.path) }
                libraryRepository.refresh()
            }
            _state.update { it.copy(saving = false) }
            _events.emit(if (ok) TagEditorEvent.Saved else TagEditorEvent.Error)
        }
    }

    private suspend fun applyToAllTracks(s: AlbumTagState): Boolean {
        var allOk = true
        for (track in tracks) {
            val base = tagRepository.read(track.contentUri)?.tags ?: continue
            val merged = base.copy(
                album = if (s.applyAlbum) s.album else base.album,
                albumArtist = if (s.applyAlbumArtist) s.albumArtist else base.albumArtist,
                year = if (s.applyYear) s.year else base.year,
                genre = if (s.applyGenre) s.genre else base.genre,
            )
            val ok = tagRepository.write(track.contentUri, merged, s.coverEdit)
            allOk = allOk && ok
        }
        return allOk
    }

    private suspend fun requestConsent(sender: IntentSender): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        consent = deferred
        _events.emit(TagEditorEvent.RequestConsent(sender))
        return deferred.await()
    }
}
