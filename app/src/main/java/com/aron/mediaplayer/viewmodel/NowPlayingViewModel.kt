package com.aron.mediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aron.mediaplayer.service.PlaybackServiceConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NowPlayingViewModel : ViewModel() {
    val currentUri: StateFlow<String?> = PlaybackServiceConnection.currentUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}