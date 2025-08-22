package com.aron.mediaplayer.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aron.mediaplayer.R
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.data.PlaylistTrack
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@UnstableApi
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "playback_channel"

        const val ACTION_PLAY = "com.aron.mediaplayer.PLAY"
        private const val ACTION_PAUSE = "com.aron.mediaplayer.PAUSE"
        private const val ACTION_NEXT = "com.aron.mediaplayer.NEXT"
        private const val ACTION_PREVIOUS = "com.aron.mediaplayer.PREVIOUS"

        const val ACTION_ADD_TO_PLAYLIST = "com.aron.mediaplayer.ADD_TO_PLAYLIST"
        const val EXTRA_URI = "mediaUri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_ARTWORK_URI = "artworkUri"
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    this@PlaybackService,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            addListener(playerListener)
        }
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, Class.forName("com.aron.mediaplayer.MainActivity")),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        createNotificationChannel()
    }

    override fun onDestroy() {
        player.removeListener(playerListener)
        mediaSession?.release()
        player.release()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // If queue is empty at startup, load snapshot from Flow
        if (player.mediaItemCount == 0) {
            val dao = AppDatabase.getInstance(applicationContext).playlistDao()
            val playlistTracks = runBlocking { dao.getAll().first() }

            if (playlistTracks.isNotEmpty()) {
                val mediaItems = playlistTracks.map { track ->
                    MediaItem.Builder()
                        .setUri(track.uri)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .setArtworkUri(track.artworkUri?.let { Uri.parse(it) })
                                .build()
                        )
                        .build()
                }
                player.setMediaItems(mediaItems)
                player.prepare()
                player.play()
            }
        }

        // Add a track to playlist from an external intent
        if (intent?.action == ACTION_ADD_TO_PLAYLIST) {
            val dao = AppDatabase.getInstance(applicationContext).playlistDao()
            runBlocking {
                dao.insert(
                    PlaylistTrack(
                        uri = intent.getStringExtra(EXTRA_URI) ?: return@runBlocking,
                        title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown Title",
                        artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist",
                        duration = intent.getLongExtra(EXTRA_DURATION, 0L),
                        artworkUri = intent.getStringExtra(EXTRA_ARTWORK_URI)
                    )
                )
            }
        }

        // Core control actions
        when (intent?.action) {
            ACTION_PLAY -> {
                val uri = intent.getStringExtra(EXTRA_URI)
                if (uri != null) {
                    val mediaItem = MediaItem.Builder()
                        .setUri(uri)
                        .build()
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                } else {
                    player.play()
                }
            }
            ACTION_PAUSE -> player.pause()
            ACTION_NEXT -> player.seekToNextMediaItem()
            ACTION_PREVIOUS -> handlePrevious()
            Intent.ACTION_MEDIA_BUTTON -> { /* handled by MediaSession */ }
        }

        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startForeground(NOTIFICATION_ID, buildLoadingNotification())
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun handlePrevious() {
        if (player.currentPosition > 3000) {
            player.seekTo(0)
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    private fun buildLoadingNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Loading…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun buildNotification(): Notification {
        val currentItem = player.currentMediaItem
        val mm = currentItem?.mediaMetadata

        val title = mm?.title?.toString()?.takeIf { it.isNotEmpty() } ?: "Sample Track"
        val artist = mm?.artist?.toString()?.takeIf { it.isNotEmpty() } ?: "Unknown Artist"

        val artUri: Uri = mm?.artworkUri
            ?: Uri.parse("https://via.placeholder.com/300.png?text=No+Artwork")

        var artBitmap: Bitmap? = null
        try {
            artBitmap = Glide.with(this)
                .asBitmap()
                .load(artUri)
                .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                .get()
        } catch (_: Exception) { }

        val playIntent = PendingIntent.getService(
            this, 0, Intent(this, PlaybackService::class.java).setAction(ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this, 0, Intent(this, PlaybackService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 0, Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevIntent = PendingIntent.getService(
            this, 0, Intent(this, PlaybackService::class.java).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(artBitmap)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (player.isPlaying) "Pause" else "Play",
                if (player.isPlaying) pauseIntent else playIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}