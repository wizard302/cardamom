package io.github.wizard302.cardamom.data.media

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.wizard302.cardamom.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(FlowPreview::class)
@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val scanner: MediaStoreScanner,
    settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Everything MediaStore returned, before folder exclusions. The folder picker
    // needs this so the user can still see (and re-include) excluded folders.
    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())
    val allTracks: StateFlow<List<Track>> = _allTracks.asStateFlow()

    /** Library tracks with the user's excluded folders removed. */
    val tracks: StateFlow<List<Track>> =
        combine(_allTracks, settings.excludedFolders) { list, excluded ->
            if (excluded.isEmpty()) list else list.filterNot { it.isUnder(excluded) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> = tracks
        .map { list ->
            list.groupBy { it.albumId }
                .map { (albumId, ts) ->
                    val first = ts.first()
                    Album(
                        id = albumId,
                        title = first.album,
                        artist = first.artist,
                        artistId = first.artistId,
                        year = ts.maxOf { it.year },
                        trackCount = ts.size,
                        artUri = first.albumArtUri,
                        dateAdded = ts.maxOf { it.dateAdded },
                    )
                }
                .sortedBy { it.title.lowercase() }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> = tracks
        .map { list ->
            list.groupBy { it.artistId }
                .map { (artistId, ts) ->
                    Artist(
                        id = artistId,
                        name = ts.first().artist,
                        albumCount = ts.distinctBy { it.albumId }.size,
                        trackCount = ts.size,
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshRequests.tryEmit(Unit)
        }
    }

    init {
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        scope.launch {
            refreshRequests.debounce(1_000).collect { doScan() }
        }
    }

    /** Call after the audio permission is granted, or to force a rescan. */
    fun refresh() {
        scope.launch { doScan() }
    }

    private suspend fun doScan() {
        _allTracks.value = scanner.scanTracks()
    }
}

/** True when this track's file sits inside any of the [excludedFolders]. */
private fun Track.isUnder(excludedFolders: Set<String>): Boolean =
    excludedFolders.any { path == it || path.startsWith("$it/") }
