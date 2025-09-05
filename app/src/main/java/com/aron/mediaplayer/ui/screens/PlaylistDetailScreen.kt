@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aron.mediaplayer.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.aron.mediaplayer.data.*
import com.aron.mediaplayer.service.PlaybackService
import com.aron.mediaplayer.ui.components.EditPlaylistDialog
import com.aron.mediaplayer.ui.components.PlaylistPickerDialog
import com.aron.mediaplayer.ui.components.PlaylistTrackItem
import com.aron.mediaplayer.viewmodel.PlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    viewModel: PlaylistViewModel,
    currentPlayingUri: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).playlistDao() }
    val activeStore = remember { ActivePlaylistStore(context, dao) }
    val scope = rememberCoroutineScope()

    val playlist by produceState<PlaylistEntity?>(initialValue = null, playlistId) {
        value = dao.getPlaylistById(playlistId)
    }
    val tracks by viewModel.getTracksForPlaylist(playlistId).collectAsState()
    val playlists by dao.getAllPlaylists().collectAsState(initial = emptyList())

    var showPicker by remember { mutableStateOf(false) }
    var targetSong by remember { mutableStateOf<PlaylistTrack?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    if (showPicker && targetSong != null) {
        PlaylistPickerDialog(
            playlists = playlists,
            onCreateNew = { name ->
                val song = targetSong
                showPicker = false
                scope.launch(Dispatchers.IO) {
                    val newId = dao.insertPlaylist(PlaylistEntity(name = name))
                    song?.let { s -> dao.insertTrack(s.copy(playlistId = newId)) }
                    activeStore.setActivePlaylistId(newId)
                }
            },
            onSelect = { playlist ->
                val song = targetSong
                showPicker = false
                scope.launch(Dispatchers.IO) {
                    song?.let { s -> dao.insertTrack(s.copy(playlistId = playlist.playlistId)) }
                    activeStore.setActivePlaylistId(playlist.playlistId)
                }
            },
            onDismiss = { showPicker = false }
        )
    }

    if (showEditDialog && playlist != null) {
        EditPlaylistDialog(
            playlist = playlist!!,
            onSave = { newName, newDesc, newCover ->
                scope.launch(Dispatchers.IO) {
                    dao.updatePlaylist(
                        playlist!!.copy(
                            name = newName,
                            description = newDesc,
                            coverUri = newCover
                        )
                    )
                }
            },
            onDismiss = { showEditDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (playlist != null) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Playlist")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.DarkGray)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val artworkToShow = playlist?.coverUri ?: tracks.firstOrNull()?.artworkUri
                    if (!artworkToShow.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkToShow,
                            contentDescription = "Playlist artwork",
                            modifier = Modifier
                                .size(180.dp)
                                .background(Color.Black),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("♪", style = MaterialTheme.typography.displayLarge)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = playlist?.name.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    playlist?.description?.let { desc ->
                        if (desc.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.LightGray
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${tracks.size} songs • ${formatDuration(tracks.sumOf { it.duration })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            // Track list
            items(tracks) { track ->
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
                    onDelete = { viewModel.removeTrack(track) },
                    onAddToOtherPlaylist = {
                        targetSong = track
                        showPicker = true
                    }
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) "${hours} hr ${remainingMinutes} min" else "$minutes min"
}