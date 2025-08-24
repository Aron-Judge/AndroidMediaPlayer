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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aron.mediaplayer.data.*
import com.aron.mediaplayer.service.PlaybackService
import com.aron.mediaplayer.viewmodel.PlaylistViewModel
import com.aron.mediaplayer.ui.components.PlaylistPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistViewModel,
    currentPlayingUri: String?,
    hasPermission: Boolean,
    songs: List<Song>,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).playlistDao() }
    val activeStore = remember { ActivePlaylistStore(context, dao) }
    val scope = rememberCoroutineScope()

    val playlists by dao.getAllPlaylists().collectAsState(initial = emptyList())
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }

    var showPicker by remember { mutableStateOf(false) }
    var targetSong by remember { mutableStateOf<PlaylistTrack?>(null) }

    LaunchedEffect(playlists) {
        if (playlists.isNotEmpty() && selectedPlaylistId == null) {
            val id = activeStore.ensureDefaultPlaylistSelected()
            selectedPlaylistId = id
        }
    }

    val selectedPlaylistTracks by if (selectedPlaylistId != null) {
        dao.getTracksForPlaylist(selectedPlaylistId!!).collectAsState(initial = emptyList())
    } else {
        mutableStateOf(emptyList())
    }

    if (showPicker && targetSong != null) {
        PlaylistPickerDialog(
            playlists = playlists,
            onCreateNew = { name ->
                val song = targetSong
                showPicker = false // close UI instantly
                scope.launch(Dispatchers.IO) {
                    val newId = dao.insertPlaylist(PlaylistEntity(name = name))
                    song?.let { s -> dao.insertTrack(s.copy(playlistId = newId)) }
                }
            },
            onSelect = { playlist ->
                val song = targetSong
                showPicker = false
                scope.launch(Dispatchers.IO) {
                    song?.let { s -> dao.insertTrack(s.copy(playlistId = playlist.playlistId)) }
                }
            },
            onDismiss = { showPicker = false }
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Playlists") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                !hasPermission -> Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { onRequestPermission() },
                    contentAlignment = Alignment.Center
                ) { Text("Please grant permissions to view playlists") }

                playlists.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { Text("No playlists yet — add songs from the Songs tab") }

                else -> {
                    ScrollableTabRow(
                        selectedTabIndex = playlists.indexOfFirst { it.playlistId == selectedPlaylistId }
                            .coerceAtLeast(0)
                    ) {
                        playlists.forEach { playlist ->
                            Tab(
                                selected = playlist.playlistId == selectedPlaylistId,
                                onClick = {
                                    selectedPlaylistId = playlist.playlistId
                                    scope.launch { activeStore.setActivePlaylistId(playlist.playlistId) }
                                },
                                text = { Text(playlist.name) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                    ) {
                        items(selectedPlaylistTracks) { track ->
                            PlaylistTrackItem(
                                track = track,
                                isPlaying = track.uri == currentPlayingUri,
                                onPlay = {
                                    val intent = Intent(context, PlaybackService::class.java).apply {
                                        action = PlaybackService.ACTION_PLAY
                                        putExtra(PlaybackService.EXTRA_URI, track.uri)
                                    }
                                    ContextCompat.startForegroundService(context, intent)
                                },
                                onDelete = {
                                    scope.launch(Dispatchers.IO) { dao.deleteTrack(track) }
                                },
                                onAddToOtherPlaylist = {
                                    targetSong = track
                                    showPicker = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistTrackItem(
    track: PlaylistTrack,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onAddToOtherPlaylist: () -> Unit
) {
    val highlight = Color(0xFF1DB954)
    val bgColor = if (isPlaying) highlight.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isPlaying) highlight else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 8.dp)
            .clickable { onPlay() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = textColor)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = textColor)
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = textColor)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
        TextButton(onClick = onAddToOtherPlaylist) {
            Text("Add…")
        }
    }
}