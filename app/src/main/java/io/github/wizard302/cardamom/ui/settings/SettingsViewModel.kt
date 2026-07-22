package io.github.wizard302.cardamom.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.settings.SettingsRepository
import io.github.wizard302.cardamom.data.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

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

    fun rescanLibrary() = libraryRepository.refresh()
}
