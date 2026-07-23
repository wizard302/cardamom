package io.github.wizard302.cardamom.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val pauseOnDisconnectKey = booleanPreferencesKey("pause_on_disconnect")
    private val resumeOnConnectKey = booleanPreferencesKey("resume_on_connect")
    private val trackSortKey = stringPreferencesKey("track_sort")
    private val albumSortKey = stringPreferencesKey("album_sort")
    private val artistSortKey = stringPreferencesKey("artist_sort")
    private val eqEnabledKey = booleanPreferencesKey("eq_enabled")
    private val eqPresetKey = intPreferencesKey("eq_preset")
    private val eqBandsKey = stringPreferencesKey("eq_bands")
    private val bassBoostKey = intPreferencesKey("eq_bass_boost")
    private val virtualizerKey = intPreferencesKey("eq_virtualizer")
    private val excludedFoldersKey = stringSetPreferencesKey("excluded_folders")

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[themeModeKey] } ?: ThemeMode.SYSTEM
    }

    /** "Synced lyrics highlighting" — karaoke mode; on by default. */
    val syncedLyricsHighlighting: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[syncedLyricsKey] ?: true
    }

    /** Material You colors on API 31+; ignored below that. */
    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: true
    }

    /** Pause when headphones are unplugged or Bluetooth audio drops. On by default. */
    val pauseOnDisconnect: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[pauseOnDisconnectKey] ?: true
    }

    /** Resume playback when wired headphones are plugged back in. Off by default. */
    val resumeOnConnect: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[resumeOnConnectKey] ?: false
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setPauseOnDisconnect(enabled: Boolean) {
        context.settingsDataStore.edit { it[pauseOnDisconnectKey] = enabled }
    }

    suspend fun setResumeOnConnect(enabled: Boolean) {
        context.settingsDataStore.edit { it[resumeOnConnectKey] = enabled }
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

    /**
     * Equalizer state. Preset -1 means "custom"; band levels are a comma-separated
     * list of per-band gains in millibel whose length matches the device's band
     * count (ignored on restore if the device reports a different count). Bass boost
     * and virtualizer strengths are 0..1000.
     */
    val eqEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[eqEnabledKey] ?: false }
    val eqPreset: Flow<Int> = context.settingsDataStore.data.map { it[eqPresetKey] ?: -1 }
    val eqBands: Flow<String> = context.settingsDataStore.data.map { it[eqBandsKey] ?: "" }
    val bassBoost: Flow<Int> = context.settingsDataStore.data.map { it[bassBoostKey] ?: 0 }
    val virtualizer: Flow<Int> = context.settingsDataStore.data.map { it[virtualizerKey] ?: 0 }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[eqEnabledKey] = enabled }
    }

    suspend fun setEqPreset(preset: Int) {
        context.settingsDataStore.edit { it[eqPresetKey] = preset }
    }

    suspend fun setEqBands(bands: String) {
        context.settingsDataStore.edit { it[eqBandsKey] = bands }
    }

    suspend fun setBassBoost(strength: Int) {
        context.settingsDataStore.edit { it[bassBoostKey] = strength }
    }

    suspend fun setVirtualizer(strength: Int) {
        context.settingsDataStore.edit { it[virtualizerKey] = strength }
    }

    /**
     * Absolute folder paths excluded from library scanning. Empty means "scan
     * everything" (the default), so newly added folders are included until the
     * user opts them out. A track is skipped when its file sits under any of these.
     */
    val excludedFolders: Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        prefs[excludedFoldersKey] ?: emptySet()
    }

    suspend fun setExcludedFolders(folders: Set<String>) {
        context.settingsDataStore.edit { it[excludedFoldersKey] = folders }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setSyncedLyricsHighlighting(enabled: Boolean) {
        context.settingsDataStore.edit { it[syncedLyricsKey] = enabled }
    }
}
