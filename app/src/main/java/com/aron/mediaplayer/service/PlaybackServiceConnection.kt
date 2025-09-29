package com.aron.mediaplayer.service

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton connection point between UI (ViewModels) and PlaybackService.
 * Exposes flows for current song, playback state, and playback position/duration.
 */
object PlaybackServiceConnection {

    // Already exposed from PlaybackService
    val currentUri: StateFlow<String?> get() = PlaybackService.currentUri
    val isPlaying: StateFlow<Boolean> get() = PlaybackService.isPlaying
    val currentSong: StateFlow<com.aron.mediaplayer.data.PlaylistTrack?> get() = PlaybackService.currentSong

    // 🔹 New: playback position and duration
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    // Coroutine scope for polling
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Reference to the ExoPlayer inside PlaybackService
    private val player: ExoPlayer?
        get() = PlaybackService.player

    init {
        // Poll position/duration every 500ms
        scope.launch {
            while (isActive) {
                player?.let {
                    _positionMs.value = it.currentPosition
                    _durationMs.value = it.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    // Controls
    fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun skipToNext() {
        player?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        player?.seekToPreviousMediaItem()
    }
}