package io.github.wizard302.cardamom.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.media.Track

@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    onTrackMenuAction: (TrackMenuAction, Track) -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val album by viewModel.album.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = album?.title.orEmpty(), onBack = onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkThumb(model = album?.artUri, size = 112)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    ) {
                        Text(
                            text = album?.title.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = album?.artist.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val year = album?.year ?: 0
                        Text(
                            text = listOfNotNull(
                                year.takeIf { it > 0 }?.toString(),
                                pluralStringResource(
                                    R.plurals.track_count,
                                    tracks.size,
                                    tracks.size,
                                ),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalIconButton(
                            onClick = { viewModel.play(0) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.menu_play),
                            )
                        }
                    }
                }
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                AlbumTrackRow(
                    track = track,
                    position = index + 1,
                    onClick = { viewModel.play(index) },
                    onMenuAction = onTrackMenuAction,
                )
            }
        }
    }
}

@Composable
private fun AlbumTrackRow(
    track: Track,
    position: Int,
    onClick: () -> Unit,
    onMenuAction: (TrackMenuAction, Track) -> Unit,
) {
    TrackRow(
        track = track,
        onClick = onClick,
        onMenuAction = onMenuAction,
        showGoTo = false,
        leading = {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )
        },
    )
}

@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onTrackMenuAction: (TrackMenuAction, Track) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val artist by viewModel.artist.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = artist?.name.orEmpty(), onBack = onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (albums.isNotEmpty()) {
                item(key = "albums-header") {
                    SectionHeader(stringResource(R.string.tab_albums))
                }
                item(key = "albums-row") {
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                        items(albums, key = { it.id }) { album ->
                            Column(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .width(112.dp)
                                    .clickable { onAlbumClick(album.id) },
                            ) {
                                ArtworkThumb(model = album.artUri, size = 112)
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "tracks-header") {
                SectionHeader(stringResource(R.string.tab_tracks))
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    track = track,
                    onClick = { viewModel.play(index) },
                    onMenuAction = onTrackMenuAction,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
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
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
