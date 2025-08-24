// app/src/main/java/com/aron/mediaplayer/data/Song.kt
package com.aron.mediaplayer.data

import android.net.Uri

data class Song(
    val contentUri: Uri,
    val title: String,
    val artist: String,
    val duration: Long,
    val artworkUri: String? = null
)