package io.github.wizard302.cardamom.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CardamomDatabase =
        Room.databaseBuilder(context, CardamomDatabase::class.java, "cardamom.db").build()

    @Provides
    fun providePlaylistDao(db: CardamomDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideFavoriteDao(db: CardamomDatabase): FavoriteDao = db.favoriteDao()
}
