@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.aron.mediaplayer.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.aron.mediaplayer.data.ActivePlaylistStore
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.data.PlaylistEntity
import com.aron.mediaplayer.data.PlaylistTrack
import com.aron.mediaplayer.service.PlaybackService
import com.aron.mediaplayer.ui.components.FastScroller
import com.aron.mediaplayer.ui.components.PlaylistManageDialogs
import com.aron.mediaplayer.ui.components.PlaylistTrackItem
import com.aron.mediaplayer.ui.components.SharedPlaylistPicker
import com.aron.mediaplayer.viewmodel.NowPlayingViewModel
import com.aron.mediaplayer.viewmodel.PlaylistViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel

@UnstableApi
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    viewModel: PlaylistViewModel,
    listState: LazyListState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context) }
    val activeStore = remember { ActivePlaylistStore(context, dao.playlistDao()) }
    val scope = rememberCoroutineScope()

    // Load playlist metadata once per ID
    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    val playlist by viewModel.playlist.collectAsState()
    val tracks by viewModel.tracksForPlaylistLive(playlistId).collectAsState()
    val searchText by viewModel.getSearchQuery().collectAsState()
    val playlists by dao.playlistDao().getAllPlaylists().collectAsState(initial = emptyList())

    // Now playing state for highlighting current track
    val nowPlayingViewModel: NowPlayingViewModel = viewModel()
    val currentPlayingUri by nowPlayingViewModel.currentUri.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    var targetSong by remember { mutableStateOf<PlaylistTrack?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTrackOptionsSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {

            // Compact header (Spotify-style)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Back + menu row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        if (playlist != null) {
                            var menuExpanded by remember { mutableStateOf(false) }

                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options"
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit details") },
                                    onClick = {
                                        showEditDialog = true
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete playlist") },
                                    onClick = {
                                        showDeleteDialog = true
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val artworkToShow = playlist?.coverUri ?: tracks.firstOrNull()?.artworkUri
                    if (!artworkToShow.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkToShow,
                            contentDescription = "Playlist artwork",
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(8.dp))
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
                        text = "${tracks.size} songs • ${
                            formatDuration(
                                tracks.sumOf { it.duration }
                            )
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by song or artist") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null
                            )
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }

            // Empty state
            item {
                if (tracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (searchText.isBlank()) Icons.Default.MusicNote else Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            val message = if (searchText.isBlank()) {
                                "This playlist is empty"
                            } else {
                                "No results found for \"$searchText\""
                            }
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // Track list
            itemsIndexed(
                tracks,
                key = { _, track -> track.id }
            ) { _, track ->
                PlaylistTrackItem(
                    track = track,
                    isPlaying = track.uri == currentPlayingUri,
                    onPlay = {
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_PLAY
                            putExtra(PlaybackService.EXTRA_URI, track.uri)
                            putExtra(PlaybackService.EXTRA_PLAYLIST_ID, playlistId)
                        }
                        ContextCompat.startForegroundService(context, intent)
                    },
                    onMenuClick = {
                        targetSong = track
                        showTrackOptionsSheet = true
                    },
                    searchQuery = searchText,
                    modifier = Modifier.animateItemPlacement()
                )
            }
        }

        FastScroller(
            listState = listState,
            itemCount = tracks.size,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
        )
    }

    if (showTrackOptionsSheet && targetSong != null) {
        ModalBottomSheet(
            onDismissRequest = { showTrackOptionsSheet = false }
        ) {
            ListItem(
                headlineContent = { Text("Remove from this playlist") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.removeTrack(targetSong!!)
                        showTrackOptionsSheet = false
                    }
                    .padding(vertical = 12.dp)
            )

            ListItem(
                headlineContent = { Text("Add to another playlist") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showTrackOptionsSheet = false
                        showPicker = true
                    }
                    .padding(vertical = 12.dp)
            )

            Spacer(Modifier.height(12.dp))
        }
    }

    SharedPlaylistPicker(
        showPicker = showPicker,
        targetSong = targetSong,
        playlists = playlists,
        dao = dao.playlistDao(),
        activeStore = activeStore,
        scope = scope,
        onDismiss = { showPicker = false }
    )

    PlaylistManageDialogs(
        playlistToEdit = if (showEditDialog) playlist else null,
        onEditDismiss = { showEditDialog = false },
        playlistToDelete = if (showDeleteDialog) playlist else null,
        onDeleteDismiss = { showDeleteDialog = false },
        dao = dao,
        scope = scope,
        onDeleted = { onBack() }
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) "${hours} hr ${remainingMinutes} min" else "$minutes min"
}