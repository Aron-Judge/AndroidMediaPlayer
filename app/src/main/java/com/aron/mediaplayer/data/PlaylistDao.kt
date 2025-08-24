package com.aron.mediaplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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

    // — Tracks in a playlist —
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY id ASC")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<PlaylistTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrack): Long

    @Delete
    suspend fun deleteTrack(track: PlaylistTrack)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)
}