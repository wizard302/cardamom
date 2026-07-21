package io.github.wizard302.cardamom.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** Deezer search (https://api.deezer.com) — no key; used as a cover-art fallback. */
interface DeezerApi {
    @GET("search")
    suspend fun search(@Query("q") query: String): DeezerSearch
}

@Serializable
data class DeezerSearch(val data: List<DeezerTrack> = emptyList())

@Serializable
data class DeezerTrack(
    val title: String = "",
    val artist: DeezerArtist = DeezerArtist(),
    val album: DeezerAlbum = DeezerAlbum(),
)

@Serializable
data class DeezerArtist(val name: String = "")

@Serializable
data class DeezerAlbum(
    val title: String = "",
    @SerialName("cover_xl") val coverXl: String? = null,
)
