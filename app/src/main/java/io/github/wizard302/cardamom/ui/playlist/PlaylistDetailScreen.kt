package io.github.wizard302.cardamom.ui.playlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.ui.library.TrackMenuAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onTrackMenuAction: (TrackMenuAction, Track) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val serverRows by viewModel.rows.collectAsStateWithLifecycle()

    var rows by remember { mutableStateOf(serverRows) }
    var draggingKey by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { PLAYLIST_ROW_HEIGHT.toPx() }

    // Mirror the DB rows locally, but freeze while a reorder drag is in flight.
    LaunchedEffect(serverRows) {
        if (draggingKey == null) rows = serverRows
    }

    var showMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(M3U_EXPORT_MIME_TYPE),
    ) { uri -> if (uri != null) viewModel.export(uri) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
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
                        text = playlist?.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = { viewModel.play(0) },
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text(stringResource(R.string.menu_play))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.action_more),
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename)) },
                                onClick = { showMenu = false; renaming = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_export)) },
                                onClick = {
                                    showMenu = false
                                    exportLauncher.launch("${playlist?.name.orEmpty()}.m3u8")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = { showMenu = false; deleting = true },
                            )
                        }
                    }
                }
            }

            if (rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.playlist_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            }

            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) {
                            viewModel.removeTrack(row.key)
                            true
                        } else {
                            false
                        }
                    },
                )
                val isDragged = draggingKey == row.key
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PLAYLIST_ROW_HEIGHT)
                                .background(MaterialTheme.colorScheme.errorContainer),
                        )
                    },
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .then(if (isDragged) Modifier else Modifier.animateItem())
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f },
                ) {
                    ResolvedRowWithMenu(
                        row = row,
                        onClick = {
                            val pos = rows.indexOfFirst { it.key == row.key }
                            if (pos >= 0) viewModel.play(pos)
                        },
                        onMenuAction = onTrackMenuAction,
                        removeLabel = stringResource(R.string.menu_remove_from_playlist),
                        onRemove = { viewModel.removeTrack(row.key) },
                        dragHandle = Modifier.pointerInput(row.key) {
                            var from = -1
                            detectDragGestures(
                                onDragStart = {
                                    from = rows.indexOfFirst { it.key == row.key }
                                    draggingKey = row.key
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    var idx = rows.indexOfFirst { it.key == row.key }
                                    while (dragOffset > rowHeightPx / 2 && idx < rows.lastIndex) {
                                        rows = rows.moved(idx, idx + 1)
                                        dragOffset -= rowHeightPx
                                        idx++
                                    }
                                    while (dragOffset < -rowHeightPx / 2 && idx > 0) {
                                        rows = rows.moved(idx, idx - 1)
                                        dragOffset += rowHeightPx
                                        idx--
                                    }
                                },
                                onDragEnd = {
                                    draggingKey = null
                                    dragOffset = 0f
                                    viewModel.persistOrder(rows.map { it.key })
                                },
                                onDragCancel = {
                                    draggingKey = null
                                    dragOffset = 0f
                                    rows = serverRows
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = stringResource(R.string.playlist_rename),
            confirmLabel = stringResource(R.string.action_rename),
            initial = playlist?.name.orEmpty(),
            onConfirm = { viewModel.rename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete),
            message = stringResource(R.string.playlist_delete_confirm, playlist?.name.orEmpty()),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(); deleting = false; onBack() },
            onDismiss = { deleting = false },
        )
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }

const val M3U_EXPORT_MIME_TYPE = "audio/x-mpegurl"
