package io.github.wizard302.cardamom.ui.tageditor

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.tags.CoverEdit
import io.github.wizard302.cardamom.data.tags.TagRepository
import io.github.wizard302.cardamom.data.tags.TrackTags
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

data class TagEditorUiState(
    val loading: Boolean = true,
    val title: String = "",
    val tags: TrackTags = TrackTags(),
    val cover: ByteArray? = null,
    val coverEdit: CoverEdit = CoverEdit.Keep,
    val saving: Boolean = false,
    val readFailed: Boolean = false,
)

sealed interface TagEditorEvent {
    /** Ask the UI to launch a scoped-storage write-consent dialog. */
    data class RequestConsent(val intentSender: IntentSender) : TagEditorEvent
    data object Saved : TagEditorEvent
    data object Error : TagEditorEvent
}

@HiltViewModel
class TagEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val trackId: Long = checkNotNull(savedStateHandle["trackId"])
    private val track = libraryRepository.tracks.value.firstOrNull { it.id == trackId }

    private val _state = MutableStateFlow(TagEditorUiState())
    val state: StateFlow<TagEditorUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TagEditorEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var consent: CompletableDeferred<Boolean>? = null

    init {
        load()
    }

    private fun load() {
        val uri = track?.contentUri
        if (uri == null) {
            _state.update { it.copy(loading = false, readFailed = true) }
            return
        }
        viewModelScope.launch {
            val result = tagRepository.read(uri)
            if (result == null) {
                _state.update { it.copy(loading = false, readFailed = true) }
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        title = result.tags.title.ifEmpty { track.title },
                        tags = result.tags,
                        cover = result.cover,
                    )
                }
            }
        }
    }

    fun updateTags(transform: (TrackTags) -> TrackTags) {
        _state.update { it.copy(tags = transform(it.tags)) }
    }

    fun replaceCover(data: ByteArray, mimeType: String) {
        _state.update {
            it.copy(cover = data, coverEdit = CoverEdit.Replace(data, mimeType))
        }
    }

    fun removeCover() {
        _state.update { it.copy(cover = null, coverEdit = CoverEdit.Remove) }
    }

    /** Feeds back the scoped-storage consent dialog result. */
    fun onConsentResult(granted: Boolean) {
        consent?.complete(granted)
        consent = null
    }

    fun save() {
        val uri = track?.contentUri ?: return
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val ok = writeWithScopedConsent(
                context = context,
                uris = listOf(uri),
                requestConsent = ::requestConsent,
                write = { tagRepository.write(uri, current.tags, current.coverEdit) },
            )
            if (ok) {
                tagRepository.notifyFileChanged(track.path)
                invalidateArtworkCache(track.albumArtUri)
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

    private fun invalidateArtworkCache(artworkUri: Uri) {
        runCatching {
            val loader = coil3.SingletonImageLoader.get(context)
            val key = artworkUri.toString()
            loader.memoryCache?.remove(coil3.memory.MemoryCache.Key(key))
            loader.diskCache?.remove(key)
        }
    }
}
