package com.aron.mediaplayer.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aron.mediaplayer.data.PlaylistDao
import com.aron.mediaplayer.data.PlaylistEntity
import com.aron.mediaplayer.data.PlaylistTrack
import com.aron.mediaplayer.data.PlaylistWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(private val dao: PlaylistDao) : ViewModel() {

    val playlists: StateFlow<List<PlaylistEntity>> =
        dao.getAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistsWithCount: StateFlow<List<PlaylistWithCount>> =
        dao.getPlaylistsWithCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getTracksForPlaylist(playlistId: Long): StateFlow<List<PlaylistTrack>> =
        dao.getTracksForPlaylist(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTrack(track: PlaylistTrack) = viewModelScope.launch { dao.insertTrack(track) }
    fun removeTrack(track: PlaylistTrack) = viewModelScope.launch { dao.deleteTrack(track) }
    fun clearPlaylist(playlistId: Long) = viewModelScope.launch { dao.clearPlaylist(playlistId) }
    fun addPlaylist(playlist: PlaylistEntity) = viewModelScope.launch { dao.insertPlaylist(playlist) }
    fun deletePlaylist(playlist: PlaylistEntity) = viewModelScope.launch { dao.deletePlaylist(playlist) }

    /**
     * Returns playlists that either have no coverUri or have a broken/inaccessible URI.
     * This is used by MainActivity to trigger the MissingCoversDialog.
     */
    fun getPlaylistsNeedingCover(context: Context): Flow<List<PlaylistEntity>> {
        return dao.getAllPlaylistsFlow().map { list ->
            list.filter { playlist ->
                playlist.coverUri.isNullOrBlank() || !isUriAccessible(context, playlist.coverUri)
            }
        }
    }

    private fun isUriAccessible(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}