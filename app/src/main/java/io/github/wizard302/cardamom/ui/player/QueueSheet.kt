package io.github.wizard302.cardamom.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wizard302.cardamom.R

private val QUEUE_ROW_HEIGHT = 56.dp

/**
 * Bottom sheet with the playback queue: tap to jump, drag the handle to
 * reorder, swipe a row away to remove it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()

    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { QUEUE_ROW_HEIGHT.toPx() }
    val listState = rememberLazyListState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.queue_title, queue.size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState) {
            itemsIndexed(queue) { index, item ->
                // Positional identity: the queue may contain the same track twice,
                // so mediaId alone is not unique. Recreating row state on index
                // change also resets swipe state after reorders/removals.
                key("$index:${item.mediaId}") {
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled) {
                                viewModel.removeQueueItem(index)
                            }
                            value != SwipeToDismissBoxValue.Settled
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(QUEUE_ROW_HEIGHT)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                            )
                        },
                        modifier = Modifier
                            .zIndex(if (dragIndex == index) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragIndex == index) dragOffset else 0f
                            },
                    ) {
                        QueueRow(
                            title = item.mediaMetadata.title?.toString().orEmpty(),
                            artist = item.mediaMetadata.artist?.toString().orEmpty(),
                            isCurrent = index == currentIndex,
                            onClick = { viewModel.seekToQueueItem(index) },
                            dragHandle = { handleModifier(index, queue.size, rowHeightPx, viewModel) { i, o ->
                                dragIndex = i
                                dragOffset = o
                            } },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Builds the drag-gesture modifier for a row's handle. Swaps queue items each
 * time the accumulated offset crosses half a row height.
 */
private fun handleModifier(
    index: Int,
    count: Int,
    rowHeightPx: Float,
    viewModel: PlayerViewModel,
    update: (dragIndex: Int, dragOffset: Float) -> Unit,
): Modifier = Modifier.pointerInput(index, count) {
    var localIndex = index
    var localOffset = 0f
    detectDragGestures(
        onDragStart = {
            localIndex = index
            localOffset = 0f
            update(localIndex, localOffset)
        },
        onDrag = { change, amount ->
            change.consume()
            localOffset += amount.y
            while (localOffset > rowHeightPx / 2 && localIndex < count - 1) {
                viewModel.moveQueueItem(localIndex, localIndex + 1)
                localIndex++
                localOffset -= rowHeightPx
            }
            while (localOffset < -rowHeightPx / 2 && localIndex > 0) {
                viewModel.moveQueueItem(localIndex, localIndex - 1)
                localIndex--
                localOffset += rowHeightPx
            }
            update(localIndex, localOffset)
        },
        onDragEnd = { update(-1, 0f) },
        onDragCancel = { update(-1, 0f) },
    )
}

@Composable
private fun QueueRow(
    title: String,
    artist: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    dragHandle: () -> Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(QUEUE_ROW_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
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
            modifier = Modifier.then(dragHandle()),
        )
    }
}
