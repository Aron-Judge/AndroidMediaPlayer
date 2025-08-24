package com.aron.mediaplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.ui.screens.PlaylistsScreen
import com.aron.mediaplayer.ui.songs.SongsScreen
import com.aron.mediaplayer.viewmodel.NowPlayingViewModel
import com.aron.mediaplayer.viewmodel.PlaylistViewModel
import com.aron.mediaplayer.viewmodel.SongsViewModel

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

@Composable
fun MediaPlayerApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

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

    // Function to trigger permission based on Android version
    val requestPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // Request on first launch
    LaunchedEffect(Unit) { requestPermission() }

    val currentPlayingUri by nowPlayingViewModel.currentUri.collectAsState()
    val songs by songsViewModel.songs.collectAsState()

    val selectedTab = remember { mutableStateOf(0) }
    val tabs = listOf("Songs", "Playlists")
    val icons = listOf(Icons.Filled.LibraryMusic, Icons.Filled.PlaylistPlay)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.Black, contentColor = Color.White) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab.value == index,
                        onClick = { selectedTab.value = index },
                        label = { Text(title) },
                        icon = { Icon(icons[index], contentDescription = title) }
                    )
                }
            }
        },
        containerColor = Color.Black,
        contentColor = Color.White
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab.value) {
                0 -> SongsScreen(
                    hasPermission = hasPermission,
                    songs = songs,
                    currentPlayingUri = currentPlayingUri,
                    onRequestPermission = requestPermission
                )
                1 -> PlaylistsScreen(
                    viewModel = playlistViewModel,
                    currentPlayingUri = currentPlayingUri,
                    hasPermission = hasPermission,
                    songs = songs,
                    onRequestPermission = requestPermission
                )
            }
        }
    }
}

@Composable
fun MediaPlayerDarkTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFF1DB954),
        onPrimary = Color.White,
        background = Color.Black,
        surface = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(colorScheme = colorScheme, content = content)
}