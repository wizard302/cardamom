package io.github.wizard302.cardamom.data.media

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.wizard302.cardamom.util.repairMojibake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun scanTracks(): List<Track> = withContext(Dispatchers.IO) {
        query(selection = null, selectionArgs = null)
    }

    /**
     * Only the tracks with the given MediaStore [ids] — e.g. to rebuild a saved
     * queue without scanning the whole library. Missing ids are simply absent.
     */
    suspend fun tracksByIds(ids: Collection<Long>): List<Track> = withContext(Dispatchers.IO) {
        // Chunked to stay under SQLite's bound-parameter limit on older devices.
        ids.distinct().chunked(MAX_QUERY_ARGS).flatMap { chunk ->
            query(
                selection = "${MediaStore.Audio.Media._ID} IN (${chunk.joinToString(",") { "?" }})",
                selectionArgs = chunk.map { it.toString() }.toTypedArray(),
            )
        }
    }

    private fun query(selection: String?, selectionArgs: Array<String>?): List<Track> {
        val hasBitrate = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val hasAlbumArtist = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.SIZE)
            if (hasBitrate) add(MediaStore.Audio.Media.BITRATE)
            if (hasAlbumArtist) add(MediaStore.Audio.Media.ALBUM_ARTIST)
        }.toTypedArray()
        val tracks = mutableListOf<Track>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                listOfNotNull("${MediaStore.Audio.Media.IS_MUSIC} != 0", selection)
                    .joinToString(" AND ") { "($it)" },
                selectionArgs,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val bitrateCol = if (hasBitrate) {
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
                } else {
                    -1
                }
                val albumArtistCol = if (hasAlbumArtist) {
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    tracks += Track(
                        id = cursor.getLong(idCol),
                        title = cursor.getString(titleCol)?.repairMojibake() ?: "",
                        artist = cursor.getString(artistCol)?.repairMojibake() ?: "",
                        artistId = cursor.getLong(artistIdCol),
                        album = cursor.getString(albumCol)?.repairMojibake() ?: "",
                        albumId = cursor.getLong(albumIdCol),
                        durationMs = cursor.getLong(durationCol),
                        trackNumber = cursor.getInt(trackCol),
                        year = cursor.getInt(yearCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        path = cursor.getString(dataCol) ?: "",
                        sizeBytes = cursor.getLong(sizeCol),
                        bitrate = if (bitrateCol >= 0) cursor.getInt(bitrateCol) else 0,
                        albumArtist = if (albumArtistCol >= 0) {
                            cursor.getString(albumArtistCol)?.repairMojibake().orEmpty()
                        } else {
                            ""
                        },
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted yet; caller shows the permission gate.
        }
        return tracks
    }

    private companion object {
        const val MAX_QUERY_ARGS = 500
    }
}
