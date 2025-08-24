@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.aron.mediaplayer.ui.songs

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.data.PlaylistTrack
import com.aron.mediaplayer.service.PlaybackService
import com.aron.mediaplayer.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val contentUri: Uri
)

@Composable
fun SongsScreen(
    nowPlayingViewModel: NowPlayingViewModel = viewModel()
) {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    // Observe current URI from shared ViewModel
    val currentPlayingUri by nowPlayingViewModel.currentUri.collectAsState()

    val mediaPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val notifPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null

    val launcherMultiple = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val mediaGranted = results[mediaPermission] == true
        val notifGranted = notifPermission?.let { results[it] == true } ?: true
        hasPermission = mediaGranted && notifGranted
        if (hasPermission) songs = loadSongs(context)
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, mediaPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(mediaPermission)
        }
        if (notifPermission != null &&
            ContextCompat.checkSelfPermission(context, notifPermission) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(notifPermission)
        }
        if (permissionsToRequest.isNotEmpty()) {
            launcherMultiple.launch(permissionsToRequest.toTypedArray())
        } else {
            hasPermission = true
            songs = loadSongs(context)
        }
    }

    if (!hasPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Permissions required to display songs and post notifications")
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            items(songs) { song ->
                SongItem(
                    song = song,
                    isPlaying = song.contentUri.toString() == currentPlayingUri,
                    onPlay = {
                        // Insert into playlist DB
                        val dao = AppDatabase.getInstance(context).playlistDao()
                        CoroutineScope(Dispatchers.IO).launch {
                            dao.insert(
                                PlaylistTrack(
                                    uri = song.contentUri.toString(),
                                    title = song.title,
                                    artist = song.artist,
                                    duration = 0L,
                                    artworkUri = null
                                )
                            )
                        }
                        // Play in service
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_PLAY
                            putExtra(PlaybackService.EXTRA_URI, song.contentUri.toString())
                        }
                        ContextCompat.startForegroundService(context, intent)
                    }
                )
            }
        }
    }
}

@Composable
fun SongItem(song: Song, isPlaying: Boolean, onPlay: () -> Unit) {
    val highlight = Color(0xFF00EEFF)
    val bgColor = if (isPlaying) highlight.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isPlaying) highlight else MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .background(bgColor)
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Text(song.title, style = MaterialTheme.typography.titleMedium, color = textColor)
        Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}

private fun loadSongs(context: Context): List<Song> {
    val songList = mutableListOf<Song>()
    val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    context.contentResolver.query(
        collection,
        projection,
        selection,
        null,
        "${MediaStore.Audio.Media.TITLE} ASC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol) ?: "Unknown Title"
            val artist = cursor.getString(artistCol) ?: "Unknown Artist"
            val contentUri = ContentUris.withAppendedId(collection, id)
            songList.add(Song(id, title, artist, contentUri))
        }
    }
    return songList
}