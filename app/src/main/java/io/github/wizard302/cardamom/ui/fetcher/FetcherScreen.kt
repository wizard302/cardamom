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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.remote.TrackCandidate
import io.github.wizard302.cardamom.ui.tageditor.TagEditorEvent

@Composable
fun FetcherScreen(
    onBack: () -> Unit,
    viewModel: FetcherViewModel = hiltViewModel(),
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

    Column(modifier = Modifier.fillMaxSize()) {
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
                text = stringResource(R.string.fetcher_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.queryArtist,
                onValueChange = viewModel::setQueryArtist,
                label = { Text(stringResource(R.string.details_artist)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.queryTitle,
                onValueChange = viewModel::setQueryTitle,
                label = { Text(stringResource(R.string.details_title)) },
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
                    Text(
                        text = stringResource(R.string.fetcher_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = viewModel::search) {
                        Text(stringResource(R.string.fetcher_retry))
                    }
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.candidates) { candidate ->
                    CandidateCard(candidate) { viewModel.selectCandidate(candidate) }
                }
            }
        }
    }

    if (state.selected != null) {
        ApplyDialog(state = state, viewModel = viewModel)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { content() }
}

@Composable
private fun CandidateCard(candidate: TrackCandidate, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
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
                    if (candidate.album.isNotBlank()) append(" · ").append(candidate.album)
                    if (candidate.year.isNotBlank()) append(" (").append(candidate.year).append(')')
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ApplyDialog(state: FetcherUiState, viewModel: FetcherViewModel) {
    val candidate = state.selected ?: return
    AlertDialog(
        onDismissRequest = viewModel::dismissSelection,
        title = { Text(stringResource(R.string.fetcher_apply_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                CoverRow(state = state, onToggle = viewModel::toggleCover)
                FieldRow(
                    label = stringResource(R.string.details_title),
                    current = state.currentTags.title,
                    new = candidate.title,
                    checked = state.applyTitle,
                    onCheck = viewModel::toggleTitle,
                )
                FieldRow(
                    label = stringResource(R.string.details_artist),
                    current = state.currentTags.artist,
                    new = candidate.artist,
                    checked = state.applyArtist,
                    onCheck = viewModel::toggleArtist,
                )
                FieldRow(
                    label = stringResource(R.string.details_album),
                    current = state.currentTags.album,
                    new = candidate.album,
                    checked = state.applyAlbum,
                    enabled = candidate.album.isNotBlank(),
                    onCheck = viewModel::toggleAlbum,
                )
                FieldRow(
                    label = stringResource(R.string.details_year),
                    current = state.currentTags.year,
                    new = candidate.year,
                    checked = state.applyYear,
                    enabled = candidate.year.isNotBlank(),
                    onCheck = viewModel::toggleYear,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::apply, enabled = !state.saving) {
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.fetcher_apply))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissSelection) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun CoverRow(state: FetcherUiState, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = state.applyCover,
            onCheckedChange = onToggle,
            enabled = state.cover != null,
        )
        Box(
            modifier = Modifier
                .size(56.dp)
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
        Text(
            text = stringResource(R.string.fetcher_cover),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun FieldRow(
    label: String,
    current: String,
    new: String,
    checked: Boolean,
    onCheck: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheck(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked && enabled, onCheckedChange = onCheck, enabled = enabled)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (current.isNotBlank() && current != new) {
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = new.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
