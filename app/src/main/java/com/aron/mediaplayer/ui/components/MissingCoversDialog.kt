package com.aron.mediaplayer.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.aron.mediaplayer.data.PlaylistEntity

@Composable
fun MissingCoversDialog(
    playlists: List<PlaylistEntity>,
    onCoverSelected: (Long, Uri) -> Unit
) {
    val context = LocalContext.current
    var currentIndex by remember { mutableStateOf(0) }

    if (currentIndex < playlists.size) {
        val playlist = playlists[currentIndex]
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                onCoverSelected(playlist.playlistId, it)
                currentIndex++
            }
        }

        AlertDialog(
            onDismissRequest = { /* force completion */ },
            title = { Text("Restore cover image") },
            text = { Text("Please select a cover image for \"${playlist.name}\"") },
            confirmButton = {
                TextButton(onClick = { launcher.launch(arrayOf("image/*")) }) {
                    Text("Choose Image")
                }
            }
        )
    }
}