package com.aron.mediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aron.mediaplayer.data.PlaylistTrack
import com.aron.mediaplayer.service.PlaybackServiceConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NowPlayingViewModel : ViewModel() {

    // Currently playing track URI
    val currentUri: StateFlow<String?> = PlaybackServiceConnection.currentUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Whether playback is active
    val isPlaying: StateFlow<Boolean> = PlaybackServiceConnection.isPlaying
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Full track metadata (title, artist, artwork)
    val currentSong: StateFlow<PlaylistTrack?> = PlaybackServiceConnection.currentSong
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Control methods
    fun togglePlayPause() = PlaybackServiceConnection.togglePlayPause()
}