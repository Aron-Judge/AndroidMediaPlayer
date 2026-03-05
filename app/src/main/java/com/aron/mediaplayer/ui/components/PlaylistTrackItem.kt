package com.aron.mediaplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aron.mediaplayer.data.PlaylistTrack
import java.util.Locale

@Composable
fun PlaylistTrackItem(
    track: PlaylistTrack,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMenuClick: () -> Unit,
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val highlightColor = Color(0xFF00EEFF)
    val bgColor = if (isPlaying) highlightColor.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isPlaying) highlightColor else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable { onPlay() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art (Spotify-style)
        AsyncImage(
            model = track.artworkUri,
            contentDescription = "${track.title} artwork",
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = highlightMatch(track.title, searchQuery, textColor),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
            Text(
                text = highlightMatch(track.artist, searchQuery, textColor),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                maxLines = 1
            )
        }

        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Track options"
            )
        }
    }
}

private fun highlightMatch(text: String, query: String, highlightColor: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)

    val lowerText = text.lowercase(Locale.getDefault())
    val lowerQuery = query.lowercase(Locale.getDefault())
    val startIndex = lowerText.indexOf(lowerQuery)

    return if (startIndex >= 0) {
        buildAnnotatedString {
            append(text.substring(0, startIndex))
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor))
            append(text.substring(startIndex, startIndex + query.length))
            pop()
            append(text.substring(startIndex + query.length))
        }
    } else {
        AnnotatedString(text)
    }
}