package io.github.wizard302.cardamom.data.playlist

import io.github.wizard302.cardamom.data.db.FavoriteDao
import io.github.wizard302.cardamom.data.db.FavoriteEntity
import io.github.wizard302.cardamom.data.db.PlaylistDao
import io.github.wizard302.cardamom.data.db.PlaylistEntity
import io.github.wizard302.cardamom.data.db.PlaylistTrackEntity
import io.github.wizard302.cardamom.data.db.PlaylistWithCount
import io.github.wizard302.cardamom.data.media.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
) {
    val playlists: Flow<List<PlaylistWithCount>> = playlistDao.observePlaylists()

    val favorites: Flow<List<FavoriteEntity>> = favoriteDao.observeFavorites()

    val favoriteIds: Flow<Set<Long>> =
        favoriteDao.observeFavoriteIds().map { it.toSet() }

    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?> =
        playlistDao.observePlaylist(playlistId)

    fun observePlaylistTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>> =
        playlistDao.observeTracks(playlistId)

    suspend fun getPlaylistTracks(playlistId: Long): List<PlaylistTrackEntity> =
        playlistDao.getTracks(playlistId)

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(
            PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()),
        )

    suspend fun renamePlaylist(playlistId: Long, name: String) =
        playlistDao.renamePlaylist(playlistId, name.trim())

    suspend fun deletePlaylist(playlistId: Long) =
        playlistDao.deletePlaylist(playlistId)

    suspend fun addTracks(playlistId: Long, tracks: List<Track>) =
        playlistDao.appendTracks(playlistId, tracks.map { it.toPlaylistTrack() })

    /** Convenience for creating a playlist and seeding it in one step. */
    suspend fun createPlaylistWith(name: String, tracks: List<Track>): Long {
        val id = createPlaylist(name)
        if (tracks.isNotEmpty()) addTracks(id, tracks)
        return id
    }

    suspend fun removeTrack(rowId: Long) = playlistDao.deleteTrack(rowId)

    /** Persists a new order given the row ids in their intended sequence. */
    suspend fun persistOrder(orderedRowIds: List<Long>) =
        playlistDao.persistOrder(orderedRowIds)

    suspend fun setFavorite(track: Track, favorite: Boolean) {
        if (favorite) {
            favoriteDao.add(track.toFavorite())
        } else {
            favoriteDao.remove(track.id)
        }
    }

    suspend fun toggleFavorite(
        mediaId: Long,
        path: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ) {
        if (favoriteDao.isFavorite(mediaId)) {
            favoriteDao.remove(mediaId)
        } else {
            favoriteDao.add(
                FavoriteEntity(
                    mediaId = mediaId,
                    path = path,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}

private fun Track.toPlaylistTrack(): PlaylistTrackEntity =
    PlaylistTrackEntity(
        playlistId = 0,
        position = 0,
        mediaId = id,
        path = path,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
    )

private fun Track.toFavorite(): FavoriteEntity =
    FavoriteEntity(
        mediaId = id,
        path = path,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        addedAt = System.currentTimeMillis(),
    )
