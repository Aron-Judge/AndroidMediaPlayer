package com.aron.mediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aron.mediaplayer.data.PlaylistDao
import com.aron.mediaplayer.data.PlaylistTrack
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val dao: PlaylistDao
) : ViewModel() {

    // Live-updating playlist
    val playlist: StateFlow<List<PlaylistTrack>> =
        dao.getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTrack(track: PlaylistTrack) {
        viewModelScope.launch {
            dao.insert(track)
        }
    }

    fun removeTrack(track: PlaylistTrack) {
        viewModelScope.launch {
            dao.delete(track)
        }
    }

    fun clearPlaylist() {
        viewModelScope.launch {
            dao.clear()
        }
    }
}