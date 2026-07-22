package io.github.wizard302.cardamom.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val syncedLyricsKey = booleanPreferencesKey("synced_lyrics_highlighting")
    private val trackSortKey = stringPreferencesKey("track_sort")
    private val albumSortKey = stringPreferencesKey("album_sort")
    private val artistSortKey = stringPreferencesKey("artist_sort")

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[themeModeKey] } ?: ThemeMode.SYSTEM
    }

    /** "Synced lyrics highlighting" — karaoke mode; on by default. */
    val syncedLyricsHighlighting: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[syncedLyricsKey] ?: true
    }

    val trackSort: Flow<TrackSort> = context.settingsDataStore.data.map { prefs ->
        TrackSort.entries.firstOrNull { it.name == prefs[trackSortKey] } ?: TrackSort.TITLE
    }

    val albumSort: Flow<AlbumSort> = context.settingsDataStore.data.map { prefs ->
        AlbumSort.entries.firstOrNull { it.name == prefs[albumSortKey] } ?: AlbumSort.TITLE
    }

    val artistSort: Flow<ArtistSort> = context.settingsDataStore.data.map { prefs ->
        ArtistSort.entries.firstOrNull { it.name == prefs[artistSortKey] } ?: ArtistSort.NAME
    }

    suspend fun setTrackSort(sort: TrackSort) {
        context.settingsDataStore.edit { it[trackSortKey] = sort.name }
    }

    suspend fun setAlbumSort(sort: AlbumSort) {
        context.settingsDataStore.edit { it[albumSortKey] = sort.name }
    }

    suspend fun setArtistSort(sort: ArtistSort) {
        context.settingsDataStore.edit { it[artistSortKey] = sort.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setSyncedLyricsHighlighting(enabled: Boolean) {
        context.settingsDataStore.edit { it[syncedLyricsKey] = enabled }
    }
}
