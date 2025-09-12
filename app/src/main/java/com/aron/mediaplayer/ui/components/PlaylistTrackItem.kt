package com.aron.mediaplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aron.mediaplayer.data.PlaylistTrack
import java.util.Locale

@Composable
fun PlaylistTrackItem(
    track: PlaylistTrack,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onAddToOtherPlaylist: () -> Unit,
    searchQuery: String = "",
    modifier: Modifier = Modifier // ✅ added
) {
    val highlight = Color(0xFF1DB954)
    val bgColor = if (isPlaying) highlight.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isPlaying) highlight else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = modifier // ✅ apply passed modifier here
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 8.dp)
            .clickable { onPlay() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = highlightMatch(track.title, searchQuery, textColor),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = highlightMatch(track.artist, searchQuery, textColor),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = textColor)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
        TextButton(onClick = onAddToOtherPlaylist) {
            Text("Add…")
        }
    }
}

private fun highlightMatch(text: String, query: String, normalColor: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)

    val lowerText = text.lowercase(Locale.getDefault())
    val lowerQuery = query.lowercase(Locale.getDefault())
    val startIndex = lowerText.indexOf(lowerQuery)

    return if (startIndex >= 0) {
        buildAnnotatedString {
            append(text.substring(0, startIndex))
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = normalColor))
            append(text.substring(startIndex, startIndex + query.length))
            pop()
            append(text.substring(startIndex + query.length))
        }
    } else {
        AnnotatedString(text)
    }
}