package io.github.wizard302.cardamom.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        LyricsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CardamomDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun lyricsDao(): LyricsDao
}

/** Adds the lyrics cache table without touching playlists/favorites. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lyrics_cache (
                artist TEXT NOT NULL,
                title TEXT NOT NULL,
                durationSec INTEGER NOT NULL,
                plainLyrics TEXT,
                syncedLyrics TEXT,
                found INTEGER NOT NULL,
                fetchedAt INTEGER NOT NULL,
                PRIMARY KEY(artist, title, durationSec)
            )
            """.trimIndent(),
        )
    }
}
