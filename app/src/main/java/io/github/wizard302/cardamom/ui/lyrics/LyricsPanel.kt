package io.github.wizard302.cardamom.ui.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wizard302.cardamom.R

@Composable
fun LyricsPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LyricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val activeLine by viewModel.activeLine.collectAsStateWithLifecycle()
    val syncedHighlighting by viewModel.syncedHighlighting.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Text(
                    text = stringResource(R.string.lyrics_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.hasSynced) {
                    IconButton(onClick = viewModel::toggleSyncedHighlighting) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = stringResource(R.string.settings_synced_lyrics),
                            tint = if (syncedHighlighting) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.lyrics_search),
                    )
                }
            }

            if (showSearch) {
                SearchEditor(
                    artist = state.queryArtist,
                    title = state.queryTitle,
                    searching = state.searching,
                    onArtist = viewModel::setQueryArtist,
                    onTitle = viewModel::setQueryTitle,
                    onSearch = { viewModel.research(); showSearch = false },
                )
            }

            when {
                state.loading || state.searching -> Centered { CircularProgressIndicator() }

                lines.isNotEmpty() && syncedHighlighting -> SyncedLyrics(
                    lines = lines,
                    activeLine = activeLine,
                    onSeek = viewModel::seekToLine,
                )

                !state.plain.isNullOrBlank() || lines.isNotEmpty() -> PlainLyrics(
                    text = state.plain?.takeIf { it.isNotBlank() }
                        ?: lines.joinToString("\n") { it.text },
                )

                state.error -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.fetcher_error),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = viewModel::research) {
                            Text(stringResource(R.string.fetcher_retry))
                        }
                    }
                }

                else -> Centered {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<io.github.wizard302.cardamom.data.lyrics.LrcLine>, activeLine: Int, onSeek: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeLine) {
        if (activeLine >= 0) {
            val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
            listState.animateScrollToItem(activeLine, -(viewport / 2))
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeLine
            val color by animateColorAsState(
                targetValue = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "lyricLineColor",
            )
            Text(
                text = line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(index) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PlainLyrics(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    )
}

@Composable
private fun SearchEditor(
    artist: String,
    title: String,
    searching: Boolean,
    onArtist: (String) -> Unit,
    onTitle: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = artist,
            onValueChange = onArtist,
            label = { Text(stringResource(R.string.details_artist)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitle,
            label = { Text(stringResource(R.string.details_title)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        TextButton(
            onClick = onSearch,
            enabled = !searching,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.fetcher_search),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), Alignment.Center) { content() }
}
