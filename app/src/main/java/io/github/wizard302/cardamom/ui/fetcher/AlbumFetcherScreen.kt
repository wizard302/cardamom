package io.github.wizard302.cardamom.ui.fetcher

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.remote.AlbumCandidate
import io.github.wizard302.cardamom.ui.tageditor.TagEditorEvent

@Composable
fun AlbumFetcherScreen(
    onBack: () -> Unit,
    viewModel: AlbumFetcherViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    val savedMessage = stringResource(R.string.tags_saved)
    val errorMessage = stringResource(R.string.tags_save_failed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TagEditorEvent.RequestConsent ->
                    consentLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())
                TagEditorEvent.Saved -> {
                    Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                    onBack()
                }
                TagEditorEvent.Error ->
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    val inReview = state.release != null
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (inReview) viewModel.backToResults() else onBack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = stringResource(R.string.album_fetcher_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }

        if (inReview) {
            ReviewContent(state, viewModel)
        } else {
            SearchContent(state, viewModel)
        }
    }
}

@Composable
private fun SearchContent(state: AlbumFetcherUiState, viewModel: AlbumFetcherViewModel) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.queryArtist,
            onValueChange = viewModel::setQueryArtist,
            label = { Text(stringResource(R.string.details_artist)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.queryAlbum,
            onValueChange = viewModel::setQueryAlbum,
            label = { Text(stringResource(R.string.details_album)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        TextButton(
            onClick = viewModel::search,
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

    when (state.status) {
        SearchStatus.LOADING -> Centered { CircularProgressIndicator() }
        SearchStatus.ERROR -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.fetcher_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::search) { Text(stringResource(R.string.fetcher_retry)) }
            }
        }
        SearchStatus.EMPTY -> Centered {
            Text(
                text = stringResource(R.string.fetcher_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SearchStatus.RESULTS -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.candidates) { candidate ->
                AlbumCandidateCard(candidate) { viewModel.selectRelease(candidate) }
            }
        }
    }
}

@Composable
private fun AlbumCandidateCard(candidate: AlbumCandidate, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = candidate.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(candidate.artist)
                    if (candidate.year.isNotBlank()) append(" (").append(candidate.year).append(')')
                    if (candidate.trackCount > 0) {
                        append(" · ").append(
                            pluralStringResource(
                                R.plurals.track_count,
                                candidate.trackCount,
                                candidate.trackCount,
                            ),
                        )
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReviewContent(state: AlbumFetcherUiState, viewModel: AlbumFetcherViewModel) {
    if (state.releaseLoading) {
        Centered { CircularProgressIndicator() }
        return
    }
    val release = state.release ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.coverLoading -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        state.cover != null -> AsyncImage(
                            model = state.cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(release.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = buildString {
                            append(release.artist)
                            if (release.year.isNotBlank()) append(" (").append(release.year).append(')')
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "options") {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                OptionRow(stringResource(R.string.details_album), state.applyAlbum, viewModel::toggleAlbum)
                OptionRow(stringResource(R.string.tag_album_artist), state.applyAlbumArtist, viewModel::toggleAlbumArtist)
                OptionRow(
                    stringResource(R.string.details_year),
                    state.applyYear,
                    viewModel::toggleYear,
                    enabled = release.year.isNotBlank(),
                )
                OptionRow(stringResource(R.string.album_fetcher_track_titles), state.applyTrackTitles, viewModel::toggleTrackTitles)
                OptionRow(
                    stringResource(R.string.fetcher_cover),
                    state.applyCover,
                    viewModel::toggleCover,
                    enabled = state.cover != null,
                )
            }
        }

        item(key = "tracks-header") {
            Text(
                text = stringResource(R.string.album_fetcher_track_preview),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }

        itemsIndexed(state.previews, key = { _, p -> p.position }) { _, preview ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    text = "${preview.position}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (state.applyTrackTitles && preview.newTitle != null &&
                        preview.newTitle != preview.currentTitle
                    ) {
                        Text(
                            text = preview.currentTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = if (state.applyTrackTitles) {
                            preview.newTitle ?: preview.currentTitle
                        } else {
                            preview.currentTitle
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item(key = "apply") {
            Button(
                onClick = viewModel::apply,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.fetcher_apply))
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onCheck: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheck(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked && enabled, onCheckedChange = onCheck, enabled = enabled)
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { content() }
}
