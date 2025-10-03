package com.aron.mediaplayer.data

import androidx.room.*

@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId")
    ]
)
data class PlaylistTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,            // which playlist it belongs to
    val uri: String,                 // content:// URI for the track
    val title: String,
    val artist: String,
    val duration: Long,
    val artworkUri: String? = null,
    val position: Int = 0                // 👈 explicit order within playlist
)