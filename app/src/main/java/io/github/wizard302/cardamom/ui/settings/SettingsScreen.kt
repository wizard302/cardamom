package io.github.wizard302.cardamom.ui.settings

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.settings.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val syncedLyrics by viewModel.syncedLyricsHighlighting.collectAsStateWithLifecycle()
    val pauseOnDisconnect by viewModel.pauseOnDisconnect.collectAsStateWithLifecycle()
    val resumeOnConnect by viewModel.resumeOnConnect.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val rescanStarted = stringResource(R.string.settings_rescan_started)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
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
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            SectionTitle(stringResource(R.string.settings_appearance))
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    RadioRow(
                        label = stringResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            },
                        ),
                        selected = themeMode == mode,
                        onSelect = { viewModel.setThemeMode(mode) },
                    )
                }
            }

            // Material You only exists from API 31; below that the switch would do nothing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(
                    label = stringResource(R.string.settings_dynamic_color),
                    checked = dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            SectionTitle(stringResource(R.string.settings_language))
            LanguagePicker()

            SectionTitle(stringResource(R.string.settings_playback))
            SwitchRow(
                label = stringResource(R.string.settings_pause_on_disconnect),
                checked = pauseOnDisconnect,
                onCheckedChange = viewModel::setPauseOnDisconnect,
            )
            SwitchRow(
                label = stringResource(R.string.settings_resume_on_connect),
                checked = resumeOnConnect,
                onCheckedChange = viewModel::setResumeOnConnect,
            )

            SectionTitle(stringResource(R.string.settings_lyrics))
            SwitchRow(
                label = stringResource(R.string.settings_synced_lyrics),
                checked = syncedLyrics,
                onCheckedChange = viewModel::setSyncedLyricsHighlighting,
            )

            SectionTitle(stringResource(R.string.settings_library))
            Text(
                text = stringResource(R.string.settings_rescan),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.rescanLibrary()
                        scope.launch { snackbarHostState.showSnackbar(rescanStarted) }
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Per-app language. AppCompat owns the value: on API 33+ it forwards to the
 * platform LocaleManager (so the system per-app language screen stays in sync),
 * below that it persists the tag itself via AppLocalesMetadataHolderService.
 */
@Composable
private fun LanguagePicker() {
    var tag by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-'))
    }
    val options = listOf(
        "" to R.string.language_system,
        "en" to R.string.language_en,
        "ru" to R.string.language_ru,
    )
    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { (optionTag, label) ->
            RadioRow(
                label = stringResource(label),
                selected = tag == optionTag,
                onSelect = {
                    tag = optionTag
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(optionTag))
                },
            )
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
