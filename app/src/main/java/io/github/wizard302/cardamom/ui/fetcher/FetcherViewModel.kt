package io.github.wizard302.cardamom.ui.fetcher

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.remote.MetadataRepository
import io.github.wizard302.cardamom.data.remote.TrackCandidate
import io.github.wizard302.cardamom.data.tags.CoverEdit
import io.github.wizard302.cardamom.data.tags.TagRepository
import io.github.wizard302.cardamom.data.tags.TrackTags
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

enum class SearchStatus { LOADING, ERROR, EMPTY, RESULTS }

data class FetcherUiState(
    val queryArtist: String = "",
    val queryTitle: String = "",
    val status: SearchStatus = SearchStatus.LOADING,
    val candidates: List<TrackCandidate> = emptyList(),
    val currentTags: TrackTags = TrackTags(),
    // Selected-candidate review:
    val selected: TrackCandidate? = null,
    val coverLoading: Boolean = false,
    val cover: ByteArray? = null,
    val applyTitle: Boolean = true,
    val applyArtist: Boolean = true,
    val applyAlbum: Boolean = true,
    val applyYear: Boolean = true,
    val applyCover: Boolean = false,
    val saving: Boolean = false,
)

@HiltViewModel
class FetcherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val metadataRepository: MetadataRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val trackId: Long = checkNotNull(savedStateHandle["trackId"])
    private val track = libraryRepository.tracks.value.firstOrNull { it.id == trackId }

    private val _state = MutableStateFlow(
        FetcherUiState(
            queryArtist = track?.artist.orEmpty(),
            queryTitle = track?.title.orEmpty(),
        ),
    )
    val state: StateFlow<FetcherUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TagEditorEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var consent: CompletableDeferred<Boolean>? = null

    init {
        track?.let {
            viewModelScope.launch {
                val read = tagRepository.read(it.contentUri)
                if (read != null) _state.update { s -> s.copy(currentTags = read.tags) }
            }
            search()
        }
    }

    fun setQueryArtist(v: String) = _state.update { it.copy(queryArtist = v) }
    fun setQueryTitle(v: String) = _state.update { it.copy(queryTitle = v) }

    fun search() {
        val s = _state.value
        if (s.queryTitle.isBlank()) return
        _state.update { it.copy(status = SearchStatus.LOADING, candidates = emptyList()) }
        viewModelScope.launch {
            runCatching { metadataRepository.searchTrack(s.queryArtist, s.queryTitle) }
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            candidates = list,
                            status = if (list.isEmpty()) SearchStatus.EMPTY else SearchStatus.RESULTS,
                        )
                    }
                }
                .onFailure { e ->
                    android.util.Log.w("Cardamom", "Track search failed", e)
                    _state.update { it.copy(status = SearchStatus.ERROR) }
                }
        }
    }

    fun selectCandidate(candidate: TrackCandidate) {
        _state.update {
            it.copy(
                selected = candidate,
                cover = null,
                applyCover = false,
                coverLoading = true,
                applyTitle = true,
                applyArtist = true,
                applyAlbum = candidate.album.isNotBlank(),
                applyYear = candidate.year.isNotBlank(),
            )
        }
        viewModelScope.launch {
            val cover = metadataRepository.fetchCover(
                releaseMbid = candidate.releaseMbid,
                artist = candidate.artist,
                title = candidate.title,
            )
            _state.update { it.copy(cover = cover, coverLoading = false, applyCover = cover != null) }
        }
    }

    fun dismissSelection() = _state.update { it.copy(selected = null, cover = null) }

    fun toggleTitle(on: Boolean) = _state.update { it.copy(applyTitle = on) }
    fun toggleArtist(on: Boolean) = _state.update { it.copy(applyArtist = on) }
    fun toggleAlbum(on: Boolean) = _state.update { it.copy(applyAlbum = on) }
    fun toggleYear(on: Boolean) = _state.update { it.copy(applyYear = on) }
    fun toggleCover(on: Boolean) = _state.update { it.copy(applyCover = on) }

    fun onConsentResult(granted: Boolean) {
        consent?.complete(granted)
        consent = null
    }

    fun apply() {
        val uri = track?.contentUri ?: return
        val s = _state.value
        val candidate = s.selected ?: return
        val merged = s.currentTags.copy(
            title = if (s.applyTitle) candidate.title else s.currentTags.title,
            artist = if (s.applyArtist) candidate.artist else s.currentTags.artist,
            album = if (s.applyAlbum) candidate.album else s.currentTags.album,
            year = if (s.applyYear) candidate.year else s.currentTags.year,
        )
        val coverEdit = if (s.applyCover && s.cover != null) {
            CoverEdit.Replace(s.cover, "image/jpeg")
        } else {
            CoverEdit.Keep
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val ok = writeWithScopedConsent(
                context = context,
                uris = listOf(uri),
                requestConsent = ::requestConsent,
                write = { tagRepository.write(uri, merged, coverEdit) },
            )
            if (ok) {
                tagRepository.notifyFileChanged(track.path)
                libraryRepository.refresh()
            }
            _state.update { it.copy(saving = false) }
            _events.emit(if (ok) TagEditorEvent.Saved else TagEditorEvent.Error)
        }
    }

    private suspend fun requestConsent(sender: IntentSender): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        consent = deferred
        _events.emit(TagEditorEvent.RequestConsent(sender))
        return deferred.await()
    }
}
