package io.github.wizard302.cardamom.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A playlist together with its current track count, for list rows. */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
)

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id, p.name, p.createdAt,
               (SELECT COUNT(*) FROM playlist_tracks t WHERE t.playlistId = p.id) AS trackCount
        FROM playlists p
        ORDER BY p.name COLLATE NOCASE ASC
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT name FROM playlists")
    suspend fun allNames(): List<String>

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getTracks(playlistId: Long): List<PlaylistTrackEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE id = :rowId")
    suspend fun deleteTrack(rowId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE id = :rowId")
    suspend fun setPosition(rowId: Long, position: Int)

    /** Appends [tracks] after the current last position, preserving their order. */
    @Transaction
    suspend fun appendTracks(playlistId: Long, tracks: List<PlaylistTrackEntity>) {
        val start = maxPosition(playlistId) + 1
        insertTracks(tracks.mapIndexed { i, t -> t.copy(playlistId = playlistId, position = start + i) })
    }

    /** Rewrites positions to match the given ordered [rowIds] (after a reorder). */
    @Transaction
    suspend fun persistOrder(rowIds: List<Long>) {
        rowIds.forEachIndexed { i, rowId -> setPosition(rowId, i) }
    }
}
