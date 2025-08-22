package com.aron.mediaplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.viewmodel.PlaylistViewModel
import com.aron.mediaplayer.ui.screens.PlaylistsScreen
import com.aron.mediaplayer.ui.songs.SongsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab.value == index,
                        onClick = { selectedTab.value = index },
                        label = { Text(title) },
                        icon = { Icon(icons[index], contentDescription = title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab.value) {
                0 -> SongsScreen()
                1 -> PlaylistsScreen(viewModel = playlistViewModel)
            }
        }
    }
}