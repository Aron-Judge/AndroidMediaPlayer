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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aron.mediaplayer.service.PlaybackService

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val contentUri: Uri
)

@Composable
fun SongsScreen() {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

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
                SongItem(song, context)
            }
        }
    }
}

@Composable
fun SongItem(song: Song, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(context, PlaybackService::class.java).apply {
                    putExtra("mediaUri", song.contentUri.toString())
                }
                ContextCompat.startForegroundService(context, intent)
            }
            .padding(vertical = 8.dp)
    ) {
        Text(text = song.title, style = MaterialTheme.typography.titleMedium)
        Text(text = song.artist, style = MaterialTheme.typography.bodyMedium)
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