package io.github.wizard302.cardamom.ui.fetcher

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.data.remote.AlbumCandidate
import io.github.wizard302.cardamom.data.remote.AlbumReleaseDetail
import io.github.wizard302.cardamom.data.remote.MetadataRepository
import io.github.wizard302.cardamom.data.tags.CoverEdit
import io.github.wizard302.cardamom.data.tags.TagRepository
import io.github.wizard302.cardamom.data.tags.writeWithScopedConsent
import io.github.wizard302.cardamom.ui.tageditor.TagEditorEvent
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

/** One existing track lined up with the release track at the same position. */
data class TrackPreview(
    val position: Int,
    val currentTitle: String,
    val newTitle: String?,
)

data class AlbumFetcherUiState(
    val queryArtist: String = "",
    val queryAlbum: String = "",
    val status: SearchStatus = SearchStatus.LOADING,
    val candidates: List<AlbumCandidate> = emptyList(),
    // Review of a chosen release:
    val release: AlbumReleaseDetail? = null,
    val releaseLoading: Boolean = false,
    val cover: ByteArray? = null,
    val coverLoading: Boolean = false,
    val previews: List<TrackPreview> = emptyList(),
    val applyAlbum: Boolean = true,
    val applyAlbumArtist: Boolean = true,
    val applyYear: Boolean = true,
    val applyTrackTitles: Boolean = true,
    val applyCover: Boolean = false,
    val saving: Boolean = false,
)

@HiltViewModel
class AlbumFetcherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val metadataRepository: MetadataRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val albumId: Long = checkNotNull(savedStateHandle["albumId"])
    private val albumTracks: List<Track> = libraryRepository.tracks.value
        .filter { it.albumId == albumId }
        .sortedBy { it.trackNumber }

    private val _state = MutableStateFlow(AlbumFetcherUiState())
    val state: StateFlow<AlbumFetcherUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TagEditorEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var consent: CompletableDeferred<Boolean>? = null

    init {
        val album = libraryRepository.albums.value.firstOrNull { it.id == albumId }
        _state.update {
            it.copy(
                queryArtist = album?.artist ?: albumTracks.firstOrNull()?.artist.orEmpty(),
                queryAlbum = album?.title ?: albumTracks.firstOrNull()?.album.orEmpty(),
            )
        }
        search()
    }

    fun setQueryArtist(v: String) = _state.update { it.copy(queryArtist = v) }
    fun setQueryAlbum(v: String) = _state.update { it.copy(queryAlbum = v) }

    fun search() {
        val s = _state.value
        if (s.queryAlbum.isBlank()) return
        _state.update { it.copy(status = SearchStatus.LOADING, candidates = emptyList()) }
        viewModelScope.launch {
            runCatching { metadataRepository.searchAlbum(s.queryArtist, s.queryAlbum) }
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            candidates = list,
                            status = if (list.isEmpty()) SearchStatus.EMPTY else SearchStatus.RESULTS,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(status = SearchStatus.ERROR) } }
        }
    }

    fun selectRelease(candidate: AlbumCandidate) {
        _state.update { it.copy(releaseLoading = true, coverLoading = true, cover = null) }
        viewModelScope.launch {
            val detail = runCatching { metadataRepository.getAlbumRelease(candidate.releaseMbid) }
                .getOrNull()
            if (detail == null) {
                _state.update { it.copy(releaseLoading = false, coverLoading = false) }
                _events.emit(TagEditorEvent.Error)
                return@launch
            }
            val previews = albumTracks.mapIndexed { i, track ->
                TrackPreview(
                    position = i + 1,
                    currentTitle = track.title,
                    newTitle = detail.tracks.getOrNull(i)?.title,
                )
            }
            _state.update {
                it.copy(
                    release = detail,
                    releaseLoading = false,
                    previews = previews,
                    applyAlbum = true,
                    applyAlbumArtist = true,
                    applyYear = detail.year.isNotBlank(),
                    applyTrackTitles = true,
                    applyCover = false,
                )
            }
            val cover = metadataRepository.fetchCover(candidate.releaseMbid, candidate.artist, candidate.title)
            _state.update { it.copy(cover = cover, coverLoading = false, applyCover = cover != null) }
        }
    }

    fun backToResults() = _state.update { it.copy(release = null, cover = null, previews = emptyList()) }

    fun toggleAlbum(on: Boolean) = _state.update { it.copy(applyAlbum = on) }
    fun toggleAlbumArtist(on: Boolean) = _state.update { it.copy(applyAlbumArtist = on) }
    fun toggleYear(on: Boolean) = _state.update { it.copy(applyYear = on) }
    fun toggleTrackTitles(on: Boolean) = _state.update { it.copy(applyTrackTitles = on) }
    fun toggleCover(on: Boolean) = _state.update { it.copy(applyCover = on) }

    fun onConsentResult(granted: Boolean) {
        consent?.complete(granted)
        consent = null
    }

    fun apply() {
        val s = _state.value
        val release = s.release ?: return
        if (albumTracks.isEmpty()) return
        val coverEdit = if (s.applyCover && s.cover != null) {
            CoverEdit.Replace(s.cover, "image/jpeg")
        } else {
            CoverEdit.Keep
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val ok = writeWithScopedConsent(
                context = context,
                uris = albumTracks.map { it.contentUri },
                requestConsent = ::requestConsent,
                write = { applyToTracks(release, s, coverEdit) },
            )
            if (ok) {
                albumTracks.forEach { tagRepository.notifyFileChanged(it.path) }
                libraryRepository.refresh()
            }
            _state.update { it.copy(saving = false) }
            _events.emit(if (ok) TagEditorEvent.Saved else TagEditorEvent.Error)
        }
    }

    private suspend fun applyToTracks(
        release: AlbumReleaseDetail,
        s: AlbumFetcherUiState,
        coverEdit: CoverEdit,
    ): Boolean {
        var allOk = true
        albumTracks.forEachIndexed { i, track ->
            val base = tagRepository.read(track.contentUri)?.tags ?: return@forEachIndexed
            val releaseTitle = release.tracks.getOrNull(i)?.title
            val merged = base.copy(
                album = if (s.applyAlbum) release.title else base.album,
                albumArtist = if (s.applyAlbumArtist) release.artist else base.albumArtist,
                year = if (s.applyYear) release.year else base.year,
                title = if (s.applyTrackTitles && releaseTitle != null) releaseTitle else base.title,
            )
            val ok = tagRepository.write(track.contentUri, merged, coverEdit)
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
