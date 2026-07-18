package io.github.wizard302.cardamom.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.media.Album
import io.github.wizard302.cardamom.data.media.Artist
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.data.settings.ThemeMode
import io.github.wizard302.cardamom.ui.library.AlbumScreen
import io.github.wizard302.cardamom.ui.library.AlbumsTab
import io.github.wizard302.cardamom.ui.library.ArtistScreen
import io.github.wizard302.cardamom.ui.library.ArtistsTab
import io.github.wizard302.cardamom.ui.library.FoldersTab
import io.github.wizard302.cardamom.ui.library.LibraryViewModel
import io.github.wizard302.cardamom.ui.library.PlaceholderTab
import io.github.wizard302.cardamom.ui.library.TrackDetailsDialog
import io.github.wizard302.cardamom.ui.library.TrackMenuAction
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

    MainNavigation(libraryViewModel, playerViewModel)
}

@Composable
private fun MainNavigation(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
) {
    val navController = rememberNavController()
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var detailsTrack by remember { mutableStateOf<Track?>(null) }

    fun onTrackMenuAction(action: TrackMenuAction, track: Track) {
        when (action) {
            TrackMenuAction.PLAY -> libraryViewModel.play(listOf(track), 0)
            TrackMenuAction.PLAY_NEXT -> libraryViewModel.playNext(listOf(track))
            TrackMenuAction.ADD_TO_QUEUE -> libraryViewModel.addToQueue(listOf(track))
            TrackMenuAction.GO_TO_ARTIST -> navController.navigate("artist/${track.artistId}")
            TrackMenuAction.GO_TO_ALBUM -> navController.navigate("album/${track.albumId}")
            TrackMenuAction.DETAILS -> detailsTrack = track
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "library",
                    modifier = Modifier.weight(1f),
                ) {
                    composable("library") {
                        LibraryScreen(
                            libraryViewModel = libraryViewModel,
                            onArtistClick = { navController.navigate("artist/${it.id}") },
                            onAlbumClick = { navController.navigate("album/${it.id}") },
                            onGoToArtist = { artistId -> navController.navigate("artist/$artistId") },
                            onTrackMenuAction = ::onTrackMenuAction,
                        )
                    }
                    composable(
                        route = "artist/{artistId}",
                        arguments = listOf(navArgument("artistId") { type = NavType.LongType }),
                    ) {
                        ArtistScreen(
                            onBack = { navController.popBackStack() },
                            onAlbumClick = { navController.navigate("album/$it") },
                            onTrackMenuAction = ::onTrackMenuAction,
                        )
                    }
                    composable(
                        route = "album/{albumId}",
                        arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
                    ) {
                        AlbumScreen(
                            onBack = { navController.popBackStack() },
                            onTrackMenuAction = ::onTrackMenuAction,
                        )
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

    detailsTrack?.let { track ->
        TrackDetailsDialog(track = track, onDismiss = { detailsTrack = null })
    }
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
            verticalArrangement = Arrangement.Center,
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
private fun LibraryScreen(
    libraryViewModel: LibraryViewModel,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onGoToArtist: (Long) -> Unit,
    onTrackMenuAction: (TrackMenuAction, Track) -> Unit,
) {
    val tracks by libraryViewModel.tracks.collectAsStateWithLifecycle()
    val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
    val artists by libraryViewModel.artists.collectAsStateWithLifecycle()

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

    Column(modifier = Modifier.fillMaxSize()) {
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
                    onArtistClick = onArtistClick,
                    onPlay = { libraryViewModel.playArtist(it.id) },
                    onPlayNext = { libraryViewModel.playNextArtist(it.id) },
                    onAddToQueue = { libraryViewModel.addArtistToQueue(it.id) },
                )
                1 -> AlbumsTab(
                    albums = albums,
                    emptyText = emptyText,
                    onAlbumClick = onAlbumClick,
                    onPlay = { libraryViewModel.playAlbum(it.id) },
                    onPlayNext = { libraryViewModel.playNextAlbum(it.id) },
                    onAddToQueue = { libraryViewModel.addAlbumToQueue(it.id) },
                    onGoToArtist = { album -> onGoToArtist(album.artistId) },
                )
                2 -> TracksTab(
                    tracks = tracks,
                    emptyText = emptyText,
                    onTrackClick = { index -> libraryViewModel.play(tracks, index) },
                    onMenuAction = onTrackMenuAction,
                )
                3 -> PlaceholderTab(stringResource(R.string.placeholder_phase2))
                4 -> FoldersTab(
                    tracks = tracks,
                    emptyText = emptyText,
                    onPlay = { folderTracks, index -> libraryViewModel.play(folderTracks, index) },
                    onPlayNext = libraryViewModel::playNext,
                    onAddToQueue = libraryViewModel::addToQueue,
                    onMenuAction = onTrackMenuAction,
                )
            }
        }
    }
}
