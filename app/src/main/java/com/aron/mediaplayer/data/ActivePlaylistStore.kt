package com.aron.mediaplayer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull

private val Context.dataStore by preferencesDataStore(name = "settings")
private const val REQUEST_CODE_READ_AUDIO = 1001

class ActivePlaylistStore(private val context: Context, private val dao: PlaylistDao) {
    companion object {
        private val KEY_ACTIVE_PLAYLIST_ID = longPreferencesKey("active_playlist_id")
    }

    val activePlaylistId: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PLAYLIST_ID] ?: -1L
    }

    suspend fun ensureDefaultPlaylistSelected(): Long {
        val current = activePlaylistId.first()
        if (current > 0) return current

        // Ensure default exists
        val existing = dao.getAllPlaylists().firstOrNull()
        val id = if (existing.isNullOrEmpty()) {
            dao.insertPlaylist(PlaylistEntity(name = "My Playlist"))
        } else {
            existing.first().playlistId
        }
        setActivePlaylistId(id)
        return id
    }

    suspend fun setActivePlaylistId(id: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PLAYLIST_ID] = id
        }
    }
}