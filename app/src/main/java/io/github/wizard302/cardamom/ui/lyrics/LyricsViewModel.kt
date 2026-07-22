package io.github.wizard302.cardamom.ui.lyrics

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.cardamom.data.lyrics.Lyrics
import io.github.wizard302.cardamom.data.lyrics.LrcLine
import io.github.wizard302.cardamom.data.lyrics.LrcParser
import io.github.wizard302.cardamom.data.lyrics.LyricsRepository
import io.github.wizard302.cardamom.data.settings.SettingsRepository
import io.github.wizard302.cardamom.playback.EXTRA_PATH
import io.github.wizard302.cardamom.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LyricsUiState(
    val loading: Boolean = true,
    val plain: String? = null,
    val hasSynced: Boolean = false,
    val queryArtist: String = "",
    val queryTitle: String = "",
    val searching: Boolean = false,
    val notFound: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val connection: PlayerConnection,
    private val lyricsRepository: LyricsRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    private val _lines = MutableStateFlow<List<LrcLine>>(emptyList())
    val lines: StateFlow<List<LrcLine>> = _lines.asStateFlow()

    val syncedHighlighting: StateFlow<Boolean> = settingsRepository.syncedLyricsHighlighting
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Line index active at the current playback position; -1 before the first. */
    val activeLine: StateFlow<Int> =
        combine(positionTicker(), _lines) { position, lines ->
            LrcParser.activeIndex(lines, position)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    private var loadedMediaId: Long? = null

    init {
        viewModelScope.launch {
            connection.currentItem
                .map { it?.mediaId }
                .distinctUntilChanged()
                .collect { load() }
        }
    }

    private fun load() {
        val item = connection.currentItem.value ?: return
        loadedMediaId = item.mediaId.toLongOrNull()
        val meta = item.mediaMetadata
        val artist = meta.artist?.toString().orEmpty()
        val title = meta.title?.toString().orEmpty()
        _state.update {
            it.copy(loading = true, queryArtist = artist, queryTitle = title, notFound = false, error = false)
        }
        val uri = item.localConfiguration?.uri
        viewModelScope.launch {
            val lyrics = if (uri != null) {
                lyricsRepository.getLyrics(
                    uri = uri,
                    artist = artist,
                    title = title,
                    album = meta.albumTitle?.toString().orEmpty(),
                    durationMs = connection.durationMs.value.coerceAtLeast(0),
                )
            } else {
                Lyrics(null, null)
            }
            applyLyrics(lyrics)
        }
    }

    fun setQueryArtist(v: String) = _state.update { it.copy(queryArtist = v) }
    fun setQueryTitle(v: String) = _state.update { it.copy(queryTitle = v) }

    /** Manual re-search against LRCLIB with the edited query. */
    fun research() {
        val s = _state.value
        _state.update { it.copy(searching = true, notFound = false, error = false) }
        viewModelScope.launch {
            val lyrics = lyricsRepository.refetch(
                artist = s.queryArtist,
                title = s.queryTitle,
                album = connection.currentItem.value?.mediaMetadata?.albumTitle?.toString().orEmpty(),
                durationMs = connection.durationMs.value.coerceAtLeast(0),
            )
            _state.update { it.copy(searching = false) }
            applyLyrics(lyrics)
        }
    }

    private fun applyLyrics(lyrics: Lyrics) {
        _lines.value = lyrics.synced?.let { LrcParser.parse(it) }.orEmpty()
        _state.update {
            it.copy(
                loading = false,
                plain = lyrics.plain,
                hasSynced = _lines.value.isNotEmpty(),
                notFound = lyrics.isEmpty && !lyrics.networkError,
                error = lyrics.networkError,
            )
        }
    }

    fun seekToLine(index: Int) {
        _lines.value.getOrNull(index)?.let { connection.seekTo(it.timeMs) }
    }

    private fun positionTicker() = flow {
        while (true) {
            emit(connection.currentPositionMs())
            delay(200)
        }
    }
}
