package io.github.wizard302.cardamom.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.settings.AlbumSort
import io.github.wizard302.cardamom.data.settings.ArtistSort
import io.github.wizard302.cardamom.data.settings.TrackSort

/** Search field shown under the tab row while search is active. */
@Composable
fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_clear),
                )
            }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .focusRequester(focusRequester),
    )
}

/** Overflow-style sort picker; the caller supplies the options for the visible tab. */
@Composable
fun <T> SortMenuButton(
    options: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Rounded.Sort,
            contentDescription = stringResource(R.string.action_sort),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { option ->
            val isSelected = option == selected
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isSelected, onClick = null)
                        Text(
                            text = labelFor(option),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                },
                onClick = {
                    onSelect(option)
                    expanded = false
                },
            )
        }
    }
}

@get:StringRes
val TrackSort.labelRes: Int
    get() = when (this) {
        TrackSort.TITLE -> R.string.sort_title
        TrackSort.ARTIST -> R.string.sort_artist
        TrackSort.ALBUM -> R.string.sort_album
        TrackSort.DATE_ADDED -> R.string.sort_date_added
        TrackSort.DURATION -> R.string.sort_duration
    }

@get:StringRes
val AlbumSort.labelRes: Int
    get() = when (this) {
        AlbumSort.TITLE -> R.string.sort_title
        AlbumSort.ARTIST -> R.string.sort_artist
        AlbumSort.YEAR -> R.string.sort_year
        AlbumSort.DATE_ADDED -> R.string.sort_date_added
    }

@get:StringRes
val ArtistSort.labelRes: Int
    get() = when (this) {
        ArtistSort.NAME -> R.string.sort_name
        ArtistSort.TRACK_COUNT -> R.string.sort_track_count
    }
