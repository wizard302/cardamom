package io.github.wizard302.cardamom.ui.lyrics

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.cardamom.data.lyrics.Lyrics
import io.github.wizard302.cardamom.data.lyrics.LrcLine
import io.github.wizard302.cardamom.data.lyrics.LrcParser
import io.github.wizard302.cardamom.data.lyrics.LyricsRepository
import io.github.wizard302.cardamom.data.settings.SettingsRepository
import io.github.wizard302.cardamom.data.tags.TagRepository
import io.github.wizard302.cardamom.data.tags.writeWithScopedConsent
import io.github.wizard302.cardamom.playback.EXTRA_DURATION_MS
import io.github.wizard302.cardamom.playback.EXTRA_PATH
import io.github.wizard302.cardamom.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    /**
     * Whether the lyrics on screen came from LRCLIB/the cache and can still be
     * written into the file. False once they are the file's own copy.
     */
    val canSaveToFile: Boolean = false,
    val savingToFile: Boolean = false,
)

sealed interface LyricsEvent {
    /** Ask the UI to launch a scoped-storage write-consent dialog. */
    data class RequestConsent(val intentSender: IntentSender) : LyricsEvent
    data object Saved : LyricsEvent
    data object Error : LyricsEvent
}

@HiltViewModel
class LyricsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connection: PlayerConnection,
    private val lyricsRepository: LyricsRepository,
    private val settingsRepository: SettingsRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    private val _lines = MutableStateFlow<List<LrcLine>>(emptyList())
    val lines: StateFlow<List<LrcLine>> = _lines.asStateFlow()

    val syncedHighlighting: StateFlow<Boolean> = settingsRepository.syncedLyricsHighlighting
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Toggles karaoke (synced) highlighting from the lyrics screen. */
    fun toggleSyncedHighlighting() {
        val enabled = !syncedHighlighting.value
        viewModelScope.launch { settingsRepository.setSyncedLyricsHighlighting(enabled) }
    }

    /** Line index active at the current playback position; -1 before the first. */
    val activeLine: StateFlow<Int> =
        combine(positionTicker(), _lines) { position, lines ->
            LrcParser.activeIndex(lines, position)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    private val _events = MutableSharedFlow<LyricsEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var consent: CompletableDeferred<Boolean>? = null

    /** Text eligible for embedding: the LRCLIB copy, synced preferred. */
    private var fetchedText: String? = null

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
                    durationMs = trackDurationMs(),
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
                durationMs = trackDurationMs(),
            )
            _state.update { it.copy(searching = false) }
            applyLyrics(lyrics)
        }
    }

    private fun applyLyrics(lyrics: Lyrics) {
        _lines.value = lyrics.synced?.let { LrcParser.parse(it) }.orEmpty()
        // Synced text always comes from LRCLIB (embedded lyrics are read as plain),
        // so anything but a file-sourced plain-only result is worth saving.
        fetchedText = lyrics.synced?.takeIf { it.isNotBlank() }
            ?: lyrics.plain?.takeIf { it.isNotBlank() && !lyrics.plainFromFile }
        _state.update {
            it.copy(
                loading = false,
                plain = lyrics.plain,
                hasSynced = _lines.value.isNotEmpty(),
                notFound = lyrics.isEmpty && !lyrics.networkError,
                error = lyrics.networkError,
                canSaveToFile = fetchedText != null,
            )
        }
    }

    /** Feeds back the scoped-storage consent dialog result. */
    fun onConsentResult(granted: Boolean) {
        consent?.complete(granted)
        consent = null
    }

    /**
     * Embeds the fetched lyrics in the playing file. The synced (LRC) text wins
     * over the plain one; after a successful write the file's own copy takes
     * priority on the next load, so the action disappears.
     */
    fun saveToFile() {
        val text = fetchedText ?: return
        val item = connection.currentItem.value ?: return
        val uri = item.localConfiguration?.uri ?: return
        val path = item.mediaMetadata.extras?.getString(EXTRA_PATH).orEmpty()
        viewModelScope.launch {
            _state.update { it.copy(savingToFile = true) }
            val ok = writeWithScopedConsent(
                context = context,
                uris = listOf(uri),
                requestConsent = ::requestConsent,
                write = { tagRepository.writeLyrics(uri, text) },
            )
            if (ok && path.isNotEmpty()) tagRepository.notifyFileChanged(path)
            _state.update { it.copy(savingToFile = false, canSaveToFile = !ok) }
            _events.emit(if (ok) LyricsEvent.Saved else LyricsEvent.Error)
        }
    }

    private suspend fun requestConsent(sender: IntentSender): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        consent = deferred
        _events.emit(LyricsEvent.RequestConsent(sender))
        return deferred.await()
    }

    fun seekToLine(index: Int) {
        _lines.value.getOrNull(index)?.let { connection.seekTo(it.timeMs) }
    }

    /**
     * The MediaStore duration carried in the item's extras: it matches the file
     * exactly (LRCLIB matches on duration) and, unlike the player's duration,
     * is already correct at the moment of a gapless transition.
     */
    private fun trackDurationMs(): Long {
        val extras = connection.currentItem.value?.mediaMetadata?.extras
        val fromExtras = extras?.getLong(EXTRA_DURATION_MS, 0L) ?: 0L
        return if (fromExtras > 0) fromExtras else connection.durationMs.value.coerceAtLeast(0)
    }

    private fun positionTicker() = flow {
        while (true) {
            emit(connection.currentPositionMs())
            delay(200)
        }
    }
}
