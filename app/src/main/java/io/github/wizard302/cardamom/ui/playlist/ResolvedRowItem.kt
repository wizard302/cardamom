package io.github.wizard302.cardamom.ui.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.ui.library.ArtworkThumb
import io.github.wizard302.cardamom.ui.library.TrackContextMenu
import io.github.wizard302.cardamom.ui.library.TrackMenuAction
import io.github.wizard302.cardamom.ui.library.formatDuration
import io.github.wizard302.cardamom.ui.library.reportPressPosition

val PLAYLIST_ROW_HEIGHT = 64.dp

/**
 * [ResolvedRowItem] with the long-press context menu wired up. Rows whose file
 * is missing from the library only offer [removeLabel], since every track
 * action needs a resolved track.
 */
@Composable
fun ResolvedRowWithMenu(
    row: ResolvedRow,
    onClick: () -> Unit,
    onMenuAction: (TrackMenuAction, Track) -> Unit,
    removeLabel: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: Modifier? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    var pressPos by remember { mutableStateOf(Offset.Zero) }
    val menuOffset = with(LocalDensity.current) { DpOffset(pressPos.x.toDp(), pressPos.y.toDp()) }
    val removeItem: @Composable ColumnScope.() -> Unit = {
        DropdownMenuItem(
            text = { Text(removeLabel) },
            onClick = { showMenu = false; onRemove() },
        )
    }
    Box(modifier = modifier) {
        ResolvedRowItem(
            row = row,
            onClick = onClick,
            onLongClick = { showMenu = true },
            onPressPosition = { pressPos = it },
            dragHandle = dragHandle,
        )
        val track = row.track
        if (track != null) {
            TrackContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                onAction = { onMenuAction(it, track) },
                offset = menuOffset,
                extraItems = removeItem,
            )
        } else {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = menuOffset,
                content = removeItem,
            )
        }
    }
}

/** Row for a playlist/favorites entry. Missing (unresolved) files are dimmed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResolvedRowItem(
    row: ResolvedRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onPressPosition: (Offset) -> Unit = {},
    dragHandle: Modifier? = null,
) {
    val alpha = if (row.track == null) 0.4f else 1f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PLAYLIST_ROW_HEIGHT)
            .background(MaterialTheme.colorScheme.surface)
            .reportPressPosition(onPressPosition)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkThumb(model = row.albumArtUri, size = 48)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${row.artist} · ${row.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDuration(row.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (dragHandle != null) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = stringResource(R.string.queue_reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandle
                    .padding(start = 4.dp)
                    .size(24.dp),
            )
        }
    }
}
