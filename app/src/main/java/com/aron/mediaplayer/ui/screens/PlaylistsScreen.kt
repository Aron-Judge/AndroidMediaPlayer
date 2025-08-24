@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.aron.mediaplayer.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aron.mediaplayer.data.PlaylistTrack
import com.aron.mediaplayer.service.PlaybackService
import com.aron.mediaplayer.viewmodel.NowPlayingViewModel
import com.aron.mediaplayer.viewmodel.PlaylistViewModel

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistViewModel,
    nowPlayingViewModel: NowPlayingViewModel = viewModel()
) {
    val playlist by viewModel.playlist.collectAsState()
    val context = LocalContext.current

    // Observe the current track URI from the shared ViewModel
    val currentPlayingUri by nowPlayingViewModel.currentUri.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Playlist") }) }
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
                contentPadding = padding,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = playlist,
                    key = { it.id }
                ) { track ->
                    PlaylistTrackRow(
                        track = track,
                        isPlaying = track.uri == currentPlayingUri,
                        onPlay = {
                            val intent = Intent(context, PlaybackService::class.java).apply {
                                action = PlaybackService.ACTION_PLAY
                                putExtra(PlaybackService.EXTRA_URI, track.uri)
                            }
                            ContextCompat.startForegroundService(context, intent)
                        },
                        onRemove = { viewModel.removeTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistTrackRow(
    track: PlaylistTrack,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val highlight = Color(0xFF00EEFF)
    val bgColor = if (isPlaying) highlight.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isPlaying) highlight else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = textColor)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = textColor)
        }
        Row {
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = textColor)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }
}