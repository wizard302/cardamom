package io.github.wizard302.cardamom.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/** Fetches raw image bytes from an absolute URL (Cover Art Archive, Deezer). */
interface ImageApi {
    @Streaming
    @GET
    suspend fun fetch(@Url url: String): ResponseBody
}
