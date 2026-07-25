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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.media.Album
import io.github.wizard302.cardamom.data.media.Artist
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.data.settings.AlbumSort
import io.github.wizard302.cardamom.data.settings.ArtistSort
import io.github.wizard302.cardamom.data.settings.TrackSort
import io.github.wizard302.cardamom.ui.library.AlbumScreen
import io.github.wizard302.cardamom.ui.library.AlbumsTab
import io.github.wizard302.cardamom.ui.library.ArtistScreen
import io.github.wizard302.cardamom.ui.library.ArtistsTab
import io.github.wizard302.cardamom.ui.library.FoldersTab
import io.github.wizard302.cardamom.ui.library.LibrarySearchField
import io.github.wizard302.cardamom.ui.library.LibraryViewModel
import io.github.wizard302.cardamom.ui.library.SortMenuButton
import io.github.wizard302.cardamom.ui.library.labelRes
import io.github.wizard302.cardamom.ui.library.TrackDetailsDialog
import io.github.wizard302.cardamom.ui.library.TrackMenuAction
import io.github.wizard302.cardamom.ui.library.TracksTab
import io.github.wizard302.cardamom.ui.player.MiniPlayer
import io.github.wizard302.cardamom.ui.player.NowPlayingScreen
import io.github.wizard302.cardamom.ui.player.PlayerViewModel
import io.github.wizard302.cardamom.ui.playlist.AddToPlaylistDialog
import io.github.wizard302.cardamom.ui.playlist.FavoritesScreen
import io.github.wizard302.cardamom.ui.playlist.PlaylistDetailScreen
import io.github.wizard302.cardamom.ui.playlist.PlaylistsTab
import io.github.wizard302.cardamom.ui.fetcher.AlbumFetcherScreen
import io.github.wizard302.cardamom.ui.fetcher.FetcherScreen
import io.github.wizard302.cardamom.ui.settings.AboutScreen
import io.github.wizard302.cardamom.ui.settings.EqualizerScreen
import io.github.wizard302.cardamom.ui.settings.ScannedFoldersScreen
import io.github.wizard302.cardamom.ui.settings.SettingsScreen
import io.github.wizard302.cardamom.ui.tageditor.AlbumTagEditorScreen
import io.github.wizard302.cardamom.ui.tageditor.TagEditorScreen
import kotlinx.coroutines.flow.first
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
    var addToPlaylistTracks by remember { mutableStateOf<List<Track>?>(null) }
    // Pre-fills the new-playlist name when adding a whole folder at once.
    var addToPlaylistName by remember { mutableStateOf("") }

    // Now Playing is an overlay, not a nav destination, so leaving it for the tag
    // editor or the fetcher would otherwise drop the user back in the library.
    var restoreNowPlaying by rememberSaveable { mutableStateOf(false) }
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        if (restoreNowPlaying && currentRoute == "library") {
            restoreNowPlaying = false
            showNowPlaying = true
        }
    }

    fun onTrackMenuAction(action: TrackMenuAction, track: Track) {
        when (action) {
            TrackMenuAction.PLAY -> libraryViewModel.play(listOf(track), 0)
            TrackMenuAction.PLAY_NEXT -> libraryViewModel.playNext(listOf(track))
            TrackMenuAction.ADD_TO_QUEUE -> libraryViewModel.addToQueue(listOf(track))
            TrackMenuAction.ADD_TO_PLAYLIST -> {
                addToPlaylistName = ""
                addToPlaylistTracks = listOf(track)
            }
            TrackMenuAction.GO_TO_ARTIST -> navController.navigate("artist/${track.artistId}")
            TrackMenuAction.GO_TO_ALBUM -> navController.navigate("album/${track.albumId}")
            TrackMenuAction.EDIT_TAGS -> navController.navigate("tagEditor/${track.id}")
            TrackMenuAction.FETCH_METADATA -> navController.navigate("fetcher/${track.id}")
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
                            onPlaylistClick = { navController.navigate("playlist/$it") },
                            onFavoritesClick = { navController.navigate("favorites") },
                            onEditAlbumTags = { navController.navigate("albumTagEditor/$it") },
                            onFetchAlbumMetadata = { navController.navigate("albumFetcher/$it") },
                            onSettingsClick = { navController.navigate("settings") },
                            onAddFolderToPlaylist = { name, folderTracks ->
                                addToPlaylistName = name
                                addToPlaylistTracks = folderTracks
                            },
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
                    ) { backStackEntry ->
                        val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
                        AlbumScreen(
                            onBack = { navController.popBackStack() },
                            onEditAlbumTags = { navController.navigate("albumTagEditor/$albumId") },
                            onFetchAlbumMetadata = { navController.navigate("albumFetcher/$albumId") },
                            onTrackMenuAction = ::onTrackMenuAction,
                        )
                    }
                    composable(
                        route = "playlist/{playlistId}",
                        arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                    ) {
                        PlaylistDetailScreen(
                            onBack = { navController.popBackStack() },
                            onTrackMenuAction = ::onTrackMenuAction,
                        )
                    }
                    composable("favorites") {
                        FavoritesScreen(
                            onBack = { navController.popBackStack() },
                            onTrackMenuAction = ::onTrackMenuAction,
                        )
                    }
                    composable(
                        route = "tagEditor/{trackId}",
                        arguments = listOf(navArgument("trackId") { type = NavType.LongType }),
                    ) {
                        TagEditorScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "albumTagEditor/{albumId}",
                        arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
                    ) {
                        AlbumTagEditorScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "fetcher/{trackId}",
                        arguments = listOf(navArgument("trackId") { type = NavType.LongType }),
                    ) {
                        FetcherScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "albumFetcher/{albumId}",
                        arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
                    ) {
                        AlbumFetcherScreen(onBack = { navController.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onAbout = { navController.navigate("about") },
                            onEqualizer = { navController.navigate("equalizer") },
                            onScannedFolders = { navController.navigate("scannedFolders") },
                        )
                    }
                    composable("about") {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                    composable("equalizer") {
                        EqualizerScreen(onBack = { navController.popBackStack() })
                    }
                    composable("scannedFolders") {
                        ScannedFoldersScreen(onBack = { navController.popBackStack() })
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
                    onTrackAction = { action ->
                        val id = playerViewModel.currentItem.value?.mediaId?.toLongOrNull()
                        val track = id?.let(libraryViewModel::trackById)
                        if (track != null) {
                            // Dialogs float above this overlay; navigation targets
                            // would be hidden behind it, so step back first.
                            if (action.navigatesAway) {
                                showNowPlaying = false
                                restoreNowPlaying = true
                            }
                            onTrackMenuAction(action, track)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    detailsTrack?.let { track ->
        TrackDetailsDialog(track = track, onDismiss = { detailsTrack = null })
    }
    addToPlaylistTracks?.let { tracks ->
        AddToPlaylistDialog(
            tracks = tracks,
            suggestedName = addToPlaylistName,
            onDone = {
                addToPlaylistTracks = null
                addToPlaylistName = ""
            },
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
    onPlaylistClick: (Long) -> Unit,
    onFavoritesClick: () -> Unit,
    onEditAlbumTags: (Long) -> Unit,
    onFetchAlbumMetadata: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onAddFolderToPlaylist: (String, List<Track>) -> Unit,
    onTrackMenuAction: (TrackMenuAction, Track) -> Unit,
) {
    val tracks by libraryViewModel.tracks.collectAsStateWithLifecycle()
    val albums by libraryViewModel.albums.collectAsStateWithLifecycle()
    val artists by libraryViewModel.artists.collectAsStateWithLifecycle()
    val query by libraryViewModel.query.collectAsStateWithLifecycle()
    val trackSort by libraryViewModel.trackSort.collectAsStateWithLifecycle()
    val albumSort by libraryViewModel.albumSort.collectAsStateWithLifecycle()
    val artistSort by libraryViewModel.artistSort.collectAsStateWithLifecycle()
    var searchActive by rememberSaveable { mutableStateOf(false) }

    val tabTitles = listOf(
        stringResource(R.string.tab_artists),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_tracks),
        stringResource(R.string.tab_playlists),
        stringResource(R.string.tab_folders),
    )
    val pagerState = rememberPagerState { tabTitles.size }
    // Restore the last-opened tab, then persist the current one on every change.
    LaunchedEffect(Unit) {
        val initial = libraryViewModel.libraryTab.first { it in 0 until tabTitles.size }
        pagerState.scrollToPage(initial)
        snapshotFlow { pagerState.currentPage }.collect(libraryViewModel::setLibraryTab)
    }
    val scope = rememberCoroutineScope()
    val emptyText = if (query.isBlank()) {
        stringResource(R.string.library_empty)
    } else {
        stringResource(R.string.search_no_results)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Actions live in their own bar; sharing a row with the tabs squeezed them.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { searchActive = !searchActive }) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.action_search),
                )
            }
            when (pagerState.currentPage) {
                0 -> SortMenuButton(
                    options = ArtistSort.entries,
                    selected = artistSort,
                    labelFor = { stringResource(it.labelRes) },
                    onSelect = libraryViewModel::setArtistSort,
                )
                1 -> SortMenuButton(
                    options = AlbumSort.entries,
                    selected = albumSort,
                    labelFor = { stringResource(it.labelRes) },
                    onSelect = libraryViewModel::setAlbumSort,
                )
                // Folders reuse the track list, so they follow the track sort too.
                2, 4 -> SortMenuButton(
                    options = TrackSort.entries,
                    selected = trackSort,
                    labelFor = { stringResource(it.labelRes) },
                    onSelect = libraryViewModel::setTrackSort,
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                )
            }
        }

        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth(),
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

        if (searchActive) {
            LibrarySearchField(
                query = query,
                onQueryChange = libraryViewModel::setQuery,
                onClose = {
                    searchActive = false
                    libraryViewModel.setQuery("")
                },
            )
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
                    onEditTags = onEditAlbumTags,
                    onFetchTags = onFetchAlbumMetadata,
                )
                2 -> TracksTab(
                    tracks = tracks,
                    emptyText = emptyText,
                    onTrackClick = { index -> libraryViewModel.play(tracks, index) },
                    onMenuAction = onTrackMenuAction,
                )
                3 -> PlaylistsTab(
                    onPlaylistClick = onPlaylistClick,
                    onFavoritesClick = onFavoritesClick,
                )
                4 -> FoldersTab(
                    tracks = tracks,
                    emptyText = emptyText,
                    onPlay = { folderTracks, index -> libraryViewModel.play(folderTracks, index) },
                    onPlayNext = libraryViewModel::playNext,
                    onAddToQueue = libraryViewModel::addToQueue,
                    onAddToPlaylist = onAddFolderToPlaylist,
                    onMenuAction = onTrackMenuAction,
                )
            }
        }
    }
}
