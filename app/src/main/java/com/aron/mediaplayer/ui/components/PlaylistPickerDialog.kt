package com.aron.mediaplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aron.mediaplayer.data.PlaylistEntity

@Composable
fun PlaylistPickerDialog(
    playlists: List<PlaylistEntity>,
    onCreateNew: (String) -> Unit,
    onSelect: (PlaylistEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (playlists.isEmpty()) {
                    Text("No playlists yet")
                    Spacer(Modifier.height(8.dp))
                } else {
                    playlists.forEach { p ->
                        TextButton(
                            onClick = {
                                onSelect(p)
                                onDismiss() // instant close
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(p.name) }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("New playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = {
                    onCreateNew(newName.trim())
                    onDismiss() // instant close
                }
            ) { Text("Create & Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}