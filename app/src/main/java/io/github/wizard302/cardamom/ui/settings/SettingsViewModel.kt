package io.github.wizard302.cardamom.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.playlist.M3uIo
import io.github.wizard302.cardamom.data.settings.ReplayGainMode
import io.github.wizard302.cardamom.data.settings.SettingsRepository
import io.github.wizard302.cardamom.data.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val m3uIo: M3uIo,
) : ViewModel() {

    /** One-off outcome of a folder playlist import, consumed by the UI snackbar. */
    sealed interface DeviceImport {
        data object Idle : DeviceImport
        data object Running : DeviceImport
        data class Done(val playlists: Int, val tracks: Int) : DeviceImport
    }

    private val _deviceImport = MutableStateFlow<DeviceImport>(DeviceImport.Idle)
    val deviceImport: StateFlow<DeviceImport> = _deviceImport.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val dynamicColor: StateFlow<Boolean> = repository.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val syncedLyricsHighlighting: StateFlow<Boolean> = repository.syncedLyricsHighlighting
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val pauseOnDisconnect: StateFlow<Boolean> = repository.pauseOnDisconnect
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val resumeOnConnect: StateFlow<Boolean> = repository.resumeOnConnect
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val rgMode: StateFlow<ReplayGainMode> = repository.rgMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReplayGainMode.OFF)

    val rgPreampDb: StateFlow<Float> = repository.rgPreampDb
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setSyncedLyricsHighlighting(enabled: Boolean) {
        viewModelScope.launch { repository.setSyncedLyricsHighlighting(enabled) }
    }

    fun setPauseOnDisconnect(enabled: Boolean) {
        viewModelScope.launch { repository.setPauseOnDisconnect(enabled) }
    }

    fun setResumeOnConnect(enabled: Boolean) {
        viewModelScope.launch { repository.setResumeOnConnect(enabled) }
    }

    fun setRgMode(mode: ReplayGainMode) {
        viewModelScope.launch { repository.setRgMode(mode) }
    }

    fun setRgPreampDb(db: Float) {
        viewModelScope.launch { repository.setRgPreampDb(db) }
    }

    fun rescanLibrary() = libraryRepository.refresh()

    fun importPlaylistsFromFolder(treeUri: Uri) {
        if (_deviceImport.value == DeviceImport.Running) return
        _deviceImport.value = DeviceImport.Running
        viewModelScope.launch {
            val result = m3uIo.importFolder(treeUri)
            _deviceImport.value = DeviceImport.Done(result.playlists, result.tracks)
        }
    }

    fun clearDeviceImport() {
        _deviceImport.value = DeviceImport.Idle
    }
}
