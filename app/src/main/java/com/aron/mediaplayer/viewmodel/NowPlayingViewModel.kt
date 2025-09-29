package com.aron.mediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aron.mediaplayer.data.PlaylistTrack
import com.aron.mediaplayer.service.PlaybackServiceConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NowPlayingViewModel : ViewModel() {

    val currentUri: StateFlow<String?> = PlaybackServiceConnection.currentUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isPlaying: StateFlow<Boolean> = PlaybackServiceConnection.isPlaying
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currentSong: StateFlow<PlaylistTrack?> = PlaybackServiceConnection.currentSong
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 🔹 Playback position and duration
    val positionMs: StateFlow<Long> = PlaybackServiceConnection.positionMs
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val durationMs: StateFlow<Long> = PlaybackServiceConnection.durationMs
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Controls
    fun togglePlayPause() = PlaybackServiceConnection.togglePlayPause()
    fun seekTo(position: Long) = PlaybackServiceConnection.seekTo(position)
    fun skipToNext() = PlaybackServiceConnection.skipToNext()
    fun skipToPrevious() = PlaybackServiceConnection.skipToPrevious()
}