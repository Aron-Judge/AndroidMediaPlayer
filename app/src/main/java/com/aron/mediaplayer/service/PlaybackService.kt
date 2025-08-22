package com.aron.mediaplayer.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aron.mediaplayer.R

@UnstableApi
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "playback_channel"
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
        createNotificationChannel()
    }

    override fun onDestroy() {
        mediaSession?.release()
        player.release()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ✅ Foreground promotion guarded by POST_NOTIFICATIONS check
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startForeground(NOTIFICATION_ID, buildLoadingNotification())
        }

        intent?.getStringExtra("mediaUri")?.let { uriStr ->
            val mediaItem = MediaItem.fromUri(uriStr)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()

            // Update notification only if permission granted
            if (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification())
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun buildLoadingNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Loading…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun buildNotification(): Notification {
        val currentItem = player.currentMediaItem
        val title = currentItem?.mediaMetadata?.title ?: "Playing audio"
        val artist = currentItem?.mediaMetadata?.artist ?: ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play) // placeholder
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionCompatToken)
                // Removed .setShowActionsInCompactView(0) until actions are added
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