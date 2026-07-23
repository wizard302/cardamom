package io.github.wizard302.cardamom.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.playback.AudioEffectsController.Companion.PRESET_CUSTOM
import io.github.wizard302.cardamom.playback.EqualizerCapabilities
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = stringResource(R.string.equalizer_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        val caps = capabilities
        if (caps == null) {
            Text(
                text = stringResource(R.string.equalizer_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 48.dp),
            )
            return
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.equalizer_enable),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = viewModel::setEnabled)
        }

        PresetSelector(viewModel = viewModel, capabilities = caps)

        BandSliders(viewModel = viewModel, capabilities = caps)

        if (caps.bassBoostSupported) {
            val strength by viewModel.bassBoost.collectAsStateWithLifecycle()
            StrengthRow(
                label = stringResource(R.string.equalizer_bass_boost),
                strength = strength,
                onStrength = viewModel::setBassBoost,
            )
        }
        if (caps.virtualizerSupported) {
            val strength by viewModel.virtualizer.collectAsStateWithLifecycle()
            StrengthRow(
                label = stringResource(R.string.equalizer_virtualizer),
                strength = strength,
                onStrength = viewModel::setVirtualizer,
            )
        }

        Spacer(modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun PresetSelector(viewModel: EqualizerViewModel, capabilities: EqualizerCapabilities) {
    val preset by viewModel.preset.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = capabilities.presetNames.getOrNull(preset.toInt())
        ?: stringResource(R.string.equalizer_custom)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.equalizer_preset),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(currentLabel)
                Icon(imageVector = Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                capabilities.presetNames.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            expanded = false
                            viewModel.setPreset(index.toShort())
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BandSliders(viewModel: EqualizerViewModel, capabilities: EqualizerCapabilities) {
    val levels by viewModel.bandLevels.collectAsStateWithLifecycle()
    capabilities.centerFrequenciesHz.forEachIndexed { index, hz ->
        val level = levels.getOrElse(index) { 0 }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = freqLabel(hz),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(56.dp),
            )
            Slider(
                value = level.toFloat(),
                onValueChange = {
                    viewModel.setBandLevel(index, it.roundToInt().toShort())
                },
                valueRange = capabilities.minLevelMillibel.toFloat()..
                    capabilities.maxLevelMillibel.toFloat(),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = gainLabel(level),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(52.dp),
            )
        }
    }
}

@Composable
private fun StrengthRow(label: String, strength: Short, onStrength: (Short) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = strength.toFloat(),
            onValueChange = { onStrength(it.roundToInt().toShort()) },
            valueRange = 0f..1000f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun freqLabel(hz: Int): String =
    if (hz >= 1000) {
        stringResource(R.string.eq_khz, formatNumber(hz / 1000.0))
    } else {
        stringResource(R.string.eq_hz, hz.toString())
    }

@Composable
private fun gainLabel(millibel: Short): String =
    stringResource(R.string.eq_db, String.format(Locale.US, "%+d", millibel / 100))

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
