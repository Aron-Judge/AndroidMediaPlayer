package com.aron.mediaplayer.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.data.PlaylistEntity
import com.aron.mediaplayer.data.PlaylistTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FolderImport"

suspend fun importFolderAsPlaylist(context: Context, folderUri: Uri) = withContext(Dispatchers.IO) {
    val dao = AppDatabase.getInstance(context).playlistDao()

    val folderName = DocumentFile.fromTreeUri(context, folderUri)?.name ?: "Folder"
    Log.d(TAG, "Starting folder import for: $folderName ($folderUri)")

    // Create playlist
    val playlistId = dao.insertPlaylist(
        PlaylistEntity(name = "Imported: $folderName")
    )
    Log.d(TAG, "Created playlist ID: $playlistId")

    // Query MediaStore for audio files in this folder
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DATA // deprecated but still works for filtering
    )

    val folderPath = DocumentFile.fromTreeUri(context, folderUri)
        ?.uri
        ?.path
        ?.substringAfter("/document/primary:")
        ?.replace(":", "/")
        ?: run {
            Log.w(TAG, "Could not resolve folder path from URI")
            return@withContext
        }

    Log.d(TAG, "Resolved folder path for query: $folderPath")

    var insertedCount = 0

    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?",
        arrayOf("%$folderPath%"),
        "${MediaStore.Audio.Media.TITLE} ASC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol) ?: "Unknown Title"
            val artist = cursor.getString(artistCol) ?: "Unknown Artist"
            val duration = cursor.getLong(durationCol)
            val albumId = cursor.getLong(albumIdCol)

            val contentUri = Uri.withAppendedPath(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id.toString()
            )

            val artworkUri = Uri.withAppendedPath(
                Uri.parse("content://media/external/audio/albumart"),
                albumId.toString()
            ).toString()

            dao.insertTrack(
                PlaylistTrack(
                    playlistId = playlistId,
                    uri = contentUri.toString(),
                    title = title,
                    artist = artist,
                    duration = duration,
                    artworkUri = artworkUri
                )
            )

            insertedCount++
            Log.d(TAG, "Inserted track: $title ($artist) -> $contentUri")
        }
    }

    Log.i(TAG, "Folder import complete: $insertedCount tracks added to playlist '$folderName'")
}