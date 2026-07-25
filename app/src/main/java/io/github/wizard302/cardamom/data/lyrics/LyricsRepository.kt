package io.github.wizard302.cardamom.data.lyrics

import android.net.Uri
import android.util.Log
import io.github.wizard302.cardamom.data.db.LyricsDao
import io.github.wizard302.cardamom.data.db.LyricsEntity
import io.github.wizard302.cardamom.data.remote.LrcLibApi
import io.github.wizard302.cardamom.data.remote.LrcLibResponse
import io.github.wizard302.cardamom.data.tags.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Resolved lyrics for a track. Either field may be null. */
data class Lyrics(
    val plain: String?,
    val synced: String?,
    /** True when LRCLIB could not be reached, as opposed to having no match. */
    val networkError: Boolean = false,
    /** True when [plain] came from the file's own tag rather than LRCLIB. */
    val plainFromFile: Boolean = false,
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
            plainFromFile = embeddedPlain != null,
        )
    }

    /**
     * Forces a fresh LRCLIB lookup (bypasses the cache) for manual re-search.
     * Goes through the fuzzy search endpoint: the exact one also has to match
     * the album and the track length, which an edited query rarely does.
     */
    suspend fun refetch(
        artist: String,
        title: String,
        album: String,
        durationMs: Long,
    ): Lyrics = withContext(Dispatchers.IO) {
        val result = searchAndCache(artist, title, (durationMs / 1000).toInt())
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
        // LRCLIB rejects a duration outside 1..3600 with HTTP 400, and the
        // player reports none until the track is prepared. Search by name
        // instead of turning that into a bogus "check your connection".
        if (durationSec !in 1..MAX_DURATION_SEC) {
            return searchAndCache(artist, title, durationSec)
        }

        val response = runCatching {
            lrcLibApi.get(artist, title, album, durationSec)
        }.getOrElse { return failure("lookup", it) }

        if (!response.isSuccessful && response.code() != HTTP_NOT_FOUND) {
            return failure("lookup", response.code())
        }
        val body = response.body().takeIf { response.isSuccessful }
        // The exact endpoint also demands a matching album and a length within
        // a couple of seconds, so a 404 usually means the tags disagree with
        // LRCLIB rather than that nobody transcribed the song. Fall back to the
        // fuzzy search the user would otherwise have to trigger by hand — but
        // keep the candidate close in length, since nothing else guards against
        // picking a cover or a live take with the same title.
        return body?.let { cache(artist, title, durationSec, it) }
            ?: searchAndCache(artist, title, durationSec, AUTO_MAX_DRIFT_SEC)
    }

    /**
     * Fuzzy lookup by artist and title; keeps the candidate closest to the
     * track. [maxDriftSec] bounds how far a candidate's length may be from the
     * track: unbounded for a manual search, where the user sees what was
     * applied, tight for the automatic fallback.
     */
    private suspend fun searchAndCache(
        artist: String,
        title: String,
        durationSec: Int,
        maxDriftSec: Int = Int.MAX_VALUE,
    ): FetchResult {
        // Both query fields empty make LRCLIB answer HTTP 400, which the panel
        // would report as a connection problem; there is nothing to search for.
        if (title.isBlank() && artist.isBlank()) {
            return FetchResult(null, networkError = false)
        }

        val response = runCatching {
            lrcLibApi.search(artist, title)
        }.getOrElse { return failure("search", it) }

        if (!response.isSuccessful) {
            return failure("search", response.code())
        }
        val match = response.body().orEmpty().bestMatch(durationSec, maxDriftSec)
        return cache(artist, title, durationSec, match)
    }

    /**
     * Prefers a candidate with synced lyrics, then the one whose length is
     * closest to the track being played. Entries without any text are useless
     * (LRCLIB lists instrumentals too), so they are dropped first, as is
     * anything further than [maxDriftSec] from the track. A candidate without a
     * length is only kept when no bound was asked for.
     */
    private fun List<LrcLibResponse>.bestMatch(
        durationSec: Int,
        maxDriftSec: Int,
    ): LrcLibResponse? =
        filterNot { it.plainLyrics.isNullOrBlank() && it.syncedLyrics.isNullOrBlank() }
            .map { it to it.driftFrom(durationSec) }
            .filter { (_, drift) -> drift <= maxDriftSec }
            .minWithOrNull(
                compareBy(
                    { (candidate, _) -> candidate.syncedLyrics.isNullOrBlank() },
                    { (_, drift) -> drift },
                ),
            )
            ?.first

    /** Length difference from the track, or [Int.MAX_VALUE] when unknown. */
    private fun LrcLibResponse.driftFrom(durationSec: Int): Int =
        duration?.toInt()?.let { abs(it - durationSec) } ?: Int.MAX_VALUE

    private suspend fun cache(
        artist: String,
        title: String,
        durationSec: Int,
        body: LrcLibResponse?,
    ): FetchResult {
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

    // The panel can only say "check your connection", so leave the real reason
    // in logcat — an HTTP status here means the request did reach LRCLIB.
    private fun failure(what: String, cause: Throwable): FetchResult {
        Log.w(TAG, "Lyrics $what failed", cause)
        return FetchResult(null, networkError = true)
    }

    private fun failure(what: String, httpCode: Int): FetchResult {
        Log.w(TAG, "Lyrics $what failed: HTTP $httpCode")
        return FetchResult(null, networkError = true)
    }
}

private const val TAG = "Cardamom"
private const val HTTP_NOT_FOUND = 404
private const val MAX_DURATION_SEC = 3600

/**
 * How far a fuzzy-search candidate's length may sit from the track when the
 * fallback runs unattended: wide enough for a different master or rip, narrow
 * enough to reject another song that happens to share artist and title.
 */
private const val AUTO_MAX_DRIFT_SEC = 15
private const val NEGATIVE_CACHE_TTL_MS = 14L * 24 * 60 * 60 * 1000
