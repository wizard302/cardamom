package io.github.wizard302.cardamom.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.wizard302.cardamom.data.media.Album
import io.github.wizard302.cardamom.data.media.Artist
import io.github.wizard302.cardamom.data.media.Track
import java.util.Locale

@Composable
fun TracksTab(
    tracks: List<Track>,
    emptyText: String,
    onTrackClick: (index: Int) -> Unit,
    onMenuAction: (TrackMenuAction, Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        CenteredMessage(emptyText, modifier)
        return
    }
    val listState = rememberLazyListState()
    FastScroll(
        listState = listState,
        itemCount = tracks.size,
        labelForIndex = { tracks[it].title.firstLetter() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    onClick = { onTrackClick(index) },
                    onMenuAction = onMenuAction,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onMenuAction: (TrackMenuAction, Track) -> Unit,
    showGoTo: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
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
            if (leading != null) leading() else ArtworkThumb(model = track.albumArtUri)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${track.artist} · ${track.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TrackContextMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            onAction = { onMenuAction(it, track) },
            offset = with(density) { DpOffset(pressPos.x.toDp(), pressPos.y.toDp()) },
            showGoTo = showGoTo,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsTab(
    albums: List<Album>,
    emptyText: String,
    onAlbumClick: (Album) -> Unit,
    onPlay: (Album) -> Unit,
    onPlayNext: (Album) -> Unit,
    onAddToQueue: (Album) -> Unit,
    onGoToArtist: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) {
        CenteredMessage(emptyText, modifier)
        return
    }
    val listState = rememberLazyListState()
    FastScroll(
        listState = listState,
        itemCount = albums.size,
        labelForIndex = { albums[it].title.firstLetter() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(albums, key = { _, album -> album.id }) { _, album ->
                var showMenu by remember { mutableStateOf(false) }
                var pressPos by remember { mutableStateOf(Offset.Zero) }
                val density = LocalDensity.current
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .reportPressPosition { pressPos = it }
                            .combinedClickable(
                                onClick = { onAlbumClick(album) },
                                onLongClick = { showMenu = true },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkThumb(model = album.artUri)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = album.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    CollectionContextMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onPlay = { onPlay(album) },
                        onPlayNext = { onPlayNext(album) },
                        onAddToQueue = { onAddToQueue(album) },
                        offset = with(density) { DpOffset(pressPos.x.toDp(), pressPos.y.toDp()) },
                        onGoToArtist = { onGoToArtist(album) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsTab(
    artists: List<Artist>,
    emptyText: String,
    onArtistClick: (Artist) -> Unit,
    onPlay: (Artist) -> Unit,
    onPlayNext: (Artist) -> Unit,
    onAddToQueue: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) {
        CenteredMessage(emptyText, modifier)
        return
    }
    val listState = rememberLazyListState()
    FastScroll(
        listState = listState,
        itemCount = artists.size,
        labelForIndex = { artists[it].name.firstLetter() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(artists, key = { _, artist -> artist.id }) { _, artist ->
                var showMenu by remember { mutableStateOf(false) }
                var pressPos by remember { mutableStateOf(Offset.Zero) }
                val density = LocalDensity.current
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .reportPressPosition { pressPos = it }
                            .combinedClickable(
                                onClick = { onArtistClick(artist) },
                                onLongClick = { showMenu = true },
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                        )
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${artist.albumCount} · ${artist.trackCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CollectionContextMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onPlay = { onPlay(artist) },
                        onPlayNext = { onPlayNext(artist) },
                        onAddToQueue = { onAddToQueue(artist) },
                        offset = with(density) { DpOffset(pressPos.x.toDp(), pressPos.y.toDp()) },
                    )
                }
            }
        }
    }
}

@Composable
fun PlaceholderTab(text: String, modifier: Modifier = Modifier) {
    CenteredMessage(text, modifier)
}

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ArtworkThumb(model: Any?, size: Int = 48) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // Fallback icon sits behind the image; visible when art fails to load.
        Icon(
            imageVector = Icons.Rounded.Album,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Center)
                .size((size / 2).dp),
        )
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp),
        )
    }
}

fun String.firstLetter(): String =
    firstOrNull()?.uppercaseChar()?.toString() ?: "#"

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
