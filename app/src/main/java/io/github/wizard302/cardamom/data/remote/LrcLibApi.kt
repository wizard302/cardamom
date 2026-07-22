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
}

@Serializable
data class LrcLibResponse(
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)
