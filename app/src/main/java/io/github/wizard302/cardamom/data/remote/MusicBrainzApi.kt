package io.github.wizard302.cardamom.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * MusicBrainz web service (https://musicbrainz.org/ws/2/). JSON via fmt=json,
 * rate-limited to 1 req/s and sent with a real User-Agent (see interceptors).
 */
interface MusicBrainzApi {

    @GET("recording")
    suspend fun searchRecordings(
        @Query("query") query: String,
        @Query("fmt") fmt: String = "json",
        @Query("limit") limit: Int = 8,
    ): MbRecordingSearch

    @GET("release")
    suspend fun searchReleases(
        @Query("query") query: String,
        @Query("fmt") fmt: String = "json",
        @Query("limit") limit: Int = 8,
    ): MbReleaseSearch

    @GET("release/{id}")
    suspend fun getRelease(
        @Path("id") id: String,
        @Query("inc") inc: String = "recordings+artist-credits",
        @Query("fmt") fmt: String = "json",
    ): MbReleaseFull
}

@Serializable
data class MbRecordingSearch(val recordings: List<MbRecording> = emptyList())

@Serializable
data class MbRecording(
    val id: String,
    val title: String = "",
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    val releases: List<MbRelease> = emptyList(),
)

@Serializable
data class MbArtistCredit(
    val name: String = "",
    val joinphrase: String = "",
)

@Serializable
data class MbRelease(
    val id: String,
    val title: String = "",
    val date: String? = null,
)

@Serializable
data class MbReleaseSearch(val releases: List<MbReleaseFull> = emptyList())

@Serializable
data class MbReleaseFull(
    val id: String,
    val title: String = "",
    val date: String? = null,
    @SerialName("track-count") val trackCount: Int = 0,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    val media: List<MbMedium> = emptyList(),
)

@Serializable
data class MbMedium(val tracks: List<MbTrack> = emptyList())

@Serializable
data class MbTrack(
    val position: Int = 0,
    val title: String = "",
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
)

/** Joins an artist-credit list into a display string using its join phrases. */
fun List<MbArtistCredit>.displayName(): String =
    joinToString("") { it.name + it.joinphrase }

/** Extracts the four-digit year from an MB date like "1979-11-30". */
fun String?.toYear(): String = this?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty()
