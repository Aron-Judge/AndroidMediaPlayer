package com.aron.mediaplayer.service

import androidx.media3.exoplayer.ExoPlayer
import com.aron.mediaplayer.data.PlaylistTrack
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Singleton connection point between UI (ViewModels) and PlaybackService.
 * Exposes flows for current song, playback state, playback position/duration, and queue.
 */
object PlaybackServiceConnection {

    // Already exposed from PlaybackService
    val currentUri: StateFlow<String?> get() = PlaybackService.currentUri
    val isPlaying: StateFlow<Boolean> get() = PlaybackService.isPlaying
    val currentSong: StateFlow<PlaylistTrack?> get() = PlaybackService.currentSong

    // Queue state
    val queue: StateFlow<List<PlaylistTrack>> get() = PlaybackService.queue

    // Playback position and duration
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    // Coroutine scope for polling
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Reference to the ExoPlayer inside PlaybackService
    private val player: ExoPlayer?
        get() = PlaybackService.player

    private val service: PlaybackService?
        get() = PlaybackService.instance

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

    // Playback controls
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

    // Queue controls

    // Add to queue
    fun addToQueue(track: PlaylistTrack) {
        service?.addToQueue(track)
    }

    // Play next
    fun playNext(track: PlaylistTrack) {
        service?.playNext(track)
    }

    fun clearQueue() {
        service?.clearQueue()
    }

    fun removeFromQueue(trackId: Long) {
        service?.removeFromQueue(trackId)
    }
}
