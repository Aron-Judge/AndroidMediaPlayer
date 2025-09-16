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

    val playlistId = dao.insertPlaylist(
        PlaylistEntity(name = "Imported: $folderName")
    )
    Log.d(TAG, "Created playlist ID: $playlistId")

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DATA
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

    var insertedCount = queryAndInsert(context, dao, playlistId, projection, folderPath, "[Direct]")

    if (insertedCount == 0) {
        Log.w(TAG, "No tracks found with direct query — running fallback scan")
        insertedCount = queryAndInsertFallback(context, dao, playlistId, projection, folderPath)
    }

    Log.i(TAG, "Folder import complete: $insertedCount tracks added to playlist '$folderName'")
}

private suspend fun queryAndInsert(
    context: Context,
    dao: com.aron.mediaplayer.data.PlaylistDao,
    playlistId: Long,
    projection: Array<String>,
    folderPath: String,
    logPrefix: String
): Int {
    var count = 0
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
            count++
            Log.d(TAG, "$logPrefix Inserted track: $title ($artist) -> $contentUri")
        }
    }
    return count
}

private suspend fun queryAndInsertFallback(
    context: Context,
    dao: com.aron.mediaplayer.data.PlaylistDao,
    playlistId: Long,
    projection: Array<String>,
    folderPath: String
): Int {
    var count = 0
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        "${MediaStore.Audio.Media.TITLE} ASC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

        while (cursor.moveToNext()) {
            val filePath = cursor.getString(dataCol) ?: continue
            if (!filePath.contains(folderPath, ignoreCase = true)) continue

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
            count++
            Log.d(TAG, "[Fallback] Inserted track: $title ($artist) -> $contentUri")
        }
    }
    return count
}