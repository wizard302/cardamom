package io.github.wizard302.cardamom.ui.playlist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.playlist.M3uEntry
import io.github.wizard302.cardamom.data.playlist.M3uIo
import io.github.wizard302.cardamom.data.playlist.PlaylistRepository
import io.github.wizard302.cardamom.playback.PlayerConnection
import io.github.wizard302.cardamom.util.repairMojibake
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    libraryRepository: LibraryRepository,
    private val playerConnection: PlayerConnection,
    private val m3uIo: M3uIo,
) : ViewModel() {

    val rows: StateFlow<List<ResolvedRow>> =
        combine(repository.favorites, libraryRepository.tracks) { favorites, library ->
            val byId = library.associateBy { it.id }
            val byPath = library.associateBy { it.path }
            favorites.map { f ->
                val track = byId[f.mediaId] ?: byPath[f.path]
                ResolvedRow(
                    key = f.mediaId,
                    mediaId = f.mediaId,
                    // Same as playlists: prefer the live library copy of the tags.
                    title = track?.title ?: f.title.repairMojibake(),
                    artist = track?.artist ?: f.artist.repairMojibake(),
                    album = track?.album ?: f.album.repairMojibake(),
                    durationMs = f.durationMs,
                    path = f.path,
                    albumArtUri = track?.albumArtUri,
                    track = track,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(index: Int) {
        val current = rows.value
        val playable = current.filter { it.track != null }
        if (playable.isEmpty()) return
        val target = current.getOrNull(index)
        val startIndex = playable.indexOfFirst { it.key == target?.key }.coerceAtLeast(0)
        playerConnection.playQueue(playable.mapNotNull { it.track }, startIndex)
    }

    fun remove(mediaId: Long) {
        viewModelScope.launch { repository.removeFavorite(mediaId) }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val entries = rows.value.map {
                M3uEntry(it.path, it.durationMs / 1000, it.artist, it.title)
            }
            m3uIo.export(uri, entries)
        }
    }
}
