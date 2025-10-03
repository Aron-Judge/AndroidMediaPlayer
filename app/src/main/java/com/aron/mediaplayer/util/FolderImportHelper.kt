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

// Regex to capture leading digits like "001", "12", etc.
private val prefixRegex = Regex("""^(\d+)\s*[-_ ]?.*""")

private fun extractSortKey(fileName: String): Pair<Int, String> {
    val match = prefixRegex.find(fileName)
    val number = match?.groupValues?.get(1)?.toIntOrNull()
    return if (number != null) {
        number to fileName.lowercase()
    } else {
        Int.MAX_VALUE to fileName.lowercase()
    }
}

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
    val tracks = mutableListOf<Pair<PlaylistTrack, String>>() // track + filename

    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?",
        arrayOf("%$folderPath%"),
        null // no ORDER BY, we’ll sort ourselves
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

        while (cursor.moveToNext()) {
            val filePath = cursor.getString(dataCol) ?: continue
            val fileName = filePath.substringAfterLast('/')

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

            tracks.add(
                PlaylistTrack(
                    playlistId = playlistId,
                    uri = contentUri.toString(),
                    title = title,
                    artist = artist,
                    duration = duration,
                    artworkUri = artworkUri
                ) to fileName
            )
        }
    }

    val sorted = tracks.sortedWith(compareBy(
        { extractSortKey(it.second).first },
        { extractSortKey(it.second).second }
    ))

    sorted.forEachIndexed { index, (track, fileName) ->
        dao.insertTrack(track)
        Log.d(TAG, "$logPrefix Inserted track #$index: ${track.title} (${track.artist}) from $fileName")
    }

    return sorted.size
}

private suspend fun queryAndInsertFallback(
    context: Context,
    dao: com.aron.mediaplayer.data.PlaylistDao,
    playlistId: Long,
    projection: Array<String>,
    folderPath: String
): Int {
    val tracks = mutableListOf<Pair<PlaylistTrack, String>>() // track + filename

    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        null // no ORDER BY, we’ll sort ourselves
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
            val fileName = filePath.substringAfterLast('/')

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

            tracks.add(
                PlaylistTrack(
                    playlistId = playlistId,
                    uri = contentUri.toString(),
                    title = title,
                    artist = artist,
                    duration = duration,
                    artworkUri = artworkUri
                ) to fileName
            )
        }
    }

    val sorted = tracks.sortedWith(compareBy(
        { extractSortKey(it.second).first },
        { extractSortKey(it.second).second }
    ))

    sorted.forEachIndexed { index, (track, fileName) ->
        dao.insertTrack(track)
        Log.d(TAG, "[Fallback] Inserted track #$index: ${track.title} (${track.artist}) from $fileName")
    }

    return sorted.size
}