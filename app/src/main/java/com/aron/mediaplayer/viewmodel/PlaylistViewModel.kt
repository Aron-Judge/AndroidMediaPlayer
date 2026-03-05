package com.aron.mediaplayer.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aron.mediaplayer.data.PlaylistDao
import com.aron.mediaplayer.data.PlaylistEntity
import com.aron.mediaplayer.data.PlaylistTrack
import com.aron.mediaplayer.data.PlaylistWithCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlaylistViewModel(private val dao: PlaylistDao) : ViewModel() {

    // Search query state for live filtering
    private val searchQuery = MutableStateFlow("")
    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }
    fun getSearchQuery(): StateFlow<String> = searchQuery.asStateFlow()

    // Playlist list
    val playlists: StateFlow<List<PlaylistEntity>> =
        dao.getAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistsWithCount: StateFlow<List<PlaylistWithCount>> =
        dao.getPlaylistsWithCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Single playlist metadata (name, description, cover)
    private val _playlist = MutableStateFlow<PlaylistEntity?>(null)
    val playlist: StateFlow<PlaylistEntity?> = _playlist.asStateFlow()

    fun loadPlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _playlist.value = dao.getPlaylistById(playlistId)
        }
    }

    // Cache of live track flows per playlist to avoid recreating flows on recomposition
    private val trackFlows =
        mutableMapOf<Long, StateFlow<List<PlaylistTrack>>>()

    fun tracksForPlaylistLive(playlistId: Long): StateFlow<List<PlaylistTrack>> {
        return trackFlows.getOrPut(playlistId) {
            searchQuery
                .debounce(150)
                .map { it.trim() }
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    if (q.isEmpty()) {
                        dao.getTracksForPlaylist(playlistId)
                    } else {
                        dao.searchTracksInPlaylist(playlistId, q)
                    }
                }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    emptyList()
                )
        }
    }

    // Legacy unfiltered version if you still need it elsewhere
    fun getTracksForPlaylist(playlistId: Long): StateFlow<List<PlaylistTrack>> =
        dao.getTracksForPlaylist(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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