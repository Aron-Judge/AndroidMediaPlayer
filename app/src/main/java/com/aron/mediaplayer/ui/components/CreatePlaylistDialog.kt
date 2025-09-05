package com.aron.mediaplayer.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun CreatePlaylistDialog(
    onCreate: (String, String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var coverUri by remember { mutableStateOf<String?>(null) }

    // Use OpenDocument so we can persist URI permission
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            coverUri = it.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Playlist") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (!coverUri.isNullOrBlank()) {
                        AsyncImage(
                            model = coverUri,
                            contentDescription = "Playlist cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("No cover selected", color = Color.LightGray)
                    }
                }

                Button(
                    onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Choose Cover Image")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        name.text.trim(),
                        description.text.trim().ifBlank { null },
                        coverUri
                    )
                    onDismiss()
                },
                enabled = name.text.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}