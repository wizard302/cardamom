package io.github.wizard302.cardamom.data.lyrics

import android.net.Uri
import io.github.wizard302.cardamom.data.db.LyricsDao
import io.github.wizard302.cardamom.data.db.LyricsEntity
import io.github.wizard302.cardamom.data.remote.LrcLibApi
import io.github.wizard302.cardamom.data.tags.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Resolved lyrics for a track. Either field may be null. */
data class Lyrics(
    val plain: String?,
    val synced: String?,
) {
    val isEmpty: Boolean get() = plain.isNullOrBlank() && synced.isNullOrBlank()
}

/**
 * Resolves lyrics with the priority embedded tag → Room cache → LRCLIB.
 * LRCLIB results (including "not found") are cached, keyed by
 * (artist, title, duration), so repeat lookups don't hammer the API.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val tagRepository: TagRepository,
    private val lyricsDao: LyricsDao,
    private val lrcLibApi: LrcLibApi,
) {
    suspend fun getLyrics(
        uri: Uri,
        artist: String,
        title: String,
        album: String,
        durationMs: Long,
    ): Lyrics = withContext(Dispatchers.IO) {
        val embeddedPlain = tagRepository.readLyrics(uri)?.takeIf { it.isNotBlank() }

        val durationSec = (durationMs / 1000).toInt()
        val cached = runCatching { lyricsDao.get(artist, title, durationSec) }.getOrNull()
        val remote = if (cached == null) fetchAndCache(artist, title, album, durationSec) else cached

        Lyrics(
            plain = embeddedPlain ?: remote?.plainLyrics?.takeIf { it.isNotBlank() },
            synced = remote?.syncedLyrics?.takeIf { it.isNotBlank() },
        )
    }

    /** Forces a fresh LRCLIB lookup (bypasses the cache) for manual re-search. */
    suspend fun refetch(
        artist: String,
        title: String,
        album: String,
        durationMs: Long,
    ): Lyrics = withContext(Dispatchers.IO) {
        val row = fetchAndCache(artist, title, album, (durationMs / 1000).toInt())
        Lyrics(row?.plainLyrics, row?.syncedLyrics)
    }

    private suspend fun fetchAndCache(
        artist: String,
        title: String,
        album: String,
        durationSec: Int,
    ): LyricsEntity? {
        val response = runCatching {
            lrcLibApi.get(artist, title, album, durationSec)
        }.getOrNull()

        val entity = LyricsEntity(
            artist = artist,
            title = title,
            durationSec = durationSec,
            plainLyrics = response?.plainLyrics,
            syncedLyrics = response?.syncedLyrics,
            found = response != null,
            fetchedAt = System.currentTimeMillis(),
        )
        runCatching { lyricsDao.put(entity) }
        return entity
    }
}
