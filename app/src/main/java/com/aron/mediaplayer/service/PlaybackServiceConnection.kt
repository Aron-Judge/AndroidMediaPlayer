package com.aron.mediaplayer.service

import com.aron.mediaplayer.data.PlaylistTrack
import kotlinx.coroutines.flow.StateFlow

object PlaybackServiceConnection {
    // Forward the flows from PlaybackService
    val currentUri: StateFlow<String?> = PlaybackService.currentUri
    val isPlaying: StateFlow<Boolean> = PlaybackService.isPlaying
    val currentSong: StateFlow<PlaylistTrack?> = PlaybackService.currentSong

    // Forward the control method
    fun togglePlayPause() = PlaybackService.togglePlayPause()
}