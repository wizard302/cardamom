package io.github.wizard302.cardamom.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * LRCLIB (https://lrclib.net). Returns plain and/or synced (LRC) lyrics, or
 * HTTP 404 when nothing matches. Sends a User-Agent (global interceptor).
 */
interface LrcLibApi {
    @GET("api/get")
    suspend fun get(
        @Query("artist_name") artist: String,
        @Query("track_name") track: String,
        @Query("album_name") album: String,
        @Query("duration") duration: Int,
    ): Response<LrcLibResponse>

    /**
     * Fuzzy lookup: matches on artist and title alone and returns every
     * candidate. Unlike [get] it does not demand a matching album or a
     * duration within a couple of seconds, which is what a manual re-search
     * needs — the user is editing the query precisely because the exact
     * lookup missed.
     */
    @GET("api/search")
    suspend fun search(
        @Query("artist_name") artist: String,
        @Query("track_name") track: String,
    ): Response<List<LrcLibResponse>>
}

@Serializable
data class LrcLibResponse(
    /** Track length in seconds; used to pick the closest search candidate. */
    val duration: Double? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)
