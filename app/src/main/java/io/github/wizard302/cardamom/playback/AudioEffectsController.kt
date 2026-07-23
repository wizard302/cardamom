package io.github.wizard302.cardamom.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import io.github.wizard302.cardamom.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Immutable description of what the device's equalizer can do. */
data class EqualizerCapabilities(
    val bandCount: Int,
    val minLevelMillibel: Short,
    val maxLevelMillibel: Short,
    /** Centre frequency of each band, in Hz. */
    val centerFrequenciesHz: List<Int>,
    /** Built-in preset names, in index order. */
    val presetNames: List<String>,
    val bassBoostSupported: Boolean,
    val virtualizerSupported: Boolean,
)

/**
 * Owns the platform audio effects (Equalizer, BassBoost, Virtualizer) attached to
 * the ExoPlayer audio session. A [Singleton] so it outlives the playback service:
 * the desired state lives here (and in DataStore), and is (re)applied to the
 * hardware whenever the service (re)attaches a session.
 *
 * The UI reads [capabilities] and the state flows and drives the setters; every
 * change is applied to the live effect and persisted so it survives restarts.
 */
@Singleton
class AudioEffectsController @Inject constructor(
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var equalizer: Equalizer? = null
    private var bassBoostFx: BassBoost? = null
    private var virtualizerFx: Virtualizer? = null
    private var sessionId: Int? = null

    // Custom band levels persisted from a previous run, applied once a session with
    // a matching band count is attached.
    private var storedBands: List<Short> = emptyList()
    private var restored = false

    private val _capabilities = MutableStateFlow<EqualizerCapabilities?>(null)
    val capabilities: StateFlow<EqualizerCapabilities?> = _capabilities.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Selected preset index, or [PRESET_CUSTOM] when the bands were hand-tuned. */
    private val _preset = MutableStateFlow(PRESET_CUSTOM)
    val preset: StateFlow<Short> = _preset.asStateFlow()

    private val _bandLevels = MutableStateFlow<List<Short>>(emptyList())
    val bandLevels: StateFlow<List<Short>> = _bandLevels.asStateFlow()

    private val _bassBoost = MutableStateFlow<Short>(0)
    val bassBoost: StateFlow<Short> = _bassBoost.asStateFlow()

    private val _virtualizer = MutableStateFlow<Short>(0)
    val virtualizer: StateFlow<Short> = _virtualizer.asStateFlow()

    init {
        scope.launch {
            _enabled.value = settings.eqEnabled.first()
            _preset.value = settings.eqPreset.first().toShort()
            _bassBoost.value = settings.bassBoost.first().toShort()
            _virtualizer.value = settings.virtualizer.first().toShort()
            storedBands = parseBands(settings.eqBands.first())
            restored = true
            synchronized(this@AudioEffectsController) { applyAll() }
        }
    }

    /** Called by the playback service once it owns an audio session id. */
    @Synchronized
    fun attach(audioSessionId: Int) {
        if (sessionId == audioSessionId && equalizer != null) return
        release()
        sessionId = audioSessionId
        try {
            val eq = Equalizer(EFFECT_PRIORITY, audioSessionId)
            equalizer = eq
            val range = eq.bandLevelRange
            val bands = eq.numberOfBands.toInt()
            val presets = (0 until eq.numberOfPresets)
                .map { eq.getPresetName(it.toShort()) }
            val bass = runCatching { BassBoost(EFFECT_PRIORITY, audioSessionId) }.getOrNull()
            val virt = runCatching { Virtualizer(EFFECT_PRIORITY, audioSessionId) }.getOrNull()
            bassBoostFx = bass
            virtualizerFx = virt
            _capabilities.value = EqualizerCapabilities(
                bandCount = bands,
                minLevelMillibel = range[0],
                maxLevelMillibel = range[1],
                centerFrequenciesHz = (0 until bands)
                    .map { eq.getCenterFreq(it.toShort()) / 1000 },
                presetNames = presets,
                bassBoostSupported = bass?.strengthSupported == true,
                virtualizerSupported = virt?.strengthSupported == true,
            )
            // Seed band levels from persisted custom values when they still fit.
            _bandLevels.value =
                if (storedBands.size == bands) storedBands else readBands(eq)
            applyAll()
        } catch (_: Exception) {
            // Effects unsupported or busy; the UI shows a "not available" state.
            release()
        }
    }

    @Synchronized
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoostFx?.release() }
        runCatching { virtualizerFx?.release() }
        equalizer = null
        bassBoostFx = null
        virtualizerFx = null
        sessionId = null
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        synchronized(this) { applyAll() }
        scope.launch { settings.setEqEnabled(on) }
    }

    fun setPreset(index: Short) {
        _preset.value = index
        synchronized(this) {
            val eq = equalizer
            if (eq != null && index >= 0) {
                runCatching {
                    eq.usePreset(index)
                    _bandLevels.value = readBands(eq)
                }
            }
        }
        scope.launch {
            settings.setEqPreset(index.toInt())
            settings.setEqBands(_bandLevels.value.joinToString(","))
        }
    }

    fun setBandLevel(band: Int, levelMillibel: Short) {
        // Hand-tuning a band drops out of any named preset.
        _preset.value = PRESET_CUSTOM
        val updated = _bandLevels.value.toMutableList()
        if (band !in updated.indices) return
        updated[band] = levelMillibel
        _bandLevels.value = updated
        synchronized(this) {
            runCatching { equalizer?.setBandLevel(band.toShort(), levelMillibel) }
        }
        scope.launch {
            settings.setEqPreset(PRESET_CUSTOM.toInt())
            settings.setEqBands(updated.joinToString(","))
        }
    }

    fun setBassBoost(strength: Short) {
        _bassBoost.value = strength
        synchronized(this) {
            bassBoostFx?.let { fx ->
                runCatching {
                    fx.enabled = _enabled.value
                    fx.setStrength(strength)
                }
            }
        }
        scope.launch { settings.setBassBoost(strength.toInt()) }
    }

    fun setVirtualizer(strength: Short) {
        _virtualizer.value = strength
        synchronized(this) {
            virtualizerFx?.let { fx ->
                runCatching {
                    fx.enabled = _enabled.value
                    fx.setStrength(strength)
                }
            }
        }
        scope.launch { settings.setVirtualizer(strength.toInt()) }
    }

    /** Pushes the whole desired state onto the live effects. Caller holds the lock. */
    private fun applyAll() {
        if (!restored) return
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = _enabled.value
            val p = _preset.value
            val presetCount = _capabilities.value?.presetNames?.size ?: 0
            if (p in 0 until presetCount) {
                eq.usePreset(p)
                _bandLevels.value = readBands(eq)
            } else {
                _bandLevels.value.forEachIndexed { i, level ->
                    runCatching { eq.setBandLevel(i.toShort(), level) }
                }
            }
        }
        bassBoostFx?.let { fx ->
            runCatching {
                fx.enabled = _enabled.value
                fx.setStrength(_bassBoost.value)
            }
        }
        virtualizerFx?.let { fx ->
            runCatching {
                fx.enabled = _enabled.value
                fx.setStrength(_virtualizer.value)
            }
        }
    }

    private fun readBands(eq: Equalizer): List<Short> =
        (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()) }

    private fun parseBands(csv: String): List<Short> =
        csv.split(',').mapNotNull { it.trim().toShortOrNull() }

    companion object {
        const val PRESET_CUSTOM: Short = -1

        // Above 0 so our effect wins over lower-priority global effects.
        private const val EFFECT_PRIORITY = 1
    }
}
