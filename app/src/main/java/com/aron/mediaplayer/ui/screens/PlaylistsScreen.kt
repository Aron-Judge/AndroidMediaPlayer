@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aron.mediaplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aron.mediaplayer.data.PlaylistTrack
import com.aron.mediaplayer.viewmodel.PlaylistViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete

@Composable
fun PlaylistsScreen(viewModel: PlaylistViewModel) {
    val playlist by viewModel.playlist.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Playlist") })
        }
    ) { padding ->
        if (playlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No tracks yet")
            }
        } else {
            LazyColumn(
                contentPadding = padding
            ) {
                items(playlist) { track ->
                    PlaylistTrackRow(track, onRemove = { viewModel.removeTrack(track) })
                }
            }
        }
    }
}

@Composable
fun PlaylistTrackRow(track: PlaylistTrack, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(track.title)
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove")
        }
    }
}