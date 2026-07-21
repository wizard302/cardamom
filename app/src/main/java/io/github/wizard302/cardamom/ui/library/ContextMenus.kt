package io.github.wizard302.cardamom.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.media.Track
import java.util.Locale

enum class TrackMenuAction {
    PLAY, PLAY_NEXT, ADD_TO_QUEUE, ADD_TO_PLAYLIST, GO_TO_ARTIST, GO_TO_ALBUM, DETAILS
}

/**
 * Observes the latest pointer-down position on the Initial pass (without
 * consuming) so a long-press context menu can open at the finger instead of
 * at the row's leading edge. Keeps [combinedClickable]'s ripple and callbacks.
 */
fun Modifier.reportPressPosition(onPosition: (Offset) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull()?.let { onPosition(it.position) }
            }
        }
    }

/** Context menu shown on long-press of a track row. */
@Composable
fun TrackContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (TrackMenuAction) -> Unit,
    offset: DpOffset = DpOffset.Zero,
    showGoTo: Boolean = true,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, offset = offset) {
        MenuItem(R.string.menu_play) { onAction(TrackMenuAction.PLAY); onDismiss() }
        MenuItem(R.string.menu_play_next) { onAction(TrackMenuAction.PLAY_NEXT); onDismiss() }
        MenuItem(R.string.menu_add_to_queue) { onAction(TrackMenuAction.ADD_TO_QUEUE); onDismiss() }
        MenuItem(R.string.menu_add_to_playlist) { onAction(TrackMenuAction.ADD_TO_PLAYLIST); onDismiss() }
        if (showGoTo) {
            MenuItem(R.string.menu_go_to_artist) { onAction(TrackMenuAction.GO_TO_ARTIST); onDismiss() }
            MenuItem(R.string.menu_go_to_album) { onAction(TrackMenuAction.GO_TO_ALBUM); onDismiss() }
        }
        MenuItem(R.string.menu_details) { onAction(TrackMenuAction.DETAILS); onDismiss() }
    }
}

/** Context menu for album/artist rows: play semantics over the whole collection. */
@Composable
fun CollectionContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    onGoToArtist: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, offset = offset) {
        MenuItem(R.string.menu_play) { onPlay(); onDismiss() }
        MenuItem(R.string.menu_play_next) { onPlayNext(); onDismiss() }
        MenuItem(R.string.menu_add_to_queue) { onAddToQueue(); onDismiss() }
        if (onGoToArtist != null) {
            MenuItem(R.string.menu_go_to_artist) { onGoToArtist(); onDismiss() }
        }
    }
}

@Composable
private fun MenuItem(textRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(textRes)) }, onClick = onClick)
}

@Composable
fun TrackDetailsDialog(track: Track, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        title = { Text(track.title) },
        text = {
            Column {
                DetailRow(stringResource(R.string.details_artist), track.artist)
                DetailRow(stringResource(R.string.details_album), track.album)
                if (track.year > 0) {
                    DetailRow(stringResource(R.string.details_year), track.year.toString())
                }
                DetailRow(stringResource(R.string.details_duration), formatDuration(track.durationMs))
                DetailRow(
                    stringResource(R.string.details_size),
                    String.format(Locale.US, "%.1f MB", track.sizeBytes / 1_048_576.0),
                )
                if (track.bitrate > 0) {
                    DetailRow(
                        stringResource(R.string.details_bitrate),
                        stringResource(R.string.details_kbps, track.bitrate / 1000),
                    )
                }
                DetailRow(stringResource(R.string.details_path), track.path)
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
