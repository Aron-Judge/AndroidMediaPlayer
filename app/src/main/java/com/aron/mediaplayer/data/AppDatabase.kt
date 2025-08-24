package com.aron.mediaplayer.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [PlaylistEntity::class, PlaylistTrack::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) Create playlists table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlists (
                        playlistId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                """.trimIndent())

                // 2) Insert default playlist
                db.execSQL("INSERT INTO playlists (name) VALUES ('My Playlist')")
                // Get its id
                val cursor = db.query("SELECT playlistId FROM playlists WHERE name = 'My Playlist' LIMIT 1")
                var defaultId = 1L
                if (cursor.moveToFirst()) defaultId = cursor.getLong(0)
                cursor.close()

                // 3) Rename old table → temp (schema from v1: id, uri, title, artist, duration, artworkUri)
                db.execSQL("ALTER TABLE playlist_tracks RENAME TO playlist_tracks_old")

                // 4) Create new playlist_tracks with playlistId + FK + index
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlist_tracks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        playlistId INTEGER NOT NULL,
                        uri TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        artworkUri TEXT,
                        FOREIGN KEY(playlistId) REFERENCES playlists(playlistId) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId ON playlist_tracks(playlistId)")

                // 5) Copy rows into default playlist
                db.execSQL("""
                    INSERT INTO playlist_tracks (playlistId, uri, title, artist, duration, artworkUri)
                    SELECT $defaultId, uri, title, artist, duration, artworkUri
                    FROM playlist_tracks_old
                """.trimIndent())

                // 6) Drop temp
                db.execSQL("DROP TABLE playlist_tracks_old")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "media_player_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}