package io.github.wizard302.cardamom.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.wizard302.cardamom.playback.AudioEffectsController
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val effects: AudioEffectsController,
) : ViewModel() {

    val capabilities = effects.capabilities
    val enabled: StateFlow<Boolean> = effects.enabled
    val preset: StateFlow<Short> = effects.preset
    val bandLevels: StateFlow<List<Short>> = effects.bandLevels
    val bassBoost: StateFlow<Short> = effects.bassBoost
    val virtualizer: StateFlow<Short> = effects.virtualizer

    fun setEnabled(on: Boolean) = effects.setEnabled(on)
    fun setPreset(index: Short) = effects.setPreset(index)
    fun setBandLevel(band: Int, levelMillibel: Short) = effects.setBandLevel(band, levelMillibel)
    fun setBassBoost(strength: Short) = effects.setBassBoost(strength)
    fun setVirtualizer(strength: Short) = effects.setVirtualizer(strength)
}
