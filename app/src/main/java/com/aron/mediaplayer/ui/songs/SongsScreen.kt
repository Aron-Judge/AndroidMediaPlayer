@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.aron.mediaplayer.ui.songs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.aron.mediaplayer.data.*
import com.aron.mediaplayer.service.PlaybackService
import com.aron.mediaplayer.ui.components.PlaylistPickerDialog
import com.aron.mediaplayer.ui.components.NowPlayingBar
import com.aron.mediaplayer.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SongsScreen(
    hasPermission: Boolean,
    songs: List<Song>,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).playlistDao() }
    val activeStore = remember { ActivePlaylistStore(context, dao) }
    var showPickerForSong by remember { mutableStateOf<Song?>(null) }
    val playlists by dao.getAllPlaylists().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 👇 Collect playback state
    val nowPlayingViewModel: NowPlayingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val currentPlayingUri by nowPlayingViewModel.currentUri.collectAsState()
    val isPlaying by nowPlayingViewModel.isPlaying.collectAsState()
    val currentSong by nowPlayingViewModel.currentSong.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (currentSong != null) {
                NowPlayingBar(
                    title = currentSong!!.title,
                    artist = currentSong!!.artist,
                    artworkUri = currentSong!!.artworkUri,
                    isPlaying = isPlaying,
                    onPlayPause = { nowPlayingViewModel.togglePlayPause() },
                    onExpand = { /* TODO: navigate to full player screen */ }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                !hasPermission -> Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { onRequestPermission() },
                    contentAlignment = Alignment.Center
                ) { Text("Please grant permissions to view songs") }

                songs.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { Text("No songs found on this device") }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(songs) { song ->
                        SongItem(
                            song = song,
                            isPlaying = song.contentUri.toString() == currentPlayingUri,
                            onPlay = {
                                val intent = Intent(context, PlaybackService::class.java).apply {
                                    action = PlaybackService.ACTION_PLAY
                                    putExtra(PlaybackService.EXTRA_URI, song.contentUri.toString())
                                }
                                context.startService(intent)
                            },
                            onAddToPlaylist = { showPickerForSong = song }
                        )
                    }
                }
            }
        }
    }

    if (showPickerForSong != null) {
        PlaylistPickerDialog(
            playlists = playlists,
            onCreateNew = { name ->
                val song = showPickerForSong
                showPickerForSong = null
                coroutineScope.launch(Dispatchers.IO) {
                    val newId = dao.insertPlaylist(PlaylistEntity(name = name))
                    song?.let { s ->
                        dao.insertTrack(
                            PlaylistTrack(
                                playlistId = newId,
                                uri = s.contentUri.toString(),
                                title = s.title,
                                artist = s.artist,
                                duration = s.duration,
                                artworkUri = s.artworkUri
                            )
                        )
                    }
                    activeStore.setActivePlaylistId(newId)
                    launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Added to \"$name\"")
                    }
                }
            },
            onSelect = { playlist ->
                val song = showPickerForSong
                showPickerForSong = null
                coroutineScope.launch(Dispatchers.IO) {
                    song?.let { s ->
                        dao.insertTrack(
                            PlaylistTrack(
                                playlistId = playlist.playlistId,
                                uri = s.contentUri.toString(),
                                title = s.title,
                                artist = s.artist,
                                duration = s.duration,
                                artworkUri = s.artworkUri
                            )
                        )
                    }
                    activeStore.setActivePlaylistId(playlist.playlistId)
                    launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Added to \"${playlist.name}\"")
                    }
                }
            },
            onDismiss = { showPickerForSong = null }
        )
    }
}

@Composable
fun SongItem(song: Song, isPlaying: Boolean, onPlay: () -> Unit, onAddToPlaylist: () -> Unit) {
    val highlight = Color(0xFF00EEFF)
    val bgColor = if (isPlaying) highlight.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isPlaying) highlight else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onPlay() }
        ) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, color = textColor)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = textColor)
        }
        Row {
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = textColor)
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist")
            }
        }
    }
}