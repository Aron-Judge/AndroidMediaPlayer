@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.aron.mediaplayer.service

import kotlinx.coroutines.flow.StateFlow

object PlaybackServiceConnection {
    val currentUri: StateFlow<String?> = PlaybackService.currentUri
}