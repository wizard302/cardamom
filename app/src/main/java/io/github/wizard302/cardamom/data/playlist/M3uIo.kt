package io.github.wizard302.cardamom.data.playlist

import android.content.Context
import android.net.Uri
import io.github.wizard302.cardamom.data.media.LibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes M3U/M3U8 playlists through Storage Access Framework Uris. */
@Singleton
class M3uIo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val libraryRepository: LibraryRepository,
) {
    data class ImportResult(
        val playlistId: Long,
        val matched: Int,
        val unresolved: List<String>,
    )

    suspend fun export(uri: Uri, entries: List<M3uEntry>) = withContext(Dispatchers.IO) {
        val text = M3uWriter.write(entries)
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    /** Parses the M3U at [uri], matches against the library, and stores a new playlist. */
    suspend fun import(uri: Uri, playlistName: String): ImportResult? = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: return@withContext null

        val parsed = M3uParser.parse(text)
        val matches = M3uMatcher.match(parsed, libraryRepository.tracks.value)
        val resolved = matches.mapNotNull { it.track }
        val playlistId = playlistRepository.createPlaylistWith(playlistName, resolved)
        ImportResult(
            playlistId = playlistId,
            matched = resolved.size,
            unresolved = matches.filter { it.track == null }.map { it.entry.path },
        )
    }
}
