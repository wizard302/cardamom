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
    /** True when LRCLIB could not be reached, as opposed to having no match. */
    val networkError: Boolean = false,
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
        // Negative results expire: lyrics that appear on LRCLIB later should be
        // picked up automatically instead of requiring a manual re-search.
        val staleNegative = cached != null && !cached.found &&
            System.currentTimeMillis() - cached.fetchedAt > NEGATIVE_CACHE_TTL_MS
        val fetched = if (cached == null || staleNegative) {
            fetchAndCache(artist, title, album, durationSec)
        } else {
            FetchResult(cached, networkError = false)
        }

        Lyrics(
            plain = embeddedPlain ?: fetched.entity?.plainLyrics?.takeIf { it.isNotBlank() },
            synced = fetched.entity?.syncedLyrics?.takeIf { it.isNotBlank() },
            // An embedded copy makes a failed lookup irrelevant.
            networkError = fetched.networkError && embeddedPlain == null,
        )
    }

    /** Forces a fresh LRCLIB lookup (bypasses the cache) for manual re-search. */
    suspend fun refetch(
        artist: String,
        title: String,
        album: String,
        durationMs: Long,
    ): Lyrics = withContext(Dispatchers.IO) {
        val result = fetchAndCache(artist, title, album, (durationMs / 1000).toInt())
        Lyrics(
            plain = result.entity?.plainLyrics,
            synced = result.entity?.syncedLyrics,
            networkError = result.networkError,
        )
    }

    private class FetchResult(val entity: LyricsEntity?, val networkError: Boolean)

    /**
     * Only a definite answer from LRCLIB is cached. A transport failure or a
     * server error must not be stored as "no lyrics", or the miss would stick
     * around long after the network came back.
     */
    private suspend fun fetchAndCache(
        artist: String,
        title: String,
        album: String,
        durationSec: Int,
    ): FetchResult {
        val response = runCatching {
            lrcLibApi.get(artist, title, album, durationSec)
        }.getOrElse { return FetchResult(null, networkError = true) }

        if (!response.isSuccessful && response.code() != HTTP_NOT_FOUND) {
            return FetchResult(null, networkError = true)
        }
        val body = response.body().takeIf { response.isSuccessful }

        val entity = LyricsEntity(
            artist = artist,
            title = title,
            durationSec = durationSec,
            plainLyrics = body?.plainLyrics,
            syncedLyrics = body?.syncedLyrics,
            found = body != null,
            fetchedAt = System.currentTimeMillis(),
        )
        runCatching { lyricsDao.put(entity) }
        return FetchResult(entity, networkError = false)
    }
}

private const val HTTP_NOT_FOUND = 404
private const val NEGATIVE_CACHE_TTL_MS = 14L * 24 * 60 * 60 * 1000
