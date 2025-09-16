package com.aron.mediaplayer.ui.screens

import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aron.mediaplayer.data.*
import com.aron.mediaplayer.ui.components.CreatePlaylistDialog
import com.aron.mediaplayer.ui.components.PlaylistManageDialogs
import com.aron.mediaplayer.util.importFolderAsPlaylist
import kotlinx.coroutines.launch

@Composable
fun PlaylistListScreen(
    playlists: List<PlaylistWithCount>,
    onPlaylistClick: (Long) -> Unit,
    onCreatePlaylist: (String, String?, String?) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistToEdit by remember { mutableStateOf<PlaylistEntity?>(null) }
    var menuExpandedFor by remember { mutableStateOf<Long?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch { importFolderAsPlaylist(context, it) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(playlists) { playlist ->
                val entity by produceState<PlaylistEntity?>(initialValue = null, playlist.playlistId) {
                    value = dao.playlistDao().getPlaylistById(playlist.playlistId)
                }
                val firstTrack by dao.playlistDao().getTracksForPlaylist(playlist.playlistId)
                    .collectAsState(initial = emptyList<PlaylistTrack>())

                val cover = entity?.coverUri ?: firstTrack.firstOrNull()?.artworkUri

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaylistClick(playlist.playlistId) }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!cover.isNullOrBlank()) {
                                AsyncImage(
                                    model = cover,
                                    contentDescription = "Playlist artwork",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(Color.DarkGray),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("♪", style = MaterialTheme.typography.titleLarge)
                                }
                            }

                            Spacer(Modifier.width(16.dp))

                            Column {
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                Text("${playlist.trackCount} songs", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        // Three-dot menu
                        Box {
                            IconButton(onClick = { menuExpandedFor = playlist.playlistId }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpandedFor == playlist.playlistId,
                                onDismissRequest = { menuExpandedFor = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit details") },
                                    onClick = {
                                        entity?.let { playlistToEdit = it }
                                        menuExpandedFor = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete playlist") },
                                    onClick = {
                                        entity?.let { playlistToDelete = it }
                                        menuExpandedFor = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // FABs
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New Playlist", tint = Color.White)
        }

        FloatingActionButton(
            onClick = { folderPickerLauncher.launch(null) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.secondary
        ) {
            Icon(Icons.Filled.Folder, contentDescription = "Import Folder", tint = Color.White)
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onCreate = { newName, newDesc, newCover ->
                onCreatePlaylist(newName, newDesc, newCover)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // Shared edit/delete dialogs
    PlaylistManageDialogs(
        playlistToEdit = playlistToEdit,
        onEditDismiss = { playlistToEdit = null },
        playlistToDelete = playlistToDelete,
        onDeleteDismiss = { playlistToDelete = null },
        dao = dao,
        scope = scope
    )
}