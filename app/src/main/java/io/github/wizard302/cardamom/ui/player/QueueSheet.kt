package io.github.wizard302.cardamom.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.ui.SwipeToRemove

private val QUEUE_ROW_HEIGHT = 64.dp

/**
 * One queue row. [id] is the media id plus its occurrence count, so it survives
 * reordering and removal of other rows; [index] is the timeline index the
 * player understands.
 */
private data class QueueEntry(
    val id: String,
    val index: Int,
    val item: MediaItem,
    val isCurrent: Boolean,
)

/**
 * Bottom sheet with the playback queue: tap to jump, drag the handle to
 * reorder, swipe a row far to the left to remove it.
 * With shuffle on, rows follow the shuffled play order.
 *
 * Reordering happens on a local copy while dragging and is committed to the
 * player once, on drag end. Mutating the player on every threshold crossing
 * churns the queue flow and makes the list jump, so we avoid it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val flowQueue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()

    var entries by remember { mutableStateOf<List<QueueEntry>>(emptyList()) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { QUEUE_ROW_HEIGHT.toPx() }

    // Rebuild the local list from the player, but never while a drag is in flight.
    LaunchedEffect(flowQueue, currentIndex) {
        if (draggingId == null) {
            val seen = HashMap<String, Int>()
            entries = flowQueue.map { slot ->
                val mediaId = slot.item.mediaId
                val n = (seen[mediaId] ?: 0) + 1
                seen[mediaId] = n
                QueueEntry(
                    id = "$mediaId#$n",
                    index = slot.index,
                    item = slot.item,
                    isCurrent = slot.index == currentIndex,
                )
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.queue_title, entries.size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(entries, key = { it.id }) { entry ->
                val isDragged = draggingId == entry.id
                SwipeToRemove(
                    onRemove = { viewModel.removeQueueItem(entry.index) },
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        // Displaced rows glide to their new slot; the dragged row
                        // follows the finger via translationY instead.
                        .then(if (isDragged) Modifier else Modifier.animateItem())
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f },
                ) {
                    QueueRow(
                        title = entry.item.mediaMetadata.title?.toString().orEmpty(),
                        artist = entry.item.mediaMetadata.artist?.toString().orEmpty(),
                        isCurrent = entry.isCurrent,
                        onClick = { viewModel.seekToQueueItem(entry.index) },
                        dragModifier = Modifier.pointerInput(entry.id) {
                            var from = -1
                            detectDragGestures(
                                onDragStart = {
                                    from = entries.indexOfFirst { it.id == entry.id }
                                    draggingId = entry.id
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    var idx = entries.indexOfFirst { it.id == entry.id }
                                    while (dragOffset > rowHeightPx / 2 && idx < entries.lastIndex) {
                                        entries = entries.moved(idx, idx + 1)
                                        dragOffset -= rowHeightPx
                                        idx++
                                    }
                                    while (dragOffset < -rowHeightPx / 2 && idx > 0) {
                                        entries = entries.moved(idx, idx - 1)
                                        dragOffset += rowHeightPx
                                        idx--
                                    }
                                },
                                onDragEnd = {
                                    val to = entries.indexOfFirst { it.id == entry.id }
                                    if (from >= 0 && to >= 0 && from != to) {
                                        viewModel.moveQueueItem(from, to)
                                    }
                                    draggingId = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }

@Composable
private fun QueueRow(
    title: String,
    artist: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    dragModifier: Modifier,
) {
    val background = if (isCurrent) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(QUEUE_ROW_HEIGHT)
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            Icon(
                imageVector = Icons.Rounded.VolumeUp,
                contentDescription = stringResource(R.string.queue_now_playing),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.queue_reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragModifier
                .padding(8.dp)
                .size(24.dp),
        )
    }
}
