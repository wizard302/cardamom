package io.github.wizard302.cardamom.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.ui.library.formatDuration

private val PresetMinutes = listOf(15, 30, 45, 60)

/**
 * Sleep timer dialog: presets, "end of track", a free-form minutes field and a
 * cancel row shown only while a timer is armed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(
    remainingMs: Long?,
    afterTrack: Boolean,
    onStart: (Int) -> Unit,
    onStartAfterTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    var customMinutes by remember { mutableStateOf("") }
    val custom = customMinutes.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer_title)) },
        text = {
            Column {
                if (remainingMs != null) {
                    Text(
                        text = stringResource(
                            R.string.sleep_timer_remaining,
                            formatDuration(remainingMs),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                } else if (afterTrack) {
                    Text(
                        text = stringResource(R.string.sleep_timer_end_of_track_active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetMinutes.forEach { minutes ->
                        AssistChip(
                            onClick = {
                                onStart(minutes)
                                onDismiss()
                            },
                            label = {
                                Text(stringResource(R.string.sleep_timer_minutes, minutes))
                            },
                        )
                    }
                    AssistChip(
                        onClick = {
                            onStartAfterTrack()
                            onDismiss()
                        },
                        label = { Text(stringResource(R.string.sleep_timer_end_of_track)) },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { input ->
                            customMinutes = input.filter { it.isDigit() }.take(4)
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.sleep_timer_custom)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            custom?.let(onStart)
                            onDismiss()
                        },
                        enabled = custom != null && custom > 0,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.sleep_timer_start))
                    }
                }
            }
        },
        confirmButton = {
            if (remainingMs != null || afterTrack) {
                TextButton(
                    onClick = {
                        onCancelTimer()
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.sleep_timer_cancel))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
