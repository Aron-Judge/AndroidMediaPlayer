package com.aron.mediaplayer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.data.PlaylistWithCount
import com.aron.mediaplayer.data.PlaylistTrack
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun PlaylistListScreen(
    playlists: List<PlaylistWithCount>,
    onPlaylistClick: (Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).playlistDao() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(playlists) { playlist ->
            // Get the first track for artwork preview
            val firstTrack by dao.getTracksForPlaylist(playlist.playlistId)
                .collectAsState(initial = emptyList<PlaylistTrack>())

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist.playlistId) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (firstTrack.isNotEmpty() && !firstTrack.first().artworkUri.isNullOrBlank()) {
                        AsyncImage(
                            model = firstTrack.first().artworkUri,
                            contentDescription = "Playlist artwork",
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.DarkGray),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("♪", style = MaterialTheme.typography.titleLarge)
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column {
                        Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                        Text("${playlist.trackCount} songs", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}