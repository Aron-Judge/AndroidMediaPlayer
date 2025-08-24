package com.aron.mediaplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.ui.screens.PlaylistsScreen
import com.aron.mediaplayer.ui.songs.SongsScreen
import com.aron.mediaplayer.viewmodel.PlaylistViewModel
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force dark mode globally for any Views or system UI
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

    // Provide PlaylistViewModel with DAO
    val playlistViewModel: PlaylistViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlaylistViewModel(db.playlistDao()) as T
            }
        }
    )

    val selectedTab = remember { mutableStateOf(0) }
    val tabs = listOf("Songs", "Playlists")
    val icons = listOf(Icons.Filled.LibraryMusic, Icons.Filled.PlaylistPlay)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.Black,
                contentColor = Color.White
            ) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab.value == index,
                        onClick = { selectedTab.value = index },
                        label = { Text(title) },
                        icon = { androidx.compose.material3.Icon(icons[index], contentDescription = title) }
                    )
                }
            }
        },
        containerColor = Color.Black,
        contentColor = Color.White
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab.value) {
                0 -> SongsScreen()
                1 -> PlaylistsScreen(viewModel = playlistViewModel)
            }
        }
    }
}

@Composable
fun MediaPlayerDarkTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFF1DB954), // Spotify green
        onPrimary = Color.White,
        background = Color.Black,
        surface = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}