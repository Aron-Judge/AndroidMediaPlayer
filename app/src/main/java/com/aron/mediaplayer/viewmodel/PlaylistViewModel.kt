package com.aron.mediaplayer.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aron.mediaplayer.data.PlaylistDao
import com.aron.mediaplayer.data.PlaylistEntity
import com.aron.mediaplayer.data.PlaylistTrack
import com.aron.mediaplayer.data.PlaylistWithCount
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlaylistViewModel(private val dao: PlaylistDao) : ViewModel() {

    // ✅ Search query state for live filtering
    private val searchQuery = MutableStateFlow("")
    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }
    fun getSearchQuery(): StateFlow<String> = searchQuery.asStateFlow()

    val playlists: StateFlow<List<PlaylistEntity>> =
        dao.getAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistsWithCount: StateFlow<List<PlaylistWithCount>> =
        dao.getPlaylistsWithCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Existing: unfiltered playlist tracks
    fun getTracksForPlaylist(playlistId: Long): StateFlow<List<PlaylistTrack>> =
        dao.getTracksForPlaylist(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ✅ New: live search version — matches title OR artist, updates as user types
    fun getTracksForPlaylistLive(playlistId: Long): StateFlow<List<PlaylistTrack>> {
        return searchQuery
            .debounce(150) // smooth typing
            .map { it.trim() }
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.isEmpty()) {
                    dao.getTracksForPlaylist(playlistId)
                        .distinctUntilChanged()   // 👈 prevent redundant emissions
                } else {
                    dao.searchTracksInPlaylist(playlistId, q)
                        .distinctUntilChanged()   // 👈 same here
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun addTrack(track: PlaylistTrack) = viewModelScope.launch { dao.insertTrack(track) }
    fun removeTrack(track: PlaylistTrack) = viewModelScope.launch { dao.deleteTrack(track) }
    fun clearPlaylist(playlistId: Long) = viewModelScope.launch { dao.clearPlaylist(playlistId) }
    fun addPlaylist(playlist: PlaylistEntity) = viewModelScope.launch { dao.insertPlaylist(playlist) }
    fun deletePlaylist(playlist: PlaylistEntity) = viewModelScope.launch { dao.deletePlaylist(playlist) }

    /**
     * Returns playlists that either have no coverUri or have a broken/inaccessible URI.
     * This is used by MainActivity to trigger the MissingCoversDialog.
     */
    fun getPlaylistsNeedingCover(context: Context): Flow<List<PlaylistEntity>> {
        return dao.getAllPlaylistsFlow().map { list ->
            list.filter { playlist ->
                playlist.coverUri.isNullOrBlank() || !isUriAccessible(context, playlist.coverUri)
            }
        }
    }

    private fun isUriAccessible(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}