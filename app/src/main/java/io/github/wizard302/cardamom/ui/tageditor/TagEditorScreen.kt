package io.github.wizard302.cardamom.ui.tageditor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.tags.TrackTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TagEditorScreen(
    onBack: () -> Unit,
    viewModel: TagEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    // API ≤ 28 writes need the legacy runtime permission; 29+ uses consent flow.
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.save() }

    fun onSaveClick() {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            writePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.save()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                if (bytes != null) viewModel.replaceCover(bytes, mime)
            }
        }
    }

    val savedMessage = stringResource(R.string.tags_saved)
    val errorMessage = stringResource(R.string.tags_save_failed)
    androidx.compose.runtime.LaunchedEffect(Unit) {
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
                text = stringResource(R.string.tag_editor_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = ::onSaveClick,
                enabled = !state.saving && !state.loading && !state.readFailed,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.action_save))
                }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.readFailed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = stringResource(R.string.tags_read_failed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> TagEditorForm(
                tags = state.tags,
                cover = state.cover,
                onTagsChange = viewModel::updateTags,
                onPickCover = {
                    imagePicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onRemoveCover = viewModel::removeCover,
            )
        }
    }
}

@Composable
private fun TagEditorForm(
    tags: TrackTags,
    cover: ByteArray?,
    onTagsChange: ((TrackTags) -> TrackTags) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        CoverSection(cover = cover, onPickCover = onPickCover, onRemoveCover = onRemoveCover)

        Field(stringResource(R.string.details_title), tags.title) { v -> onTagsChange { it.copy(title = v) } }
        Field(stringResource(R.string.details_artist), tags.artist) { v -> onTagsChange { it.copy(artist = v) } }
        Field(stringResource(R.string.details_album), tags.album) { v -> onTagsChange { it.copy(album = v) } }
        Field(stringResource(R.string.tag_album_artist), tags.albumArtist) { v -> onTagsChange { it.copy(albumArtist = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(
                stringResource(R.string.tag_track_number),
                tags.trackNumber,
                modifier = Modifier.weight(1f),
                numeric = true,
            ) { v -> onTagsChange { it.copy(trackNumber = v) } }
            Field(
                stringResource(R.string.tag_disc_number),
                tags.discNumber,
                modifier = Modifier.weight(1f),
                numeric = true,
            ) { v -> onTagsChange { it.copy(discNumber = v) } }
            Field(
                stringResource(R.string.details_year),
                tags.year,
                modifier = Modifier.weight(1f),
                numeric = true,
            ) { v -> onTagsChange { it.copy(year = v) } }
        }
        Field(stringResource(R.string.tag_genre), tags.genre) { v -> onTagsChange { it.copy(genre = v) } }
        androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun CoverSection(cover: ByteArray?, onPickCover: () -> Unit, onRemoveCover: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            OutlinedButton(onClick = onPickCover) {
                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.tag_cover_replace),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (cover != null) {
                OutlinedButton(onClick = onRemoveCover, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.tag_cover_remove),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
