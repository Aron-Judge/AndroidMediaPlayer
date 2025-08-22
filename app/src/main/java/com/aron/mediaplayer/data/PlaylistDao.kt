package com.aron.mediaplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // Live stream of the entire playlist
    @Query("SELECT * FROM playlist_tracks")
    fun getAll(): Flow<List<PlaylistTrack>>

    // Add or replace a single track
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: PlaylistTrack)

    // Remove a single track
    @Delete
    suspend fun delete(track: PlaylistTrack)

    // Clear the entire playlist
    @Query("DELETE FROM playlist_tracks")
    suspend fun clear()
}