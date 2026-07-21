package io.github.wizard302.cardamom.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LyricsDao {

    @Query(
        "SELECT * FROM lyrics_cache WHERE artist = :artist AND title = :title AND durationSec = :durationSec",
    )
    suspend fun get(artist: String, title: String, durationSec: Int): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: LyricsEntity)
}
