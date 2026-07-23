package io.github.wizard302.cardamom.data.playlist

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.util.documentUriToFilePath
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

    /** Aggregate outcome of importing every playlist file found under a folder. */
    data class FolderImportResult(val playlists: Int, val tracks: Int)

    suspend fun export(uri: Uri, entries: List<M3uEntry>) = withContext(Dispatchers.IO) {
        // Relative paths whenever the playlist's real location is resolvable —
        // that keeps exported playlists portable across devices.
        val baseDir = documentUriToFilePath(uri)?.substringBeforeLast('/', missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() }
        val text = M3uWriter.write(entries, baseDir)
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
        val playlistDir = documentUriToFilePath(uri)
            ?.substringBeforeLast('/', missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() }
        val matches = M3uMatcher.match(parsed, libraryRepository.tracks.value, playlistDir)
        val resolved = matches.mapNotNull { it.track }
        val playlistId = playlistRepository.createPlaylistWith(playlistName, resolved)
        ImportResult(
            playlistId = playlistId,
            matched = resolved.size,
            unresolved = matches.filter { it.track == null }.map { it.entry.path },
        )
    }

    /**
     * Walks the SAF tree at [treeUri] (from `ACTION_OPEN_DOCUMENT_TREE`), imports
     * every `.m3u`/`.m3u8` file into its own playlist, and returns how many were
     * added. A modern, non-deprecated alternative to `MediaStore.Audio.Playlists`:
     * it finds the real playlist files instead of the platform's playlist table.
     * Playlists whose name already exists, or that resolve to no library tracks,
     * are skipped so re-running the import stays idempotent.
     */
    suspend fun importFolder(treeUri: Uri): FolderImportResult = withContext(Dispatchers.IO) {
        val library = libraryRepository.tracks.value
        val existingNames = playlistRepository.existingNames().toMutableSet()
        val resolver = context.contentResolver

        var playlists = 0
        var tracks = 0
        val pending = ArrayDeque<String>()
        pending.add(DocumentsContract.getTreeDocumentId(treeUri))
        while (pending.isNotEmpty()) {
            val parentDocId = pending.removeLast()
            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol).orEmpty()
                    if (cursor.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(docId)
                        continue
                    }
                    if (!name.endsWith(".m3u", true) && !name.endsWith(".m3u8", true)) continue
                    val playlistName = name.substringBeforeLast('.')
                    if (playlistName in existingNames) continue
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val resolved = resolvePlaylistFile(docUri, library)

                    if (resolved.isEmpty()) continue
                    playlistRepository.createPlaylistWith(playlistName, resolved)
                    existingNames.add(playlistName)
                    playlists++
                    tracks += resolved.size
                }
            }
        }
        FolderImportResult(playlists, tracks)
    }

    /** Reads a single playlist file and resolves its entries to library tracks. */
    private fun resolvePlaylistFile(uri: Uri, library: List<Track>): List<Track> {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: return emptyList()
        val playlistDir = documentUriToFilePath(uri)
            ?.substringBeforeLast('/', missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() }
        return M3uMatcher.match(M3uParser.parse(text), library, playlistDir)
            .mapNotNull { it.track }
    }
}
