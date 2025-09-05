@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aron.mediaplayer.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.data.ActivePlaylistStore
import com.aron.mediaplayer.data.PlaylistEntity
import com.aron.mediaplayer.data.PlaylistTrack
import com.aron.mediaplayer.service.PlaybackService
import com.aron.mediaplayer.ui.components.PlaylistPickerDialog
import com.aron.mediaplayer.viewmodel.PlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.aron.mediaplayer.ui.components.PlaylistTrackItem

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

    val tracks by viewModel.getTracksForPlaylist(playlistId).collectAsState()
    val playlists by dao.getAllPlaylists().collectAsState(initial = emptyList())

    var showPicker by remember { mutableStateOf(false) }
    var targetSong by remember { mutableStateOf<PlaylistTrack?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn {
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
}