package io.github.wizard302.cardamom.data.remote

import io.github.wizard302.cardamom.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(UserAgentInterceptor())
        .addInterceptor(RateLimitInterceptor(host = "musicbrainz.org"))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor { message ->
                        android.util.Log.d("CardamomHttp", message)
                    }.apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun retrofit(client: OkHttpClient, json: Json, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideMusicBrainzApi(client: OkHttpClient, json: Json): MusicBrainzApi =
        retrofit(client, json, "https://musicbrainz.org/ws/2/").create()

    @Provides
    @Singleton
    fun provideDeezerApi(client: OkHttpClient, json: Json): DeezerApi =
        retrofit(client, json, "https://api.deezer.com/").create()

    @Provides
    @Singleton
    fun provideImageApi(client: OkHttpClient, json: Json): ImageApi =
        retrofit(client, json, "https://coverartarchive.org/").create()

    @Provides
    @Singleton
    fun provideLrcLibApi(client: OkHttpClient, json: Json): LrcLibApi =
        retrofit(client, json, "https://lrclib.net/").create()
}
