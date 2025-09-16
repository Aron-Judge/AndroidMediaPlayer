package com.aron.mediaplayer.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.data.PlaylistEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun PlaylistManageDialogs(
    playlistToEdit: PlaylistEntity?,
    onEditDismiss: () -> Unit,
    playlistToDelete: PlaylistEntity?,
    onDeleteDismiss: () -> Unit,
    dao: AppDatabase,
    scope: CoroutineScope,
    onDeleted: (() -> Unit)? = null
) {
    if (playlistToEdit != null) {
        EditPlaylistDialog(
            playlist = playlistToEdit,
            onSave = { newName, newDesc, newCover ->
                scope.launch(Dispatchers.IO) {
                    dao.playlistDao().updatePlaylist(
                        playlistToEdit.copy(
                            name = newName,
                            description = newDesc,
                            coverUri = newCover
                        )
                    )
                }
                onEditDismiss()
            },
            onDismiss = onEditDismiss
        )
    }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete \"${playlistToDelete.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            dao.playlistDao().deletePlaylist(playlistToDelete)
                            onDeleteDismiss()
                            onDeleted?.invoke()
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}