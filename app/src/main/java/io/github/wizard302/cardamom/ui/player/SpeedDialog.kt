package io.github.wizard302.cardamom.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.playback.MAX_SPEED
import io.github.wizard302.cardamom.playback.MIN_SPEED
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SpeedPresets = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** 0.5–2.0 in 0.05 steps; Slider wants the number of intervals between them. */
private const val SPEED_STEP = 0.05f
private val SLIDER_STEPS = ((MAX_SPEED - MIN_SPEED) / SPEED_STEP).roundToInt() - 1

/** "1.0×" for round tenths, "1.25×" for the finer slider steps. */
fun formatSpeed(speed: Float): String {
    val hundredths = (speed * 100).roundToInt()
    val pattern = if (hundredths % 10 == 0) "%.1f×" else "%.2f×"
    return String.format(Locale.US, pattern, hundredths / 100f)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedDialog(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    // Dragging drives a local value so the slider stays smooth; every change is
    // pushed to the player right away — the effect is meant to be audible live.
    var draft by remember { mutableFloatStateOf(speed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speed_title)) },
        text = {
            Column {
                Text(
                    text = formatSpeed(draft),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Slider(
                    value = draft,
                    onValueChange = {
                        draft = it
                        onSpeedChange(it)
                    },
                    valueRange = MIN_SPEED..MAX_SPEED,
                    steps = SLIDER_STEPS,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    SpeedPresets.forEach { preset ->
                        FilterChip(
                            selected = abs(draft - preset) < 0.001f,
                            onClick = {
                                draft = preset
                                onSpeedChange(preset)
                            },
                            label = { Text(formatSpeed(preset)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
