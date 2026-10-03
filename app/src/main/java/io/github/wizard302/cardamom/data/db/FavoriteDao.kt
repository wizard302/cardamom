package io.github.wizard302.cardamom.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT mediaId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun remove(mediaId: Long)

    @Query("SELECT * FROM favorites WHERE mediaId = :mediaId")
    suspend fun get(mediaId: Long): FavoriteEntity?

    /** Deletes the favorite and returns it, so the removal can be undone. */
    @Transaction
    suspend fun take(mediaId: Long): FavoriteEntity? =
        get(mediaId)?.also { remove(mediaId) }

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId)")
    suspend fun isFavorite(mediaId: Long): Boolean
}
