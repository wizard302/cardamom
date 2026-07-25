package io.github.wizard302.cardamom.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MIN_ITEMS_FOR_SCROLLER = 50

/**
 * Wraps a LazyColumn with a draggable fast-scroll thumb on the right edge.
 * While dragging, a bubble shows [labelForIndex] (usually the first letter)
 * of the item the thumb points at. Hidden for short lists.
 */
@Composable
fun FastScroll(
    listState: LazyListState,
    itemCount: Int,
    labelForIndex: (Int) -> String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
        if (itemCount >= MIN_ITEMS_FOR_SCROLLER) {
            FastScrollRail(listState, itemCount, labelForIndex)
        }
    }
}

@Composable
private fun BoxScope.FastScrollRail(
    listState: LazyListState,
    itemCount: Int,
    labelForIndex: (Int) -> String,
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val thumbHeight = 56.dp
    val railWidth = 28.dp
    val bubbleSize = 64.dp

    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(railWidth),
    ) {
        val trackPx = with(density) { (maxHeight - thumbHeight).toPx() }.coerceAtLeast(1f)

        val listFraction by remember(itemCount) {
            derivedStateOf {
                val lastIndex = (itemCount - 1).coerceAtLeast(1)
                listState.firstVisibleItemIndex.toFloat() / lastIndex
            }
        }
        val fraction = (if (dragging) dragFraction else listFraction).coerceIn(0f, 1f)
        val targetIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(railWidth)
                .pointerInput(itemCount) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.y / trackPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.y / trackPx).coerceIn(0f, 1f)
                            val index = (dragFraction * (itemCount - 1))
                                .roundToInt()
                                .coerceIn(0, itemCount - 1)
                            scope.launch { listState.scrollToItem(index) }
                        },
                    )
                },
        ) {
            // Thumb
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, (fraction * trackPx).roundToInt()) }
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .width(5.dp)
                    .height(thumbHeight)
                    .background(
                        color = if (dragging) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        shape = RoundedCornerShape(3.dp),
                    ),
            )
        }

        if (dragging) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            with(density) { (-72).dp.roundToPx() },
                            // Centre the bubble on the thumb rather than aligning tops.
                            (fraction * trackPx).roundToInt() +
                                with(density) { ((thumbHeight - bubbleSize) / 2).roundToPx() },
                        )
                    }
                    .size(bubbleSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = labelForIndex(targetIndex),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
