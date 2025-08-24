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
import com.aron.mediaplayer.data.*
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

@UnstableApi
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    // Coroutine scope for DB observation
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playlistJob: Job? = null
    private lateinit var dao: PlaylistDao
    private lateinit var activeStore: ActivePlaylistStore

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

        // 🌟 Highlight state source of truth
        private val _currentUri = MutableStateFlow<String?>(null)
        val currentUri: StateFlow<String?> = _currentUri
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            maybeUpdateNotification()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentUri.value = mediaItem?.localConfiguration?.uri.toString()
            maybeUpdateNotification()
        }
    }

    private fun maybeUpdateNotification() {
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

        dao = AppDatabase.getInstance(applicationContext).playlistDao()
        activeStore = ActivePlaylistStore(applicationContext, dao)

        // Start live playlist sync (per active playlist)
        playlistJob = serviceScope.launch {
            val initialId = activeStore.ensureDefaultPlaylistSelected()
            activeStore.activePlaylistId.collectLatest { pid ->
                val playlistId = if (pid > 0) pid else initialId
                dao.getTracksForPlaylist(playlistId).collectLatest { tracks ->
                    syncQueueWith(tracks)
                }
            }
        }
    }

    override fun onDestroy() {
        playlistJob?.cancel()
        serviceScope.cancel()
        player.removeListener(playerListener)
        mediaSession?.release()
        player.release()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Cold start fast-load for active playlist
        if (player.mediaItemCount == 0) {
            val pid = runBlocking { activeStore.ensureDefaultPlaylistSelected() }
            val playlistTracks = runBlocking { dao.getTracksForPlaylist(pid).first() }
            if (playlistTracks.isNotEmpty()) {
                loadPlaylistIntoPlayer(playlistTracks, startIndex = 0, startPositionMs = 0, autoPlay = true)
            }
        }

        if (intent?.action == ACTION_ADD_TO_PLAYLIST) {
            runBlocking {
                val pid = activeStore.ensureDefaultPlaylistSelected()
                dao.insertTrack(
                    PlaylistTrack(
                        playlistId = pid,
                        uri = intent.getStringExtra(EXTRA_URI) ?: return@runBlocking,
                        title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown Title",
                        artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown Artist",
                        duration = intent.getLongExtra(EXTRA_DURATION, 0L),
                        artworkUri = intent.getStringExtra(EXTRA_ARTWORK_URI)
                    )
                )
            }
        }

        when (intent?.action) {
            ACTION_PLAY -> {
                val uri = intent.getStringExtra(EXTRA_URI)
                if (uri != null) {
                    _currentUri.value = uri // immediate update for UI
                    val pid = runBlocking { activeStore.ensureDefaultPlaylistSelected() }
                    val playlistTracks = runBlocking { dao.getTracksForPlaylist(pid).first() }
                    if (playlistTracks.isNotEmpty()) {
                        val index = playlistTracks.indexOfFirst { it.uri == uri }.coerceAtLeast(0)
                        loadPlaylistIntoPlayer(
                            playlistTracks,
                            startIndex = index,
                            startPositionMs = 0,
                            autoPlay = true
                        )
                    }
                } else {
                    player.play()
                }
            }
            ACTION_PAUSE -> player.pause()
            ACTION_NEXT -> player.seekToNextMediaItem()
            ACTION_PREVIOUS -> handlePrevious()
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

    // ——— Helpers ———

    private fun buildMediaItems(tracks: List<PlaylistTrack>): List<MediaItem> =
        tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
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

    private fun loadPlaylistIntoPlayer(
        playlistTracks: List<PlaylistTrack>,
        startIndex: Int,
        startPositionMs: Long,
        autoPlay: Boolean
    ) {
        val mediaItems = buildMediaItems(playlistTracks)
        player.setMediaItems(mediaItems)
        player.seekTo(startIndex, startPositionMs)

        mediaItems.getOrNull(startIndex)?.localConfiguration?.uri?.toString()?.let {
            _currentUri.value = it
        }

        player.prepare()
        if (autoPlay) player.play()
    }

    private fun syncQueueWith(tracks: List<PlaylistTrack>) {
        val newItems = buildMediaItems(tracks)

        val wasPlaying = player.isPlaying
        val oldItems = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        val oldIndex = player.currentMediaItemIndex
        val oldId = player.currentMediaItem?.mediaId
        val oldPos = player.currentPosition

        if (newItems.isEmpty()) {
            player.setMediaItems(emptyList())
            player.pause()
            _currentUri.value = null
            return
        }

        player.setMediaItems(newItems, /* resetPosition = */ false)
        player.prepare()

        val sameItemIndex = oldId?.let { id -> newItems.indexOfFirst { it.mediaId == id } } ?: -1
        if (sameItemIndex >= 0) {
            player.seekTo(sameItemIndex, oldPos)
            _currentUri.value = newItems[sameItemIndex].localConfiguration?.uri.toString()
            if (wasPlaying) player.play()
            return
        }

        val oldNextId = oldItems.getOrNull(oldIndex + 1)?.mediaId
        val nextIndex = oldNextId?.let { nx -> newItems.indexOfFirst { it.mediaId == nx } } ?: -1

        val targetIndex = when {
            nextIndex >= 0 -> nextIndex
            oldIndex <= newItems.lastIndex -> oldIndex
            else -> newItems.lastIndex
        }

        player.seekTo(targetIndex, 0L)
        _currentUri.value = newItems[targetIndex].localConfiguration?.uri.toString()
        if (wasPlaying) player.play()
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

    // Simple in-memory cache for artwork bitmaps
    private val artworkCache = mutableMapOf<Uri, Bitmap?>()

    private fun buildNotification(): Notification {
        val currentItem = player.currentMediaItem
        val mm = currentItem?.mediaMetadata

        val title = mm?.title?.toString()?.takeIf { it.isNotEmpty() } ?: "Sample Track"
        val artist = mm?.artist?.toString()?.takeIf { it.isNotEmpty() } ?: "Unknown Artist"

        val artUri: Uri = mm?.artworkUri
            ?: Uri.parse("https://via.placeholder.com/300.png?text=No+Artwork")

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_media_previous, "Previous",
                PendingIntent.getService(this, 0,
                    Intent(this, PlaybackService::class.java).setAction(ACTION_PREVIOUS),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (player.isPlaying) "Pause" else "Play",
                if (player.isPlaying)
                    PendingIntent.getService(this, 0,
                        Intent(this, PlaybackService::class.java).setAction(ACTION_PAUSE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                else
                    PendingIntent.getService(this, 0,
                        Intent(this, PlaybackService::class.java).setAction(ACTION_PLAY),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            .addAction(android.R.drawable.ic_media_next, "Next",
                PendingIntent.getService(this, 0,
                    Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        // If cached, use immediately
        artworkCache[artUri]?.let { cachedBmp ->
            return builder.setLargeIcon(cachedBmp).build()
        }

        // Otherwise load asynchronously
        serviceScope.launch(Dispatchers.IO) {
            try {
                val bmp = Glide.with(this@PlaybackService)
                    .asBitmap()
                    .load(artUri)
                    .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                    .get()
                // Cache result for reuse
                artworkCache[artUri] = bmp
                withContext(Dispatchers.Main) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, builder.setLargeIcon(bmp).build())
                }
            } catch (_: Exception) {
                artworkCache[artUri] = null
                withContext(Dispatchers.Main) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, builder.build())
                }
            }
        }

        // Return the text-only notification immediately
        return builder.build()
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