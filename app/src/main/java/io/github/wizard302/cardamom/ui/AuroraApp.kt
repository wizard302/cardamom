package io.github.wizard302.cardamom.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.settings.ThemeMode
import io.github.wizard302.cardamom.ui.library.AlbumsTab
import io.github.wizard302.cardamom.ui.library.ArtistsTab
import io.github.wizard302.cardamom.ui.library.LibraryViewModel
import io.github.wizard302.cardamom.ui.library.PlaceholderTab
import io.github.wizard302.cardamom.ui.library.TracksTab
import io.github.wizard302.cardamom.ui.player.MiniPlayer
import io.github.wizard302.cardamom.ui.player.NowPlayingScreen
import io.github.wizard302.cardamom.ui.player.PlayerViewModel
import io.github.wizard302.cardamom.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private val audioPermission: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
fun CardamomApp(
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasAudioPermission = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (hasAudioPermission) libraryViewModel.onPermissionGranted()
    }

    fun requestPermissions() {
        val permissions = buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (hasAudioPermission) {
            libraryViewModel.onPermissionGranted()
        } else {
            requestPermissions()
        }
    }

    if (!hasAudioPermission) {
        PermissionGate(onRequest = ::requestPermissions)
        return
    }

    LibraryScaffold(libraryViewModel, playerViewModel)
}

@Composable
private fun ThemeToggleButton(viewModel: SettingsViewModel = hiltViewModel()) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    IconButton(onClick = viewModel::cycleThemeMode) {
        Icon(
            imageVector = when (themeMode) {
                ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                ThemeMode.LIGHT -> Icons.Rounded.LightMode
                ThemeMode.DARK -> Icons.Rounded.DarkMode
            },
            contentDescription = stringResource(R.string.action_theme),
        )
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.permission_rationale),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRequest,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text(stringResource(R.string.permission_grant))
            }
        }
    }
}

@Composable
private fun LibraryScaffold(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
) {
    val tracks by libraryViewModel.tracks.collectAsStateWithLifecycle()
    val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
    val artists by libraryViewModel.artists.collectAsStateWithLifecycle()

    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    val tabTitles = listOf(
        stringResource(R.string.tab_artists),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_tracks),
        stringResource(R.string.tab_playlists),
        stringResource(R.string.tab_folders),
    )
    val pagerState = rememberPagerState { tabTitles.size }
    val scope = rememberCoroutineScope()
    val emptyText = stringResource(R.string.library_empty)

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 0.dp,
                        modifier = Modifier.weight(1f),
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = { Text(title) },
                            )
                        }
                    }
                    ThemeToggleButton()
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (page) {
                        0 -> ArtistsTab(
                            artists = artists,
                            emptyText = emptyText,
                            onArtistClick = { /* TODO Phase 1: artist drill-down */ },
                        )
                        1 -> AlbumsTab(
                            albums = albums,
                            emptyText = emptyText,
                            onAlbumClick = { /* TODO Phase 1: album drill-down */ },
                        )
                        2 -> TracksTab(
                            tracks = tracks,
                            emptyText = emptyText,
                            onTrackClick = { index -> libraryViewModel.play(tracks, index) },
                        )
                        3 -> PlaceholderTab(stringResource(R.string.placeholder_phase2))
                        4 -> PlaceholderTab("TODO: Folders (Phase 1 checklist)")
                    }
                }

                MiniPlayer(
                    viewModel = playerViewModel,
                    onClick = { showNowPlaying = true },
                )
            }

            if (showNowPlaying) {
                BackHandler { showNowPlaying = false }
                NowPlayingScreen(
                    viewModel = playerViewModel,
                    onBack = { showNowPlaying = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
