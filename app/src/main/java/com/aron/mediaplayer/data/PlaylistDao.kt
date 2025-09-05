package com.aron.mediaplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val playlistId: Long,
    val name: String,
    val trackCount: Int
)

@Dao
interface PlaylistDao {

    // — Playlists —
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE playlistId = :id LIMIT 1")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    // NEW: update only description
    @Query("UPDATE playlists SET description = :description WHERE playlistId = :playlistId")
    suspend fun updatePlaylistDescription(playlistId: Long, description: String?)

    // NEW: update only cover image
    @Query("UPDATE playlists SET coverUri = :coverUri WHERE playlistId = :playlistId")
    suspend fun updatePlaylistCover(playlistId: Long, coverUri: String?)

    // — Tracks in a playlist —
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY id ASC")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<PlaylistTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrack): Long

    @Delete
    suspend fun deleteTrack(track: PlaylistTrack)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    // — Playlists with track counts —
    @Query("""
        SELECT p.playlistId, p.name, COUNT(t.id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks t ON p.playlistId = t.playlistId
        GROUP BY p.playlistId
        ORDER BY p.name COLLATE NOCASE ASC
    """)
    fun getPlaylistsWithCount(): Flow<List<PlaylistWithCount>>
}