package io.github.wizard302.cardamom.ui.playlist

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.cardamom.data.db.PlaylistEntity
import io.github.wizard302.cardamom.data.db.PlaylistTrackEntity
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.playlist.M3uEntry
import io.github.wizard302.cardamom.data.playlist.M3uIo
import io.github.wizard302.cardamom.data.playlist.PlaylistRepository
import io.github.wizard302.cardamom.playback.PlayerConnection
import io.github.wizard302.cardamom.util.repairMojibake
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaylistRepository,
    libraryRepository: LibraryRepository,
    private val playerConnection: PlayerConnection,
    private val m3uIo: M3uIo,
) : ViewModel() {

    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    val playlist: StateFlow<PlaylistEntity?> = repository.observePlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rows: StateFlow<List<ResolvedRow>> =
        combine(
            repository.observePlaylistTracks(playlistId),
            libraryRepository.tracks,
        ) { entries, library ->
            val byId = library.associateBy { it.id }
            val byPath = library.associateBy { it.path }
            entries.map { e ->
                val track = byId[e.mediaId] ?: byPath[e.path]
                ResolvedRow(
                    key = e.id,
                    mediaId = e.mediaId,
                    // Rows cached before a tag edit (or scanned with broken
                    // encoding) go stale; the library copy wins when resolved.
                    title = track?.title ?: e.title.repairMojibake(),
                    artist = track?.artist ?: e.artist.repairMojibake(),
                    album = track?.album ?: e.album.repairMojibake(),
                    durationMs = e.durationMs,
                    path = e.path,
                    albumArtUri = track?.albumArtUri,
                    track = track,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Plays the playlist starting at the row at [index], skipping missing files. */
    fun play(index: Int) {
        val current = rows.value
        val playable = current.filter { it.track != null }
        if (playable.isEmpty()) return
        val target = current.getOrNull(index)
        val startIndex = playable.indexOfFirst { it.key == target?.key }.coerceAtLeast(0)
        playerConnection.playQueue(playable.mapNotNull { it.track }, startIndex)
    }

    /** Removed rows by id, kept so a removal can be undone from the snackbar. */
    private val removed = mutableMapOf<Long, Deferred<PlaylistTrackEntity?>>()

    fun removeTrack(rowId: Long) {
        removed[rowId] = viewModelScope.async { repository.removeTrack(rowId) }
    }

    fun undoRemove(rowId: Long) {
        val pending = removed.remove(rowId) ?: return
        viewModelScope.launch { pending.await()?.let { repository.restoreTrack(it) } }
    }

    fun persistOrder(orderedRowIds: List<Long>) {
        viewModelScope.launch { repository.persistOrder(orderedRowIds) }
    }

    fun rename(name: String) {
        viewModelScope.launch { repository.renamePlaylist(playlistId, name) }
    }

    fun delete() {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    /** Exports the playlist as M3U8 to the SAF [uri] the user picked. */
    fun export(uri: Uri) {
        viewModelScope.launch {
            val entries = repository.getPlaylistTracks(playlistId).map {
                M3uEntry(it.path, it.durationMs / 1000, it.artist, it.title)
            }
            m3uIo.export(uri, entries)
        }
    }
}
