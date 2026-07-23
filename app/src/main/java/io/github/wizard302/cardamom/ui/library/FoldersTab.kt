package io.github.wizard302.cardamom.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.wizard302.cardamom.data.media.Track

/**
 * Folder browser derived from MediaStore file paths (no direct File walking).
 * Shows subfolders and tracks of the current folder with breadcrumb navigation.
 */
@Composable
fun FoldersTab(
    tracks: List<Track>,
    emptyText: String,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (List<Track>) -> Unit,
    onAddToQueue: (List<Track>) -> Unit,
    onAddToPlaylist: (String, List<Track>) -> Unit,
    onMenuAction: (TrackMenuAction, Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        CenteredMessage(emptyText, modifier)
        return
    }

    val rootPath = remember(tracks) { commonRoot(tracks.map { it.parentDir }) }
    var currentPath by rememberSaveable { mutableStateOf<String?>(null) }
    val path = currentPath ?: rootPath

    // Back inside the folder tree walks one level up before leaving the screen.
    BackHandler(enabled = path != rootPath) {
        currentPath = path.substringBeforeLast('/').takeIf { it.length >= rootPath.length }
    }

    val subfolders = remember(tracks, path) {
        tracks.asSequence()
            .filter { it.parentDir.startsWith("$path/") }
            .map { it.parentDir.removePrefix("$path/").substringBefore('/') }
            .groupingBy { it }
            .eachCount()
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    val folderTracks = remember(tracks, path) {
        tracks.filter { it.parentDir == path }.sortedBy { it.title.lowercase() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Breadcrumbs(
            rootPath = rootPath,
            currentPath = path,
            onNavigate = { currentPath = it },
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(subfolders.keys.toList(), key = { "dir:$it" }) { name ->
                FolderRow(
                    name = name,
                    trackCount = subfolders[name] ?: 0,
                    tracksProvider = {
                        tracks.filter { it.parentDir.startsWith("$path/$name") }
                            .sortedBy { it.path.lowercase() }
                    },
                    onClick = { currentPath = "$path/$name" },
                    onPlay = { onPlay(it, 0) },
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = { onAddToPlaylist(name, it) },
                    onMenuAction = onMenuAction,
                )
            }
            itemsIndexed(folderTracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    track = track,
                    onClick = { onPlay(folderTracks, index) },
                    onMenuAction = onMenuAction,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    name: String,
    trackCount: Int,
    tracksProvider: () -> List<Track>,
    onClick: () -> Unit,
    onPlay: (List<Track>) -> Unit,
    onPlayNext: (List<Track>) -> Unit,
    onAddToQueue: (List<Track>) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    onMenuAction: (TrackMenuAction, Track) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var pressPos by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .reportPressPosition { pressPos = it }
                .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Text(
                text = trackCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CollectionContextMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            onPlay = { onPlay(tracksProvider()) },
            onPlayNext = { onPlayNext(tracksProvider()) },
            onAddToQueue = { onAddToQueue(tracksProvider()) },
            onAddToPlaylist = { onAddToPlaylist(tracksProvider()) },
            offset = with(density) { DpOffset(pressPos.x.toDp(), pressPos.y.toDp()) },
        )
    }
}

@Composable
private fun Breadcrumbs(
    rootPath: String,
    currentPath: String,
    onNavigate: (String) -> Unit,
) {
    val rootName = rootPath.substringAfterLast('/').ifEmpty { "/" }
    val relative = currentPath.removePrefix(rootPath).trim('/')
    val segments = if (relative.isEmpty()) emptyList() else relative.split('/')

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CrumbText(text = rootName, active = segments.isEmpty()) { onNavigate(rootPath) }
        var acc = rootPath
        segments.forEachIndexed { i, segment ->
            acc += "/$segment"
            val target = acc
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            CrumbText(text = segment, active = i == segments.lastIndex) { onNavigate(target) }
        }
    }
}

@Composable
private fun CrumbText(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        maxLines = 1,
    )
}

private val Track.parentDir: String
    get() = path.substringBeforeLast('/', "")

private fun commonRoot(dirs: List<String>): String {
    if (dirs.isEmpty()) return "/"
    var prefix = dirs.first().split('/')
    for (dir in dirs) {
        val parts = dir.split('/')
        var i = 0
        while (i < minOf(prefix.size, parts.size) && prefix[i] == parts[i]) i++
        prefix = prefix.subList(0, i)
    }
    return prefix.joinToString("/").ifEmpty { "/" }
}
