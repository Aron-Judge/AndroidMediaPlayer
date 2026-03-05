package com.aron.mediaplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import com.aron.mediaplayer.data.ActivePlaylistStore
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.data.PlaylistEntity
import com.aron.mediaplayer.ui.components.MissingCoversDialog
import com.aron.mediaplayer.ui.components.NowPlayingBar
import com.aron.mediaplayer.ui.nowplaying.NowPlayingScreen
import com.aron.mediaplayer.ui.screens.PlaylistDetailScreen
import com.aron.mediaplayer.ui.screens.PlaylistListScreen
import com.aron.mediaplayer.ui.songs.SongsScreen
import com.aron.mediaplayer.viewmodel.NowPlayingViewModel
import com.aron.mediaplayer.viewmodel.PlaylistViewModel
import com.aron.mediaplayer.viewmodel.SongsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*

sealed class Screen {
    object Songs : Screen()
    object Playlists : Screen()
    data class PlaylistDetail(val id: Long) : Screen()
    object NowPlaying : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        setContent {
            MediaPlayerDarkTheme {
                MediaPlayerApp()
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaPlayerApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val activeStore = remember { ActivePlaylistStore(context, db.playlistDao()) }

    val playlistViewModel: PlaylistViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlaylistViewModel(db.playlistDao()) as T
            }
        }
    )
    val nowPlayingViewModel: NowPlayingViewModel = viewModel()
    val songsViewModel: SongsViewModel = viewModel()

    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) songsViewModel.loadSongs(context)
    }

    val requestPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(Unit) { requestPermission() }

    val songs by songsViewModel.songs.collectAsState()
    val playlistsWithCount by playlistViewModel.playlistsWithCount.collectAsState()
    val playlistsMissingCover by playlistViewModel
        .getPlaylistsNeedingCover(context)
        .collectAsState(initial = emptyList())

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Playlists) }
    val scope = rememberCoroutineScope()

    val isPlaying by nowPlayingViewModel.isPlaying.collectAsState()
    val currentSong by nowPlayingViewModel.currentSong.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "discRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        bottomBar = {
            Column {
                if (currentSong != null) {
                    NowPlayingBar(
                        title = currentSong!!.title,
                        artist = currentSong!!.artist,
                        artworkUri = currentSong!!.artworkUri,
                        isPlaying = isPlaying,
                        onPlayPause = { nowPlayingViewModel.togglePlayPause() },
                        onExpand = { currentScreen = Screen.NowPlaying }
                    )
                }

                NavigationBar(containerColor = Color.Black, contentColor = Color.White) {

                    // Left: Playlists
                    NavigationBarItem(
                        selected = currentScreen is Screen.Playlists,
                        onClick = { currentScreen = Screen.Playlists },
                        label = { Text("Playlists") },
                        icon = { Icon(Icons.Filled.PlaylistPlay, contentDescription = "Playlists") }
                    )

                    // Middle: Now Playing (spinning disc)
                    NavigationBarItem(
                        selected = currentScreen is Screen.NowPlaying,
                        onClick = { currentScreen = Screen.NowPlaying },
                        label = { Text("Now Playing") },
                        icon = {
                            if (!currentSong?.artworkUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentSong!!.artworkUri,
                                    contentDescription = "Now Playing",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .rotate(if (isPlaying) rotation else 0f)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Album,
                                    contentDescription = "Now Playing",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .rotate(if (isPlaying) rotation else 0f)
                                )
                            }
                        }
                    )

                    // Right: Songs
                    NavigationBarItem(
                        selected = currentScreen is Screen.Songs,
                        onClick = { currentScreen = Screen.Songs },
                        label = { Text("Songs") },
                        icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "Songs") }
                    )
                }
            }
        },
        containerColor = Color.Black,
        contentColor = Color.White
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val screen = currentScreen) {
                Screen.Songs -> SongsScreen(
                    hasPermission = hasPermission,
                    songs = songs,
                    onRequestPermission = requestPermission
                )

                Screen.Playlists -> PlaylistListScreen(
                    playlists = playlistsWithCount,
                    onPlaylistClick = { id -> currentScreen = Screen.PlaylistDetail(id) },
                    onCreatePlaylist = { name, desc, cover ->
                        scope.launch(Dispatchers.IO) {
                            val newId = db.playlistDao().insertPlaylist(
                                PlaylistEntity(
                                    name = name,
                                    description = desc,
                                    coverUri = cover
                                )
                            )
                            activeStore.setActivePlaylistId(newId)
                            launch(Dispatchers.Main) {
                                currentScreen = Screen.PlaylistDetail(newId)
                            }
                        }
                    }
                )

                is Screen.PlaylistDetail -> {
                    val listState = rememberSaveable(screen.id, saver = LazyListState.Saver) {
                        LazyListState()
                    }
                    PlaylistDetailScreen(
                        playlistId = screen.id,
                        viewModel = playlistViewModel,
                        listState = listState,
                        onBack = { currentScreen = Screen.Playlists }
                    )
                }

                Screen.NowPlaying -> {
                    NowPlayingScreen(
                        viewModel = nowPlayingViewModel,
                        onBack = { currentScreen = Screen.Songs }
                    )
                }
            }

            if (playlistsMissingCover.isNotEmpty()) {
                MissingCoversDialog(
                    playlists = playlistsMissingCover,
                    onCoverSelected = { playlistId, uri ->
                        scope.launch(Dispatchers.IO) {
                            db.playlistDao().updatePlaylistCover(playlistId, uri.toString())
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MediaPlayerDarkTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFF00EEFF),
        onPrimary = Color.White,
        background = Color.Black,
        surface = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}