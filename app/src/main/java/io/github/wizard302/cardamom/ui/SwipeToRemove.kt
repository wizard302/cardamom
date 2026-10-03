package io.github.wizard302.cardamom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Shows "[message] · [actionLabel]" for a removal and calls [onUndo] if the
 * action is tapped. A newer removal replaces the snackbar of an older one.
 */
suspend fun SnackbarHostState.showUndo(
    message: String,
    actionLabel: String,
    onUndo: () -> Unit,
) {
    currentSnackbarData?.dismiss()
    val result = showSnackbar(
        message = message,
        actionLabel = actionLabel,
        duration = SnackbarDuration.Short,
    )
    if (result == SnackbarResult.ActionPerformed) onUndo()
}

/** Share of the row width a swipe must cover before letting go removes it. */
private const val REMOVE_THRESHOLD = 0.5f

/**
 * A row that is removed by swiping it right-to-left past half its width.
 *
 * Unlike Material's SwipeToDismissBox, a fast flick does not count: only the
 * distance at release decides, so a stray swipe while scrolling springs back.
 * A haptic tick marks the point past which letting go removes the row.
 */
@Composable
fun SwipeToRemove(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentOnRemove by rememberUpdatedState(onRemove)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val threshold = width * REMOVE_THRESHOLD
    val armed = width > 0 && -offset.value >= threshold

    val dragState = rememberDraggableState { delta ->
        val wasArmed = -offset.value >= threshold
        val next = (offset.value + delta).coerceIn(-width.toFloat(), 0f)
        scope.launch { offset.snapTo(next) }
        if (width > 0 && wasArmed != (-next >= threshold)) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    val background by animateColorAsState(
        if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer,
        label = "swipeBackground",
    )
    val iconTint by animateColorAsState(
        if (armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer,
        label = "swipeIcon",
    )

    Box(modifier = modifier.onSizeChanged { width = it.width }) {
        if (offset.value < 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(background),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offset.value }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        if (width > 0 && -offset.value >= threshold) {
                            offset.animateTo(-width.toFloat())
                            currentOnRemove()
                        } else {
                            offset.animateTo(0f)
                        }
                    },
                ),
        ) {
            content()
        }
    }
}
