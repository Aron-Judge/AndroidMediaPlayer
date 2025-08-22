package com.aron.mediaplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aron.mediaplayer.ui.playlists.PlaylistsScreen
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
                1 -> PlaylistsScreen()
            }
        }
    }
}