package com.aron.mediaplayer.ui.components

import androidx.compose.runtime.Composable
import com.aron.mediaplayer.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SharedPlaylistPicker(
    showPicker: Boolean,
    targetSong: PlaylistTrack?,
    playlists: List<PlaylistEntity>,
    dao: PlaylistDao,
    activeStore: ActivePlaylistStore,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    if (showPicker && targetSong != null) {
        PlaylistPickerDialog(
            playlists = playlists,
            onCreateNew = { name ->
                val song = targetSong
                onDismiss()
                scope.launch(Dispatchers.IO) {
                    val newId = dao.insertPlaylist(PlaylistEntity(name = name))
                    song?.let { s -> dao.insertTrack(s.copy(playlistId = newId)) }
                    activeStore.setActivePlaylistId(newId)
                }
            },
            onSelect = { playlist ->
                val song = targetSong
                onDismiss()
                scope.launch(Dispatchers.IO) {
                    song?.let { s -> dao.insertTrack(s.copy(playlistId = playlist.playlistId)) }
                    activeStore.setActivePlaylistId(playlist.playlistId)
                }
            },
            onDismiss = onDismiss
        )
    }
}