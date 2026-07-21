package io.github.wizard302.cardamom.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.ui.library.ArtworkThumb
import io.github.wizard302.cardamom.ui.library.formatDuration

val PLAYLIST_ROW_HEIGHT = 64.dp

/** Row for a playlist/favorites entry. Missing (unresolved) files are dimmed. */
@Composable
fun ResolvedRowItem(
    row: ResolvedRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: Modifier? = null,
) {
    val alpha = if (row.track == null) 0.4f else 1f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PLAYLIST_ROW_HEIGHT)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
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
