package com.aron.mediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aron.mediaplayer.data.PlaylistDao
import com.aron.mediaplayer.data.PlaylistEntity
import com.aron.mediaplayer.data.PlaylistTrack
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val dao: PlaylistDao
) : ViewModel() {

    // All playlists
    val playlists: StateFlow<List<PlaylistEntity>> =
        dao.getAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tracks for the currently selected playlist
    fun getTracksForPlaylist(playlistId: Long): StateFlow<List<PlaylistTrack>> =
        dao.getTracksForPlaylist(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTrack(track: PlaylistTrack) {
        viewModelScope.launch {
            dao.insertTrack(track)
        }
    }

    fun removeTrack(track: PlaylistTrack) {
        viewModelScope.launch {
            dao.deleteTrack(track)
        }
    }

    fun clearPlaylist(playlistId: Long) {
        viewModelScope.launch {
            dao.clearPlaylist(playlistId)
        }
    }

    fun addPlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            dao.insertPlaylist(playlist)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            dao.deletePlaylist(playlist)
        }
    }
}