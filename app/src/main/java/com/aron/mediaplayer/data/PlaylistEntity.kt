package com.aron.mediaplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val description: String? = null,   // NEW: optional description
    val coverUri: String? = null       // NEW: optional custom cover image
)